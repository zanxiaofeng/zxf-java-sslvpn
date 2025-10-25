package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketBuilder;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.TunProxy;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TCPSession {
    private final BlockingQueue<PacketParser.TCPPacket> packetQueue = new LinkedBlockingQueue<>();

    public final String srcIP;
    public final int srcPort;
    public final String dstIP;
    public final int dstPort;

    private TunProxy tunProxy;

    public volatile long clientInitialSeq;
    public volatile long serverInitialSeq;
    public volatile long serverNextSeq;
    public volatile long expectedClientSeq;
    public volatile int serverWindow = 65525;

    public volatile AtomicBoolean handshakeDone = new AtomicBoolean(false);
    public volatile boolean clientFINReceived = false;
    public volatile boolean clientRSTReceived = false;
    public volatile boolean serverFINSent = false;
    public volatile boolean clientFINAcked = false;
    public volatile boolean closed = false;

    public volatile long lastActivityTime = System.currentTimeMillis();

    public TCPSession(PacketParser.TCPPacket initialPacket, TunProxy tunProxy) {
        this.srcIP = initialPacket.ipPacket.srcIP;
        this.srcPort = initialPacket.srcPort;
        this.dstIP = initialPacket.ipPacket.dstIP;
        this.dstPort = initialPacket.dstPort;
        this.packetQueue.offer(initialPacket);
        this.tunProxy = tunProxy;
    }

    public void start(Runnable cleanup) {
        TCPSessionWorker session = new TCPSessionWorker(this, cleanup);
        session.start();
    }


    public void submitPacket(PacketParser.TCPPacket tcpPacket) {
        if (closed) return;
        packetQueue.offer(tcpPacket);
    }

    public PacketParser.TCPPacket waitForData() throws Exception {
        log.info("等待 DATA 包...");
        PacketParser.TCPPacket packetData = packetQueue.poll(1000, TimeUnit.SECONDS);
        if (packetData == null) return null;
        if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
            throw new SessionException.SessionResetException();
        }
        if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
            clientFINReceived = true;
            throw new SessionException.SessionEndException();
        }
        if (packetData.hasFlag(PacketParser.TCPPacket.ACK) && serverFINSent) {
            if ((packetData.ackNumber & 0xFFFFFFFFL) == (serverNextSeq & 0xFFFFFFFFL)) {
                clientFINAcked = true;
                throw new SessionException.SessionEndException();
            }
        }

        if (packetData.sequenceNumber != expectedClientSeq) {
            sendPureAck();
            return waitForData();
        }

        log.info("收到 DATA 包 {}", packetData);
        return packetData;
    }

    public void sendData(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) return;
        byte flags = (byte) (PacketParser.TCPPacket.PSH | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                expectedClientSeq, flags, serverWindow, payload);
        tunProxy.submitPacket(packet);
        lastActivityTime = System.currentTimeMillis();
        serverNextSeq += payload.length;
    }

    public void sendPureAck() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                expectedClientSeq, flags, serverWindow, null);
        tunProxy.submitPacket(packet);
    }

    public Boolean startSession() throws Exception {
        PacketParser.TCPPacket synPacket = waitForSYN();
        if (synPacket == null) {
            return false;
        }

        // 发送 SYN-ACK 响应
        sendSYNACK(synPacket);

        // 等待 ACK 完成握手
        PacketParser.TCPPacket ackPacket = waitForACK();
        if (ackPacket == null) {
            return false;
        }

        return true;
    }

    public void closeSession() {
        try {
            if (!serverFINSent) {
                sendFin();
            }
        } catch (Exception ignore) {

        }
    }

    private PacketParser.TCPPacket waitForSYN() throws Exception {
        log.info("等待 SYN 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;
            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                clientRSTReceived = true;
                throw new SessionException.SessionResetException();
            }
            if (packetData.hasFlag(PacketParser.TCPPacket.SYN) & !packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                // 更新状态
                clientInitialSeq = packetData.sequenceNumber & 0xFFFFFFFFL;
                expectedClientSeq = clientInitialSeq + 1;
                log.info("收到 SYN 包 {}, {}, {}", packetData, packetData.sequenceNumber, clientInitialSeq);
                return packetData;
            }
        }
        return null;
    }

    private PacketParser.TCPPacket waitForACK() throws InterruptedException, SessionException.SessionResetException {
        log.info("等待 ACK 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;
            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                clientRSTReceived = true;
                throw new SessionException.SessionResetException();
            }
            if (packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                if (packetData.ackNumber != serverNextSeq || packetData.sequenceNumber != expectedClientSeq) {
                    log.info("收到 无效ACK 包 {}", packetData);
                    return null;
                }

                log.info("收到 有效ACK 包 {}", packetData);
                // 握手完成
                handshakeDone.set(true);
                return packetData;
            }
        }
        return null;
    }

    private void sendSYNACK(PacketParser.TCPPacket synPacket) throws Exception {
        serverInitialSeq = ThreadLocalRandom.current().nextInt() & 0xFFFFFFFFL;
        serverNextSeq = serverInitialSeq + 1;

        // 创建 SYN-ACK 包
        byte flags = (byte) (PacketParser.TCPPacket.SYN | PacketParser.TCPPacket.ACK);

        // 创建响应包
        byte[] responsePacket = PacketBuilder.createTCPPacket(synPacket.ipPacket.dstIP, synPacket.ipPacket.srcIP, synPacket.dstPort, synPacket.srcPort,
                serverInitialSeq, expectedClientSeq, flags, serverWindow, null);

        log.info("发送 SYN-ACK: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(responsePacket, responsePacket.length)));

        tunProxy.submitPacket(responsePacket);
    }

    private void sendFin() throws Exception {
        if (serverFINSent) return;
        byte flags = (byte) (PacketParser.TCPPacket.FIN | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                expectedClientSeq, flags, serverWindow, null);
        tunProxy.submitPacket(packet);
        serverNextSeq += 1;
        serverFINSent = true;
    }

    private void sendRST() throws Exception {
        if (closed) return;
        byte flags = (byte) (PacketParser.TCPPacket.RST | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                expectedClientSeq, flags, 0, null);
        tunProxy.submitPacket(packet);
    }
}
