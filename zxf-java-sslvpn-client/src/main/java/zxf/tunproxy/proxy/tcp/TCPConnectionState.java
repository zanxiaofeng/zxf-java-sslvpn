package zxf.tunproxy.proxy.tcp;

import zxf.tunproxy.packet.PacketParser;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPConnectionState {
    // 连接信息
    public final String srcIP;
    public final String dstIP;
    public final int srcPort;
    public final int dstPort;


    public volatile Socket realSocket;

    public volatile long clientInitialSeq;
    public volatile long serverInitialSeq;
    public volatile long serverNextSeq;
    public volatile long expectedClientSeq;

    public volatile int serverWindow = 65525;


    // 连接状态
    public volatile AtomicBoolean handshakeDone = new AtomicBoolean(false);
    public volatile boolean clientFinReceived = false;
    public volatile boolean serverFinSent = false;
    public volatile boolean clientFinAcked = false;
    public volatile boolean closed = false;


    public volatile long lastActivityTime = System.currentTimeMillis();

    public TCPConnectionState(PacketParser.IPPacket initalIpPacket) {
        this.srcIP = initalIpPacket.srcIP;
        this.srcPort = initalIpPacket.srcPort;
        this.dstIP = initalIpPacket.dstIP;
        this.dstPort = initalIpPacket.dstPort;
    }
}
