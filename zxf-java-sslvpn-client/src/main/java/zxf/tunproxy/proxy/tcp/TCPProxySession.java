package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketBuilder;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.TunProxy;
import zxf.vpn.SSLVPNClient;
import zxf.vpn.SSLVPNConnection;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * TCP 代理会话
 */
@Slf4j
public class TCPProxySession {
    private final Thread readThread = new Thread(this::readFromRealConnection);
    private final Thread writeThread = new Thread(this::writeToRealConnection);
    private final TCPConnectionState state;
    private BlockingQueue<PacketParser.TCPPacket> packetQueue;
    private final TunProxy tunProxy;
    private final Runnable cleanup;
    private SSLVPNConnection realSocket;

    public TCPProxySession(PacketParser.TCPPacket initalTCPPacket, TunProxy tunProxy, Runnable cleanup) {
        this.state = new TCPConnectionState(initalTCPPacket.ipPacket);
        this.packetQueue = new LinkedBlockingQueue<>();
        this.packetQueue.offer(initalTCPPacket);
        this.tunProxy = tunProxy;
        this.cleanup = cleanup;
    }

    public void start() {
        new Thread(() -> {
            try {
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
            } catch (Exception ex) {
                try {
                    sendRST();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                closeAndCleanup();
            }
        }, "tcp-proxy-handshake").start();
    }


    private boolean establishProxyConnection() throws Exception {
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

        if (ackPacket.ackNumber != (state.serverNextSeq & 0xFFFFFFFFL)) {
            return false;
        }

        if (ackPacket.sequenceNumber != (state.expectedClientSeq & 0xFFFFFFFFL)) {
            return false;
        }

        // 握手完成
        state.handshakeDone.set(true);
        return true;
    }


    /**
     * 等待 SYN 包
     */
    private PacketParser.TCPPacket waitForSYN() throws InterruptedException {
        log.info("等待 SYN 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;
            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) return null;
            if (packetData.hasFlag(PacketParser.TCPPacket.SYN)) {
                // 更新状态
                log.info("收到 SYN 包 {}", packetData);
                state.clientInitialSeq = packetData.sequenceNumber & 0xFFFFFFFFL;
                state.expectedClientSeq = state.clientInitialSeq + 1;
                return packetData;
            }
        }
        return null;
    }

    /**
     * 发送 SYN-ACK 响应
     */
    private void sendSYNACK(PacketParser.TCPPacket synPacket) throws Exception {
        state.serverInitialSeq = ThreadLocalRandom.current().nextInt() & 0xFFFFFFFFL;
        state.serverNextSeq = state.serverInitialSeq + 1;

        // 创建 SYN-ACK 包
        byte flags = (byte) (PacketParser.TCPPacket.SYN | PacketParser.TCPPacket.ACK);

        // 创建响应包
        byte[] responsePacket = PacketBuilder.createTCPPacket(synPacket.ipPacket.dstIP, synPacket.ipPacket.srcIP,
                synPacket.dstPort, synPacket.srcPort, state.serverInitialSeq, state.expectedClientSeq, flags, state.serverWindow, null);

        log.info("发送 SYN-ACK: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(responsePacket, responsePacket.length)));
        tunProxy.submitPacket(responsePacket);
    }


    /**
     * 等待 ACK 包
     */
    private PacketParser.TCPPacket waitForACK() throws InterruptedException {
        log.info("等待 ACK 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;
            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) return null;
            if (packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                log.info("收到 ACK 包 {}", packetData);
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
            log.info("建立真实连接: {}:{} ", state.dstIP, state.dstPort);
            realSocket = new SSLVPNClient().connectToVPNServer("127.0.0.1", state.dstPort);
            return true;
        } catch (Exception e) {
            System.err.printf("建立真实连接失败: %s ", e.getMessage());
            try {
                sendRST();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return false;
        }
    }

    /**
     * 启动数据转发
     */
    private void startForwarding() {
        readThread.start();
        writeThread.start();
    }


    /**
     * 处理来自 TUN 的数据包
     */
    public void submitPacket(PacketParser.TCPPacket tcpPacket) {
        if (state.closed) return;
        packetQueue.offer(tcpPacket);
    }


    /**
     * 转发数据到真实连接
     */
    private void writeToRealConnection() {
        // 处理来自 TUN 设备的数据包
        try {
            log.info("=== 开始转发数据 ===");

            while (!state.closed) {
                PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
                if (packetData == null) {
                    checkIdleTime();
                    continue;
                }

                state.lastActivityTime = System.currentTimeMillis();

                if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                    closeAndCleanup();
                    return;
                }

                if ((packetData.sequenceNumber & 0xFFFFFFFFL) != (state.expectedClientSeq & 0xFFFFFFFFL)) {
                    sendPureAck();
                    continue;
                }

                int payloadLen = packetData.payload == null ? 0 : packetData.payload.length;
                if (payloadLen > 0) {
                    realSocket.write(packetData.payload, 0, payloadLen);
                    state.expectedClientSeq += payloadLen;
                }

                if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
                    state.expectedClientSeq += 1;
                    state.clientFinReceived = true;
                    sendPureAck();
                } else if (payloadLen > 0 || packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                    sendPureAck();
                }

                if (state.serverFinSent && packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                    if ((packetData.ackNumber & 0xFFFFFFFFL) == (state.serverNextSeq & 0xFFFFFFFFL)) {
                        state.clientFinAcked = true;
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            log.error("转发数据时发生错误: {}", ex.getMessage(), ex);
            try {
                sendRST();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            closeAndCleanup();
        }
    }

    /**
     * 从真实连接读取数据
     */
    private void readFromRealConnection() {
        try {
            log.info("=== 开始读取数据 ===");
            byte[] buffer = new byte[4096];
            while (!state.closed) {
                int bytesRead = realSocket.read(buffer);
                if (bytesRead == -1) {
                    if (!state.serverFinSent) {
                        sendFin();
                    }
                    return;
                }

                if (bytesRead > 0) {
                    state.lastActivityTime = System.currentTimeMillis();

                    byte[] payload = new byte[bytesRead];
                    System.arraycopy(buffer, 0, payload, 0, bytesRead);

                    sendDataToClient(payload);
                }
            }
        } catch (Exception ex) {
            log.error("从真实连接读取数据失败: {}", ex.getMessage(), ex);
        }
    }


    private void sendPureAck() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(state.dstIP, state.srcIP, state.dstPort, state.srcPort, state.serverNextSeq,
                state.expectedClientSeq, flags, state.serverWindow, null);
        tunProxy.submitPacket(packet);
    }

    private void sendDataToClient(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) return;
        byte flags = (byte) (PacketParser.TCPPacket.PSH | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(state.dstIP, state.srcIP, state.dstPort, state.srcPort, state.serverNextSeq,
                state.expectedClientSeq, flags, state.serverWindow, payload);
        tunProxy.submitPacket(packet);
        state.serverNextSeq += payload.length;
    }

    private void sendFin() throws Exception {
        if (state.serverFinSent) return;
        byte flags = (byte) (PacketParser.TCPPacket.FIN | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(state.dstIP, state.srcIP, state.dstPort, state.srcPort, state.serverNextSeq,
                state.expectedClientSeq, flags, state.serverWindow, null);
        tunProxy.submitPacket(packet);
        state.serverNextSeq += 1;
        state.serverFinSent = true;
    }

    private void sendRST() throws Exception {
        if (state.closed) return;
        byte flags = (byte) (PacketParser.TCPPacket.RST | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(state.dstIP, state.srcIP, state.dstPort, state.srcPort, state.serverNextSeq,
                state.expectedClientSeq, flags, 0, null);
        tunProxy.submitPacket(packet);
    }

    private void checkIdleTime() throws Exception {
        if (System.currentTimeMillis() - state.lastActivityTime > 120000) {
            sendFin();
        }
    }

    private void closeAndCleanup() {
        if (state.closed) return;
        state.closed = true;

        try {
            if (realSocket != null) {
                realSocket.close();
            }
        } catch (Exception ignore) {

        }
        cleanup.run();
    }
}
