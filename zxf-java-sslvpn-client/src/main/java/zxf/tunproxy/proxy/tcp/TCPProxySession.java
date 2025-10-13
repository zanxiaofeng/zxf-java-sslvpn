package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketParser;
import zxf.vpn.SSLVPNClient;
import zxf.vpn.SSLVPNConnection;


/**
 * TCP 代理会话
 */
@Slf4j
public class TCPProxySession {
    private final Thread readThread = new Thread(this::readFromRealConnection);
    private final Thread writeThread = new Thread(this::writeToRealConnection);
    private final TCPConnectionState state;
    private final Runnable cleanup;
    private SSLVPNConnection realSocket;

    public TCPProxySession(TCPConnectionState state, Runnable cleanup) {
        this.state = state;
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
        }, "tcp-proxy-handshake").start();
    }


    private boolean establishProxyConnection() throws Exception {
        // 等待 SYN 包
        PacketParser.TCPPacket synPacket = state.waitForSYN();
        if (synPacket == null) {
            return false;
        }

        // 发送 SYN-ACK 响应
        state.sendSYNACK(synPacket);

        // 等待 ACK 完成握手
        PacketParser.TCPPacket ackPacket = state.waitForACK();
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

    private void resetProxyConnection() {
        try {
            state.sendRST();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void closeProxyConnection() {
        try {
            if (!state.serverFinSent) {
                state.sendFin();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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

            while (!state.closed) {
                PacketParser.TCPPacket packetData = state.readPacket();
                if (packetData == null) {
                    checkIdleTime();
                    continue;
                }

                if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                    closeAndCleanup();
                    return;
                }

                if ((packetData.sequenceNumber & 0xFFFFFFFFL) != (state.expectedClientSeq & 0xFFFFFFFFL)) {
                    state.sendPureAck();
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
                    state.sendPureAck();
                } else if (payloadLen > 0 || packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                    state.sendPureAck();
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
                    closeProxyConnection();
                    return;
                }

                if (bytesRead > 0) {
                    byte[] payload = new byte[bytesRead];
                    System.arraycopy(buffer, 0, payload, 0, bytesRead);
                    state.sendDataToClient(payload);
                }
            }
        } catch (Exception ex) {
            log.error("从真实连接读取数据失败: {}", ex.getMessage(), ex);
            closeAndCleanup();
        }
    }


    private void checkIdleTime() throws Exception {
        if (System.currentTimeMillis() - state.lastActivityTime > 120000) {
            state.sendFin();
        }
    }

    private void closeAndCleanup() {
        if (state.closed) return;
        state.closed = true;
        resetProxyConnection();
        closeRealConnection();
        cleanup.run();
    }
}
