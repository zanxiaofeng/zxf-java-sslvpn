package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.vpn.SSLVPNClient;
import zxf.vpn.SSLVPNConnection;

import java.util.concurrent.atomic.AtomicBoolean;

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
            log.info("=== 开始处理 TCP 会话: {}:{} -> {}:{} ===", proxyConnection.srcIP, proxyConnection.srcPort, proxyConnection.dstIP, proxyConnection.dstPort);
            AtomicBoolean proxyConnected = new AtomicBoolean(false);

            AtomicBoolean realConnected = new AtomicBoolean(false);
            Thread realConnectionThread = new Thread(() -> {
                try {
                    // 建立真实连接
                    if (!establishRealConnection()) {
                        return;
                    }
                    realConnected.set(true);
                } catch (Exception ex) {
                    log.error("建立真实连接失败: {}", ex.getMessage(), ex);
                    closeAndCleanup();
                }
            }, "tcp-session-worker-real");
            realConnectionThread.start();

            Thread proxyConnectionThread = new Thread(() -> {
                try {
                    Thread.currentThread().sleep(1000);
                    // 处理 TCP 握手
                    if (!establishProxyConnection()) {
                        return;
                    }
                    proxyConnected.set(true);
                } catch (Exception ex) {
                    log.error("建立代理连接失败: {}", ex.getMessage(), ex);
                    closeAndCleanup();
                }
            }, "tcp-session-worker-proxy");
            proxyConnectionThread.start();

            try {
                log.info("=== 等待代理连接和真实连接建立 ===");
                realConnectionThread.join();
                proxyConnectionThread.join();

                if (proxyConnected.get() && realConnected.get()) {
                    log.info("=== 代理连接和真实连接都已建立 ===");
                    // 启动数据转发
                    startDataForwarding();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }).start();
    }


    private boolean establishProxyConnection() throws Exception {
        return proxyConnection.startSession();
    }

    private void closeProxyConnection() {
        proxyConnection.closeSession();
    }

    private boolean establishRealConnection() {
        try {
            log.info("建立真实连接: {}:{} ", proxyConnection.dstIP, proxyConnection.dstPort);
            realConnection = SSLVPNClient.connectToVPNServer("127.0.0.1", proxyConnection.dstPort);
            return true;
        } catch (Exception e) {
            System.err.printf("建立真实连接失败: %s ", e.getMessage());
            return false;
        }
    }

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
    private void startDataForwarding() {
        readThread.start();
        writeThread.start();
    }


    /**
     * 转发数据到真实连接
     */
    private void writeToRealConnection() {
        // 处理来自 TUN 设备的数据包
        try {
            log.info("=== 开始转发数据到真实连接 ===");
            while (!proxyConnection.closed) {
                proxyConnection.waitForData((packetData) -> {
                    if (packetData == null) {
                        checkIdleTime();
                        return;
                    }

                    realConnection.write(packetData.payload);
                });
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


    private void checkIdleTime() {
        if (System.currentTimeMillis() - proxyConnection.lastActivityTime > 120000) {
            closeProxyConnection();
        }
    }

    private void closeAndCleanup() {
        closeProxyConnection();
        closeRealConnection();
        cleanup.run();
    }
}
