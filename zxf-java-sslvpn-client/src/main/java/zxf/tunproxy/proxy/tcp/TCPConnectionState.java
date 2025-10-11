package zxf.tunproxy.proxy.tcp;

import zxf.tunproxy.packet.PacketParser;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

public class TCPConnectionState {
    // 连接信息
    public final String srcIP;
    public final String dstIP;
    public final int srcPort;
    public final int dstPort;

    //
    public volatile Socket realSocket;

    // 服务器到客户端方向
    public AtomicLong serverSeq = new AtomicLong(1000); // 服务器序列号
    public AtomicLong serverAck = new AtomicLong(0); // 服务器确认号
    public AtomicLong serverWindow = new AtomicLong(0); // 服务器窗口大小

    // 连接状态
    public volatile boolean clientSynSent = false;
    public volatile boolean serverSynReceived = false;
    public volatile boolean established = false;
    public volatile boolean clientFinSent = false;
    public volatile boolean serverFinSent = false;

    public TCPConnectionState(PacketParser.IPPacket initalIpPacket) {
        this.srcIP = initalIpPacket.srcIP;
        this.srcPort = initalIpPacket.srcPort;
        this.dstIP = initalIpPacket.dstIP;
        this.dstPort = initalIpPacket.dstPort;
    }
}
