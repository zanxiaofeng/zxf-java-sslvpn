package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketParser;
import zxf.vpn.SSLVPNClient;
import zxf.vpn.SSLVPNConnection;

@Slf4j
public class TCPSessionWorker {
    private final Thread readThread = new Thread(this::readFromRealConnection, "tcp-session-worker-reader");
    private final Thread writeThread = new Thread(this::writeToRealConnection, "tcp-session-worker-writer");
    private final TCPSession proxyConnection;
    private SSLVPNConnection realConnection;
    private final Runnable cleanup;


    public TCPSessionWorker(TCPSession proxyConnection, Runnable cleanup) {
        this.proxyConnection = proxyConnection;
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
        PacketParser.TCPPacket synPacket = proxyConnection.waitForSYN();
        if (synPacket == null) {
            return false;
        }

        // 发送 SYN-ACK 响应
        proxyConnection.sendSYNACK(synPacket);

        // 等待 ACK 完成握手
        PacketParser.TCPPacket ackPacket = proxyConnection.waitForACK();
        if (ackPacket == null) {
            return false;
        }


        return true;
    }

    private void resetProxyConnection() {
        try {
            proxyConnection.sendRST();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void closeProxyConnection() {
       proxyConnection.close();
    }


    /**
     * 建立真实 TCP 连接
     */
    private boolean establishRealConnection() {
        try {
            log.info("建立真实连接: {}:{} ", proxyConnection.dstIP, proxyConnection.dstPort);
            realConnection = new SSLVPNClient().connectToVPNServer("127.0.0.1", proxyConnection.dstPort);
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
            realConnection.close();
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
            while (!proxyConnection.closed) {
                PacketParser.TCPPacket packetData = proxyConnection.waitForData();
                if (packetData == null) {
                    checkIdleTime();
                    continue;
                }

                if ((packetData.sequenceNumber & 0xFFFFFFFFL) != (proxyConnection.expectedClientSeq & 0xFFFFFFFFL)) {
                    proxyConnection.sendPureAck();
                    continue;
                }

                int payloadLen = packetData.payload == null ? 0 : packetData.payload.length;
                if (payloadLen > 0) {
                    realConnection.write(packetData.payload, 0, payloadLen);
                    proxyConnection.expectedClientSeq += payloadLen;
                }

                if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
                    proxyConnection.expectedClientSeq += 1;

                    proxyConnection.sendPureAck();
                } else if (payloadLen > 0 || packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                    proxyConnection.sendPureAck();
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
            while (!proxyConnection.closed) {
                int bytesRead = realConnection.read(buffer);
                if (bytesRead == -1) {
                    closeProxyConnection();
                    return;
                }

                if (bytesRead > 0) {
                    byte[] payload = new byte[bytesRead];
                    System.arraycopy(buffer, 0, payload, 0, bytesRead);
                    proxyConnection.sendData(payload);
                }
            }
        } catch (Exception ex) {
            log.error("从真实连接读取数据失败: {}", ex.getMessage(), ex);
            closeAndCleanup();
        }
    }


    private void checkIdleTime() throws Exception {
        if (System.currentTimeMillis() - proxyConnection.lastActivityTime > 120000) {
            proxyConnection.sendFin();
        }
    }

    private void closeAndCleanup() {
        if (proxyConnection.closed) return;
        proxyConnection.closed = true;
        resetProxyConnection();
        closeRealConnection();
        cleanup.run();
    }
}
