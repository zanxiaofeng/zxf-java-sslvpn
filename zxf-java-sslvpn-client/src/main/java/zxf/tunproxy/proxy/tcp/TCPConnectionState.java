package zxf.tunproxy.proxy.tcp;

import zxf.tunproxy.packet.PacketParser;

import java.net.Socket;

public class TCPConnectionState {
    // 连接信息
    public final String srcIP;
    public final String dstIP;
    public final int srcPort;
    public final int dstPort;

    //
    public Socket realSocket;

    // 客户端到服务器方向
    public long clientSeq = 0; // 客户端序列号
    public long clientAck = 0; // 客户端确认号
    public long clientWindow = 0; // 客户端窗口大小

    // 服务器到客户端方向
    public long serverSeq = 0; // 服务器序列号
    public long serverAck = 0; // 服务器确认号
    public long serverWindow = 0; // 服务器窗口大小

    // 连接状态
    public boolean clientSynSent = false;
    public boolean serverSynReceived = false;
    public boolean established = false;
    public boolean clientFinSent = false;
    public boolean serverFinSent = false;

    // 时间戳
    public long lastClientActivity = System.currentTimeMillis();
    public long lastServerActivity = System.currentTimeMillis();

    public TCPConnectionState(PacketParser.IPPacket initalIpPacket) {
        this.srcIP = initalIpPacket.sourceIP;
        this.srcPort = initalIpPacket.sourcePort;
        this.dstIP = initalIpPacket.destIP;
        this.dstPort = initalIpPacket.destPort;
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
        return (currentTime - lastClientActivity > timeoutMs) && (currentTime - lastServerActivity > timeoutMs);
    }
}
