package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.SSLVPNClient;
import zxf.SocketUtils;
import zxf.tunproxy.packet.PacketBuilder;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.TunPacketWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TCP 代理会话
 */
@Slf4j
public class TCPProxySession {
    private final TCPConnectionState state;
    private BlockingQueue<PacketParser.TCPPacket> packetQueue;
    private final TunPacketWriter packetWriter;
    private Socket realSocket;
    Thread readThread;
    Thread writeThread;


    public TCPProxySession(PacketParser.IPPacket initalIpPacket, TunPacketWriter packetWriter) {
        this.state = new TCPConnectionState(initalIpPacket);
        this.packetWriter = packetWriter;
        this.packetQueue = new LinkedBlockingQueue<>();
    }

    public void start() {
        new Thread(() -> {
            // 处理 TCP 握手
            if (!establishProxyConnection()) {
                return;
            }

            // 建立真实连接
            if (!establishRealConnection()) {
                return;
            }

            // 启动数据转发
            startForwarding();
        }).start();
    }


    private boolean establishProxyConnection() {
        try {
            // 等待 SYN 包
            PacketParser.TCPPacket synPacket = waitForSYN();
            if (synPacket == null) {
                return false;
            }

            // 发送 SYN-ACK 响应
            sendSYNACK(synPacket);

            // 等待 ACK 完成握手
            PacketParser.TCPPacket ackPacket = waitForACK();
            if (ackPacket == null) {
                return false;
            }

            // 握手完成
            state.established = true;
            return true;
        } catch (Exception e) {
            System.err.printf("TCP 握手失败 %s: %s ", e.getMessage());
            return false;
        }
    }


    /**
     * 等待 SYN 包
     */
    private PacketParser.TCPPacket waitForSYN() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData != null && packetData.hasFlag(PacketParser.TCPPacket.SYN)) {
                return packetData;
            }
        }
        return null;
    }

    /**
     * 发送 SYN-ACK 响应
     */
    private void sendSYNACK(PacketParser.TCPPacket synPacket) {
        try {
            // 创建 SYN-ACK 包
            byte flags = (byte) (PacketParser.TCPPacket.SYN | PacketParser.TCPPacket.ACK);

            // 更新状态
            //state.serverSeq = proxyInitialSeq;
            state.serverAck = synPacket.sequenceNumber + 1; // SYN 占用一个序列号
            state.serverSynReceived = true;


            // 创建响应包
            byte[] responsePacket = PacketBuilder.createTCPPacket(synPacket.ipPacket.destIP, synPacket.ipPacket.sourceIP,
                    synPacket.srcPort, synPacket.dstPort, state.serverSeq, state.serverAck, flags, 0, null);

            System.out.printf("发送 SYN-ACK: seq=%d ack=%d ", state.serverSeq, state.serverAck);
            packetWriter.writePacket(responsePacket);
        } catch (Exception e) {
            System.err.printf("发送 SYN-ACK 失败: %s ", e.getMessage());
        }
    }


    /**
     * 等待 ACK 包
     */
    private PacketParser.TCPPacket waitForACK() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData != null && packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                return packetData;
            }
        }
        return null;
    }

    /**
     * 建立真实 TCP 连接
     */
    private boolean establishRealConnection() {
        try {
            realSocket = new SSLVPNClient().connectToVPNServer(state.dstIP, state.dstPort);
            return true;
        } catch (Exception e) {
            System.err.printf("建立真实连接失败: %s ", e.getMessage());
            return false;
        }
    }

    /**
     * 启动数据转发
     */
    private void startForwarding() {
        // 启动从真实连接读取数据的线程
        readThread = new Thread(this::readFromRealConnection);
        readThread.setDaemon(true);
        readThread.start();

        writeThread = new Thread(this::writeToRealConnection);
        readThread.setDaemon(true);
        readThread.start();
    }


    /**
     * 处理来自 TUN 的数据包
     */
    public void submitPacket(PacketParser.TCPPacket tcpPacket) {
        packetQueue.offer(tcpPacket);
    }


    /**
     * 转发数据到真实连接
     */
    private void writeToRealConnection() {
        // 处理来自 TUN 设备的数据包
        try {
            OutputStream realOutput = realSocket.getOutputStream();

            while (!Thread.currentThread().isInterrupted()) {
                byte[] packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
                if (packetData != null) {
                    realOutput.write(tcpPayload);
                    realOutput.flush();
                }

                // 检查连接是否还活跃
                if (realSocket.isClosed() || !realSocket.isConnected()) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.error("转发数据时发生错误: {}", ex.getMessage());
        }
    }

    /**
     * 从真实连接读取数据
     */
    private void readFromRealConnection() {
        try {
            InputStream realInput = realSocket.getInputStream();

            while (!Thread.currentThread().isInterrupted()) {
                byte[] buffer = new byte[4096];
                int bytesRead = realInput.read(buffer);
                if (bytesRead == -1) {
                    break; // 连接关闭
                }

                if (bytesRead > 0) {
                    // 创建响应数据包
                    byte[] responsePacket = createResponsePacket(buffer, bytesRead);
                    packetWriter.writePacket(responsePacket);

                    // 更新连接活动

                }
            }
        } catch (Exception ex) {
            log.error("从真实连接读取数据失败: {}", ex.getMessage(), ex);
        }
    }
}
