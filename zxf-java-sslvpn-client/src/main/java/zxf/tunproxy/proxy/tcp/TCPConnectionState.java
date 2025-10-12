package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketBuilder;
import zxf.tunproxy.packet.PacketParser;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TCPConnectionState {
    // 连接信息
    public final String srcIP;
    public final String dstIP;
    public final int srcPort;
    public final int dstPort;

    private BlockingQueue<PacketParser.TCPPacket> packetQueue = new LinkedBlockingQueue<>();

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


    /**
     * 等待 SYN 包
     */
    private PacketParser.TCPPacket waitForSYN() throws Exception {
        log.info("等待 SYN 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;
            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) return null;
            if (packetData.hasFlag(PacketParser.TCPPacket.SYN)) {
                // 更新状态
                log.info("收到 SYN 包 {}", packetData);
                clientInitialSeq = packetData.sequenceNumber & 0xFFFFFFFFL;
                expectedClientSeq = clientInitialSeq + 1;
                return packetData;
            }
        }
        return null;
    }

    /**
     * 发送 SYN-ACK 响应
     */
    private void sendSYNACK(PacketParser.TCPPacket synPacket) throws Exception {
        serverInitialSeq = ThreadLocalRandom.current().nextInt() & 0xFFFFFFFFL;
        serverNextSeq = serverInitialSeq + 1;

        // 创建 SYN-ACK 包
        byte flags = (byte) (PacketParser.TCPPacket.SYN | PacketParser.TCPPacket.ACK);

        // 创建响应包
        byte[] responsePacket = PacketBuilder.createTCPPacket(synPacket.ipPacket.dstIP, synPacket.ipPacket.srcIP,
                synPacket.dstPort, synPacket.srcPort, serverInitialSeq, expectedClientSeq, flags, serverWindow, null);

        log.info("发送 SYN-ACK: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(responsePacket, responsePacket.length)));
        //tunProxy.submitPacket(responsePacket);
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
}
