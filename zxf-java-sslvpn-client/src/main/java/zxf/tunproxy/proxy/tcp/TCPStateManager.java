package zxf.tunproxy.proxy.tcp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP 状态管理器 - 管理序列号和确认号
 */
public class TCPStateManager {
    private final ConcurrentHashMap<String, TCPConnectionState> connectionStates;
    private final AtomicLong sequenceNumberGenerator;

    public TCPStateManager() {
        this.connectionStates = new ConcurrentHashMap<>();
        this.sequenceNumberGenerator = new AtomicLong(System.currentTimeMillis() * 1000);
    }

    /**
     * TCP 连接状态
     */
    public static class TCPConnectionState {
        // 客户端到服务器方向
        public long clientSeq; // 客户端序列号
        public long clientAck; // 客户端确认号
        public long clientWindow; // 客户端窗口大小

        // 服务器到客户端方向
        public long serverSeq; // 服务器序列号
        public long serverAck; // 服务器确认号
        public long serverWindow; // 服务器窗口大小

        // 连接状态
        public boolean clientSynSent;
        public boolean serverSynReceived;
        public boolean established;
        public boolean clientFinSent;
        public boolean serverFinSent;

        // 时间戳
        public long lastClientActivity;
        public long lastServerActivity;

        public TCPConnectionState() {
            this.clientSeq = 0;
            this.clientAck = 0;
            this.clientWindow = 0;
            this.serverSeq = 0;
            this.serverAck = 0;
            this.serverWindow = 0;
            this.clientSynSent = false;
            this.serverSynReceived = false;
            this.established = false;
            this.clientFinSent = false;
            this.serverFinSent = false;
            this.lastClientActivity = System.currentTimeMillis();
            this.lastServerActivity = System.currentTimeMillis();
        }

        /**
         * 更新客户端活动时间
         */
        public void updateClientActivity() {
            this.lastClientActivity = System.currentTimeMillis();
        }

        /**
         * 更新服务器活动时间
         */
        public void updateServerActivity() {
            this.lastServerActivity = System.currentTimeMillis();
        }

        /**
         * 检查连接是否超时
         */
        public boolean isTimeout(long timeoutMs) {
            long currentTime = System.currentTimeMillis();
            return (currentTime - lastClientActivity > timeoutMs) &&
                    (currentTime - lastServerActivity > timeoutMs);
        }
    }

    /**
     * 生成初始序列号
     */
    public long generateInitialSequenceNumber() {
        return sequenceNumberGenerator.getAndAdd(1) % 0xFFFFFFFFL;
    }

    /**
     * 获取连接状态
     */
    public TCPConnectionState getConnectionState(String connectionKey) {
        return connectionStates.get(connectionKey);
    }

    /**
     * 创建新的连接状态
     */
    public TCPConnectionState createConnectionState(String connectionKey) {
        TCPConnectionState state = new TCPConnectionState();
        connectionStates.put(connectionKey, state);
        return state;
    }

    /**
     * 移除连接状态
     */
    public void removeConnectionState(String connectionKey) {
        connectionStates.remove(connectionKey);
    }

    /**
     * 更新客户端序列号
     */
    public void updateClientSequence(String connectionKey, long seq, long ack, int window) {
        TCPConnectionState state = connectionStates.get(connectionKey);
        if (state != null) {
            state.clientSeq = seq;
            state.clientAck = ack;
            state.clientWindow = window;
            state.updateClientActivity();
        }
    }

    /**
     * 更新服务器序列号
     */
    public void updateServerSequence(String connectionKey, long seq, long ack, int window) {
        TCPConnectionState state = connectionStates.get(connectionKey);
        if (state != null) {
            state.serverSeq = seq;
            state.serverAck = ack;
            state.serverWindow = window;
            state.updateServerActivity();
        }
    }

    /**
     * 设置连接建立状态
     */
    public void setConnectionEstablished(String connectionKey) {
        TCPConnectionState state = connectionStates.get(connectionKey);
        if (state != null) {
            state.established = true;
        }
    }

    /**
     * 清理过期连接
     */
    public void cleanupExpiredConnections(long timeoutMs) {
        connectionStates.entrySet().removeIf(entry ->
                entry.getValue().isTimeout(timeoutMs));
    }

    /**
     * 获取连接统计
     */
    public int getActiveConnections() {
        return connectionStates.size();
    }
}
