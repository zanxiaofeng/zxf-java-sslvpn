package zxf.tunproxy.proxy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接跟踪器 - 跟踪和管理网络连接状态
 */
public class ConnectionTracker {
    private final Map<String, ConnectionInfo> connections;
    private final AtomicLong connectionCounter;
    private final ScheduledExecutorService cleanupScheduler;

    public ConnectionTracker() {
        this.connections = new ConcurrentHashMap<>();
        this.connectionCounter = new AtomicLong(0);
        this.cleanupScheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * 连接信息类
     */
    public static class ConnectionInfo {
        public final String connectionId;
        public final String srcIP;
        public final String dstIP;
        public final int srcPort;
        public final int dstPort;
        public final int protocol;
        public final long startTime;
        public long lastActivity;
        public long packetsSent;
        public long packetsReceived;
        public long bytesSent;
        public long bytesReceived;
        public ConnectionState state;

        public ConnectionInfo(String srcIP, String dstIP, int srcPort, int dstPort, int protocol) {
            this.connectionId = generateConnectionId(srcIP, dstIP, srcPort, dstPort, protocol);
            this.srcIP = srcIP;
            this.dstIP = dstIP;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
            this.protocol = protocol;
            this.startTime = System.currentTimeMillis();
            this.lastActivity = startTime;
            this.state = ConnectionState.NEW;
        }

        public void updateActivity(long bytes, boolean isSent) {
            this.lastActivity = System.currentTimeMillis();
            if (isSent) {
                this.packetsSent++;
                this.bytesSent += bytes;
            } else {
                this.packetsReceived++;
                this.bytesReceived += bytes;
            }
        }

        public long getDuration() {
            return System.currentTimeMillis() - startTime;
        }

        public boolean isExpired(long timeout) {
            return (System.currentTimeMillis() - lastActivity) > timeout;
        }
    }

    /**
     * 连接状态
     */
    public enum ConnectionState {
        NEW, ESTABLISHED, CLOSING, CLOSED, TIMEOUT
    }

    /**
     * 生成连接 ID
     */
    private static String generateConnectionId(String srcIP, String dstIP, int srcPort, int dstPort, int protocol) {
        return String.format("%s:%d->%s:%d/%d", srcIP, srcPort, dstIP, dstPort, protocol);
    }

    /**
     * 开始清理任务
     */
    public void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long timeout = 5 * 60 * 1000; // 5分钟超时
            cleanupExpiredConnections(timeout);
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 停止跟踪器
     */
    public void stop() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        connections.clear();
        System.out.println("连接跟踪器已停止");
    }

    /**
     * 注册新连接
     */
    public ConnectionInfo registerConnection(String srcIP, String dstIP,
                                             int srcPort, int dstPort, int protocol) {
        String connectionId = generateConnectionId(srcIP, dstIP, srcPort, dstPort, protocol);

        ConnectionInfo info = new ConnectionInfo(srcIP, dstIP, srcPort, dstPort, protocol);
        connections.put(connectionId, info);
        connectionCounter.incrementAndGet();

        return info;
    }

    /**
     * 获取连接信息
     */
    public ConnectionInfo getConnection(String srcIP, String dstIP,
                                        int srcPort, int dstPort, int protocol) {
        String connectionId = generateConnectionId(srcIP, dstIP, srcPort, dstPort, protocol);
        return connections.get(connectionId);
    }

    /**
     * 获取反向连接信息
     */
    public ConnectionInfo getReverseConnection(String srcIP, String dstIP,
                                               int srcPort, int dstPort, int protocol) {
        // 对于响应包，源和目标需要互换
        return getConnection(dstIP, srcIP, dstPort, srcPort, protocol);
    }

    /**
     * 更新连接活动
     */
    public void updateConnectionActivity(String srcIP, String dstIP,
                                         int srcPort, int dstPort, int protocol,
                                         long bytes, boolean isSent) {
        ConnectionInfo info = getConnection(srcIP, dstIP, srcPort, dstPort, protocol);
        if (info != null) {
            info.updateActivity(bytes, isSent);
        }
    }

    /**
     * 更新连接状态
     */
    public void updateConnectionState(String srcIP, String dstIP,
                                      int srcPort, int dstPort, int protocol,
                                      ConnectionState state) {
        ConnectionInfo info = getConnection(srcIP, dstIP, srcPort, dstPort, protocol);
        if (info != null) {
            info.state = state;
        }
    }

    /**
     * 清理过期连接
     */
    public void cleanupExpiredConnections(long timeout) {
        Iterator<Map.Entry<String, ConnectionInfo>> it = connections.entrySet().iterator();
        int removed = 0;

        while (it.hasNext()) {
            Map.Entry<String, ConnectionInfo> entry = it.next();
            ConnectionInfo info = entry.getValue();

            if (info.isExpired(timeout) || info.state == ConnectionState.CLOSED) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            System.out.printf("清理了 %d 个过期连接 ", removed);
        }
    }

    /**
     * 获取连接统计
     */
    public void printStatistics() {
        System.out.println("=== 连接统计 ===");
        System.out.printf("活跃连接数: %d ", connections.size());
        System.out.printf("总连接数: %d ", connectionCounter.get());

        long totalBytes = 0;
        long totalPackets = 0;

        for (ConnectionInfo info : connections.values()) {
            totalBytes += info.bytesSent + info.bytesReceived;
            totalPackets += info.packetsSent + info.packetsReceived;
        }

        System.out.printf("总数据量: %.2f MB ", totalBytes / (1024.0 * 1024.0));
        System.out.printf("总包数: %d ", totalPackets);
    }
}