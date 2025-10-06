package zxf.tunproxy.proxy.handler;

import lombok.extern.slf4j.Slf4j;
import zxf.SSLVPNClient;
import zxf.SocketUtils;
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
            tcpWorkerPool.submit(session);
            activeSessions.put(sessionKey, session);
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
        log.info("TCP 处理器已停止");
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
    private static class TCPProxySession implements Runnable {
        private final String srcIP;
        private final String dstIP;
        private final int srcPort;
        private final int dstPort;
        private final TunPacketWriter packetWriter;
        private final ConnectionTracker connectionTracker;
        private Socket realSocket;
        private InputStream realInput;
        private OutputStream realOutput;
        private BlockingQueue<byte[]> packetQueue;

        public TCPProxySession(IPPacketParser.IPPacket initalIpPacket, TunPacketWriter packetWriter, ConnectionTracker connectionTracker) {
            this.srcIP = initalIpPacket.sourceIP;
            this.srcPort = initalIpPacket.sourcePort;
            this.dstIP = initalIpPacket.destIP;
            this.dstPort = initalIpPacket.destPort;
            this.packetWriter = packetWriter;
            this.connectionTracker = connectionTracker;
            this.packetQueue = new LinkedBlockingQueue<>();
            connectionTracker.registerConnection(srcIP, dstIP, srcPort, dstPort, 6);
        }

        @Override
        public void run() {
            try {
                establishRealConnection();
                startForwarding();
            } catch (Exception ex) {
                log.error("TCP 会话错误 {}:{}->{}:{}: {}", srcIP, srcPort, dstIP, dstPort, ex.getMessage(), ex);
            } finally {
                stop();
            }
        }

        /**
         * 建立真实 TCP 连接
         */
        private void establishRealConnection() throws Exception {
            realSocket = new SSLVPNClient().connectToVPNServer(dstIP, dstPort);
            connectionTracker.updateConnectionState(srcIP, dstIP, srcPort, dstPort, 6,
                    ConnectionTracker.ConnectionState.ESTABLISHED);

            realInput = realSocket.getInputStream();
            realOutput = realSocket.getOutputStream();

            log.info("TCP 连接已建立 {}:{}->{}:{} ", srcIP, srcPort, dstIP, dstPort);
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
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    byte[] packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (packetData != null) {
                        forwardToRealConnection(packetData);
                    }

                    // 检查连接是否还活跃
                    if (realSocket.isClosed() || !realSocket.isConnected()) {
                        break;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    log.error("转发数据时发生错误: {}", ex.getMessage());
                    break;
                }
            }
        }

        /**
         * 处理来自 TUN 的数据包
         */
        public void handlePacket(IPPacketParser.IPPacket ipPacket, byte[] packet) {
            // 提取 TCP 载荷
            if (ipPacket.payload != null && ipPacket.payload.length > 20) {
                // TCP 头至少 20 字节，载荷从第 20 字节开始
                int tcpHeaderLength = ((ipPacket.payload[12] & 0xF0) >> 4) * 4;
                if (ipPacket.payload.length > tcpHeaderLength) {
                    byte[] tcpPayload = new byte[ipPacket.payload.length - tcpHeaderLength];
                    System.arraycopy(ipPacket.payload, tcpHeaderLength, tcpPayload, 0, tcpPayload.length);
                    // 将载荷加入队列，准备转发到真实连接
                    packetQueue.offer(tcpPayload);
                }
            }
        }

        /**
         * 转发数据到真实连接
         */
        private void forwardToRealConnection(byte[] tcpPayload) {
            try {
                realOutput.write(tcpPayload);
                realOutput.flush();
                // 更新连接活动
                connectionTracker.updateConnectionActivity(srcIP, dstIP, srcPort, dstPort, 6, tcpPayload.length, true);
            } catch (IOException ex) {
                log.error("向真实连接写入数据失败: {}", ex.getMessage(), ex);
                stop();
            }
        }

        /**
         * 从真实连接读取数据
         */
        private void readFromRealConnection() {
            byte[] buffer = new byte[4096];

            try {
                while (!realSocket.isClosed()) {
                    int bytesRead = realInput.read(buffer);
                    if (bytesRead == -1) {
                        break; // 连接关闭
                    }

                    if (bytesRead > 0) {
                        // 创建响应数据包
                        byte[] responsePacket = createResponsePacket(buffer, bytesRead);
                        packetWriter.writePacket(responsePacket);

                        // 更新连接活动
                        connectionTracker.updateConnectionActivity(srcIP, dstIP, srcPort, dstPort, 6, bytesRead, false);
                    }
                }
            } catch (IOException ex) {
                log.error("从真实连接读取数据失败: {}", ex.getMessage(), ex);
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
            SocketUtils.closeQuietly(realSocket);
            connectionTracker.updateConnectionState(srcIP, dstIP, srcPort, dstPort, 6, ConnectionTracker.ConnectionState.CLOSED);
            log.info("TCP 会话已关闭 {}:{}->{}:{} ", srcIP, srcPort, dstIP, dstPort);
        }
    }
}