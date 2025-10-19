package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketParser;
import zxf.vpn.SSLVPNClient;
import zxf.vpn.SSLVPNConnection;

@Slf4j
public class TCPSessionWorker {
    private final Thread readThread = new Thread(this::readFromRealConnection, "tcp-session-worker-reader");
    private final Thread writeThread = new Thread(this::writeToRealConnection, "tcp-session-worker-writer");
    private final TCPSession tcpSession;
    private final Runnable cleanup;
    private SSLVPNConnection realSocket;

    public TCPSessionWorker(TCPSession tcpSession, Runnable cleanup) {
        this.tcpSession = tcpSession;
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
                    closeProxyConnection();
                    return;
                }

                // 启动数据转发
                startForwarding();
            } catch (Exception ex) {
                closeAndCleanup();
            }
        }, "tcp-session-worker-handshake").start();
    }


    private boolean establishProxyConnection() throws Exception {
        // 等待 SYN 包
        PacketParser.TCPPacket synPacket = tcpSession.waitForSYN();
        if (synPacket == null) {
            return false;
        }

        // 发送 SYN-ACK 响应
        tcpSession.sendSYNACK(synPacket);

        // 等待 ACK 完成握手
        PacketParser.TCPPacket ackPacket = tcpSession.waitForACK();
        if (ackPacket == null) {
            return false;
        }


        return true;
    }

    private void resetProxyConnection() {
        try {
            tcpSession.sendRST();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void closeProxyConnection() {
       tcpSession.close();
    }


    /**
     * 建立真实 TCP 连接
     */
    private boolean establishRealConnection() {
        try {
            log.info("建立真实连接: {}:{} ", tcpSession.dstIP, tcpSession.dstPort);
            realSocket = new SSLVPNClient().connectToVPNServer("127.0.0.1", tcpSession.dstPort);
            return true;
        } catch (Exception e) {
            System.err.printf("建立真实连接失败: %s ", e.getMessage());
            resetProxyConnection();
            return false;
        }
    }

    /**
     * 建立真实 TCP 连接
     */
    private void closeRealConnection() {
        try {
            realSocket.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
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
     * 转发数据到真实连接
     */
    private void writeToRealConnection() {
        // 处理来自 TUN 设备的数据包
        try {
            log.info("=== 开始转发数据 ===");
            while (!tcpSession.closed) {
                PacketParser.TCPPacket packetData = tcpSession.waitForData();
                if (packetData == null) {
                    checkIdleTime();
                    continue;
                }

                if ((packetData.sequenceNumber & 0xFFFFFFFFL) != (tcpSession.expectedClientSeq & 0xFFFFFFFFL)) {
                    tcpSession.sendPureAck();
                    continue;
                }

                int payloadLen = packetData.payload == null ? 0 : packetData.payload.length;
                if (payloadLen > 0) {
                    realSocket.write(packetData.payload, 0, payloadLen);
                    tcpSession.expectedClientSeq += payloadLen;
                }

                if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
                    tcpSession.expectedClientSeq += 1;

                    tcpSession.sendPureAck();
                } else if (payloadLen > 0 || packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                    tcpSession.sendPureAck();
                }
            }
        } catch (Exception ex) {
            log.error("转发数据时发生错误: {}", ex.getMessage(), ex);
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
            while (!tcpSession.closed) {
                int bytesRead = realSocket.read(buffer);
                if (bytesRead == -1) {
                    closeProxyConnection();
                    return;
                }

                if (bytesRead > 0) {
                    byte[] payload = new byte[bytesRead];
                    System.arraycopy(buffer, 0, payload, 0, bytesRead);
                    tcpSession.sendData(payload);
                }
            }
        } catch (Exception ex) {
            log.error("从真实连接读取数据失败: {}", ex.getMessage(), ex);
            closeAndCleanup();
        }
    }


    private void checkIdleTime() throws Exception {
        if (System.currentTimeMillis() - tcpSession.lastActivityTime > 120000) {
            tcpSession.sendFin();
        }
    }

    private void closeAndCleanup() {
        if (tcpSession.closed) return;
        tcpSession.closed = true;
        resetProxyConnection();
        closeRealConnection();
        cleanup.run();
    }
}
