package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.JavaUtils;
import zxf.tunproxy.packet.PacketBuilder;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.TunProxy;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class TCPSession {
    private final BlockingQueue<PacketParser.TCPPacket> packetQueue = new LinkedBlockingQueue<>();

    public final String srcIP;
    public final int srcPort;
    public final String dstIP;
    public final int dstPort;

    private final TunProxy tunProxy;

    private volatile long clientInitialSeq;
    private volatile long clientNextSeq;
    private volatile long serverInitialSeq;
    private volatile long serverNextSeq;
    private volatile int serverWindow = 65525;

    private volatile boolean clientRSTReceived = false;
    private volatile boolean clientFINReceived = false;
    private volatile boolean serverFINSent = false;
    private volatile boolean clientFINAcked = false;
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

    public void waitForData(Consumer<PacketParser.TCPPacket> consumer) throws Exception {
        log.info("等待 DATA 包...");
        PacketParser.TCPPacket packetData = packetQueue.poll(1000, TimeUnit.SECONDS);
        if (packetData == null) {
            consumer.accept(packetData);
            return;
        }

        if (packetData.ackNumber != serverNextSeq || packetData.sequenceNumber != clientNextSeq) {
            log.info("收到 无效ACK 包 {}", packetData);
            //sendPureAck();
            //waitForData(consumer);
            return;
        }

        if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
            log.info("收到 RST 包 {}", packetData);
            throw new SessionException.SessionResetException();
        }

        if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
            clientFINReceived = true;
            log.info("收到 FIN 包 {}", packetData);
            throw new SessionException.SessionEndException();
        }

        log.info("收到 DATA 包 {}", packetData);
        consumer.accept(packetData);

        if (packetData.hasPayload()) {
            clientNextSeq += packetData.payload.length;
        }

        if (packetData.hasPayload() || packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
            log.info("sendPureAck");
            sendPureAck();
        }
    }

    public void sendData(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) return;
        byte flags = (byte) (PacketParser.TCPPacket.PSH | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                clientNextSeq, flags, serverWindow, payload);
        log.info("发送 DATA: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)));
        tunProxy.submitPacket(packet);
        lastActivityTime = System.currentTimeMillis();
        serverNextSeq += payload.length;
    }

    public void sendPureAck() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                clientNextSeq, flags, serverWindow, null);
        log.info("发送 Pure ACK: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)));
        tunProxy.submitPacket(packet);
    }

    public Boolean startSession() throws Exception {
        PacketParser.TCPPacket synPacket = waitForSYN();
        if (synPacket == null) {
            return false;
        }

        // 发送 SYN-ACK 响应
        sendSYNACK();

        // 等待 ACK 完成握手
        PacketParser.TCPPacket ackPacket = waitForACK();
        if (ackPacket == null) {
            return false;
        }

        return true;
    }

    public void closeSession() {
        try {
            if (!clientFINReceived && !serverFINSent) {
                sendFin();
                serverFINSent = true;
                waitForACK();
                clientFINAcked = true;
                waitForFIN();
                clientFINAcked = true;
                sendPureAck();
            }

            if (clientFINReceived && !serverFINSent) {
                sendPureAck();
                sendFin();
                serverFINSent = true;
                waitForACK();
                clientFINAcked = true;
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
                log.info("收到 RST 包 {}", packetData);
                throw new SessionException.SessionResetException();
            }
            if (packetData.hasFlag(PacketParser.TCPPacket.SYN) & !packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                // 更新状态
                clientInitialSeq = packetData.sequenceNumber;
                clientNextSeq = clientInitialSeq + 1;
                log.info("收到 SYN 包 {}", packetData);
                return packetData;
            }
        }
        return null;
    }

    private void sendSYNACK() throws Exception {
        serverInitialSeq = JavaUtils.getUnsignedInt(ThreadLocalRandom.current().nextInt());

        byte flags = (byte) (PacketParser.TCPPacket.SYN | PacketParser.TCPPacket.ACK);
        byte[] responsePacket = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverInitialSeq, clientNextSeq, flags, serverWindow, null);

        log.info("{},{}", serverInitialSeq, serverNextSeq);
        log.info("发送 SYN-ACK: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(responsePacket, responsePacket.length)));

        tunProxy.submitPacket(responsePacket);
        serverNextSeq = serverInitialSeq + 1;
    }

    private PacketParser.TCPPacket waitForACK() throws InterruptedException, SessionException.SessionResetException {
        log.info("等待 ACK 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;

            if (packetData.ackNumber != serverNextSeq || packetData.sequenceNumber != clientNextSeq) {
                log.info("收到 无效ACK 包 {}, {}, {}", packetData, serverNextSeq, clientNextSeq);
                return null;
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                clientRSTReceived = true;
                log.info("收到 RST 包 {}", packetData);
                throw new SessionException.SessionResetException();
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                log.info("收到 有效ACK 包 {}", packetData);
                return packetData;
            }
        }
        return null;
    }

    private PacketParser.TCPPacket waitForFIN() throws InterruptedException, SessionException.SessionResetException {
        log.info("等待 FIN 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;

            if (packetData.ackNumber != serverNextSeq || packetData.sequenceNumber != clientNextSeq) {
                log.info("收到 无效包 {}", packetData);
                return null;
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                clientRSTReceived = true;
                log.info("收到 RST 包 {}", packetData);
                throw new SessionException.SessionResetException();
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
                log.info("收到 有效FIN 包 {}", packetData);
                return packetData;
            }
        }
        return null;
    }


    private void sendFin() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.FIN);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                clientNextSeq, flags, serverWindow, null);

        log.info("发送 SYN-ACK: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)));

        tunProxy.submitPacket(packet);
        serverNextSeq += 1;
        serverFINSent = true;
    }

    private void sendRST() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.RST | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq,
                clientNextSeq, flags, 0, null);

        log.info("发送 SYN-ACK: {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)));

        tunProxy.submitPacket(packet);
    }
}
