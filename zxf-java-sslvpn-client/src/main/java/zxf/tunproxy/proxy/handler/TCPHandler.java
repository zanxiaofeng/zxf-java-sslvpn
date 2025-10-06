package zxf.tunproxy.proxy.handler;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.IPPacketParser;
import zxf.tunproxy.packet.PacketBuilder;
import zxf.tunproxy.proxy.ConnectionTracker;
import zxf.tunproxy.proxy.TunPacketWriter;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;

/**
 * TCP 协议处理器
 */
@Slf4j
public class TCPHandler {
    private final TunPacketWriter packetWriter;
    private final ExecutorService tcpWorkerPool;
    private final ConnectionTracker connectionTracker;
    private final Map<String, TCPProxySession> activeSessions;

    public TCPHandler(TunPacketWriter packetWriter) {
        this.packetWriter = packetWriter;
        this.tcpWorkerPool = Executors.newCachedThreadPool();
        this.connectionTracker = new ConnectionTracker();
        this.activeSessions = new ConcurrentHashMap<>();
    }

    /**
     * 处理 TCP 数据包
     */
    public void handlePacket(IPPacketParser.IPPacket ipPacket, byte[] packet) {
        String sessionKey = getSessionKey(ipPacket);

        // 获取或创建会话
        TCPProxySession session = activeSessions.get(sessionKey);
        if (session == null) {
            session = new TCPProxySession(ipPacket, packetWriter, connectionTracker);
            activeSessions.put(sessionKey, session);
            tcpWorkerPool.submit(session);
        }

        // 处理数据包
        session.handlePacket(ipPacket, packet);
    }

    /**
     * 停止处理器
     */
    public void stop() {
        tcpWorkerPool.shutdown();
        connectionTracker.stop();

        // 停止所有活动会话
        for (TCPProxySession session : activeSessions.values()) {
            session.stop();
        }
        activeSessions.clear();

        System.out.println("TCP 处理器已停止");
    }


    /**
     * 生成会话键
     */
    private String getSessionKey(IPPacketParser.IPPacket ipPacket) {
        return String.format("%s:%d->%s:%d", ipPacket.sourceIP, ipPacket.sourcePort, ipPacket.destIP, ipPacket.destPort);
    }



/**
 * TCP 代理会话
 */
class TCPProxySession implements Runnable {
    private final String srcIP;
    private final String dstIP;
    private final int srcPort;
    private final int dstPort;
    private final TunPacketWriter packetWriter;
    private final ConnectionTracker connectionTracker;

    private Socket realSocket;
    private InputStream realInput;
    private OutputStream realOutput;
    private volatile boolean running;
    private BlockingQueue<byte[]> packetQueue;

    public TCPProxySession(IPPacketParser.IPPacket ipPacket,
                           TunPacketWriter packetWriter,
                           ConnectionTracker connectionTracker) {
        this.srcIP = ipPacket.sourceIP;
        this.dstIP = ipPacket.destIP;
        this.srcPort = ipPacket.sourcePort;
        this.dstPort = ipPacket.destPort;
        this.packetWriter = packetWriter;
        this.connectionTracker = connectionTracker;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.running = true;

        // 注册连接
        connectionTracker.registerConnection(srcIP, dstIP, srcPort, dstPort, 6);
        connectionTracker.updateConnectionState(srcIP, dstIP, srcPort, dstPort, 6,
                ConnectionTracker.ConnectionState.NEW);
    }

    @Override
    public void run() {
        try {
            // 建立真实连接
            establishRealConnection();

            // 启动数据转发
            startForwarding();

        } catch (Exception e) {
            System.err.printf("TCP 会话错误 %s:%d->%s:%d: %s ",
                    srcIP, srcPort, dstIP, dstPort, e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * 建立真实 TCP 连接
     */
    private void establishRealConnection() throws IOException {
        // 建立 socket 连接
        realSocket = new Socket();
        realSocket.connect(new InetSocketAddress(dstIP, dstPort), 10000);
        realSocket.setTcpNoDelay(true);

        realInput = realSocket.getInputStream();
        realOutput = realSocket.getOutputStream();

        connectionTracker.updateConnectionState(srcIP, dstIP, srcPort, dstPort, 6,
                ConnectionTracker.ConnectionState.ESTABLISHED);

        System.out.printf("TCP 连接已建立: %s:%d -> %s:%d ",
                srcIP, srcPort, dstIP, dstPort);
    }

    /**
     * 启动数据转发
     */
    private void startForwarding() {
        // 启动从真实连接读取数据的线程
        Thread readThread = new Thread(this::readFromRealConnection);
        readThread.setDaemon(true);
        readThread.start();

        // 处理来自 TUN 设备的数据包
        while (running) {
            try {
                byte[] packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
                if (packetData != null) {
                    forwardToRealConnection(packetData);
                }

                // 检查连接是否还活跃
                if (realSocket.isClosed() || !realSocket.isConnected()) {
                    break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.printf("转发数据时发生错误: %s ", e.getMessage());
                break;
            }
        }
    }

    /**
     * 处理来自 TUN 的数据包
     */
    public void handlePacket(IPPacketParser.IPPacket ipPacket, byte[] packet) {
        if (!running) return;

        // 提取 TCP 载荷
        if (ipPacket.payload != null && ipPacket.payload.length > 20) {
            // TCP 头至少 20 字节，载荷从第 20 字节开始
            int tcpHeaderLength = ((ipPacket.payload[12] & 0xF0) >> 4) * 4;
            if (ipPacket.payload.length > tcpHeaderLength) {
                byte[] tcpPayload = new byte[ipPacket.payload.length - tcpHeaderLength];
                System.arraycopy(ipPacket.payload, tcpHeaderLength, tcpPayload, 0, tcpPayload.length);

                // 更新连接活动
                connectionTracker.updateConnectionActivity(srcIP, dstIP, srcPort, dstPort, 6,
                        tcpPayload.length, true);

                // 将载荷加入队列，准备转发到真实连接
                packetQueue.offer(tcpPayload);
            }
        }
    }

    /**
     * 转发数据到真实连接
     */
    private void forwardToRealConnection(byte[] data) {
        try {
            realOutput.write(data);
            realOutput.flush();
        } catch (IOException e) {
            System.err.printf("向真实连接写入数据失败: %s ", e.getMessage());
            stop();
        }
    }

    /**
     * 从真实连接读取数据
     */
    private void readFromRealConnection() {
        byte[] buffer = new byte[4096];

        try {
            while (running && !realSocket.isClosed()) {
                int bytesRead = realInput.read(buffer);
                if (bytesRead == -1) {
                    break; // 连接关闭
                }

                if (bytesRead > 0) {
                    // 创建响应数据包
                    byte[] responsePacket = createResponsePacket(buffer, bytesRead);
                    packetWriter.writePacket(responsePacket);

                    // 更新连接活动
                    connectionTracker.updateConnectionActivity(srcIP, dstIP, srcPort, dstPort, 6,
                            bytesRead, false);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.printf("从真实连接读取数据失败: %s ", e.getMessage());
            }
        } finally {
            stop();
        }
    }

    /**
     * 创建响应数据包
     */
    private byte[] createResponsePacket(byte[] data, int length) {
        // 交换源和目标，创建响应包
        return PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort,
                0, 0, (byte) 0x10, data); // ACK 标志
    }

    /**
     * 停止会话
     */
    public void stop() {
        running = false;
        cleanup();
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        try {
            if (realSocket != null) {
                realSocket.close();
            }
        } catch (IOException e) {
            // 忽略关闭错误
        }

        connectionTracker.updateConnectionState(srcIP, dstIP, srcPort, dstPort, 6,
                ConnectionTracker.ConnectionState.CLOSED);

        System.out.printf("TCP 会话已关闭: %s:%d -> %s:%d ",
                srcIP, srcPort, dstIP, dstPort);
    }
}