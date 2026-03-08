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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Slf4j
public class TCPSession {
    private final BlockingQueue<PacketParser.TCPPacket> packetQueue = new LinkedBlockingQueue<>();

    public final String srcIP;
    public final int srcPort;
    public final String dstIP;
    public final int dstPort;

    private final TunProxy tunProxy;

    private final AtomicLong clientNextSeq = new AtomicLong(0);
    private final AtomicLong serverNextSeq = new AtomicLong(0);
    private final AtomicLong serverSentSeq = new AtomicLong(0);
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
        while (true) {

            PacketParser.TCPPacket packetData = packetQueue.poll(30, TimeUnit.SECONDS);
            if (packetData == null) {
                consumer.accept(packetData);
                return;
            }

            if (packetData.ackNumber != serverSentSeq.get() || packetData.sequenceNumber != clientNextSeq.get()) {
                log.info("收到 无效 包 {}, {}, {}", packetData, clientNextSeq, serverSentSeq);
                continue;
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                log.info("收到 RST 包 {}, {}, {}", packetData, clientNextSeq, serverSentSeq);
                throw new SessionException.SessionResetException();
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
                clientFINReceived = true;
                log.info("收到 FIN 包 {}, {}, {}", packetData, clientNextSeq, serverSentSeq);
                throw new SessionException.SessionEndException();
            }

            if (packetData.flags == PacketParser.TCPPacket.ACK) {
                log.info("收到 ACK 包 {}, {}, {}", packetData, clientNextSeq, serverSentSeq);
                continue;
            }

            if (packetData.hasPayload()) {
                log.info("收到 DATA 包 {}, {}, {}", packetData, clientNextSeq, serverSentSeq);
                consumer.accept(packetData);

                clientNextSeq.addAndGet(packetData.payload.length);
                sendPureAck();

                return;
            }
        }
    }

    public void sendData(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) return;
        byte flags = (byte) (PacketParser.TCPPacket.PSH | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq.get(),
                clientNextSeq.get(), flags, serverWindow, payload);
        log.info("发送 DATA: {}, {}, {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)), serverNextSeq, clientNextSeq);
        serverNextSeq.addAndGet(payload.length);
        tunProxy.submitPacket(packet, () -> {
            lastActivityTime = System.currentTimeMillis();
            serverSentSeq.addAndGet(payload.length);
        });
    }

    private void sendPureAck() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq.get(),
                clientNextSeq.get(), flags, serverWindow, null);
        log.info("发送 Pure ACK: {}, {}, {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)), serverNextSeq, clientNextSeq);
        tunProxy.submitPacket(packet, () -> {
        });
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
                log.info("收到 RST 包 {}, {}, {}", packetData, serverSentSeq, serverNextSeq);
                throw new SessionException.SessionResetException();
            }
            if (packetData.hasFlag(PacketParser.TCPPacket.SYN) && !packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                // 更新状态
                clientNextSeq.set(packetData.sequenceNumber + 1);
                log.info("收到 SYN 包 {}, {}, {}", packetData, serverSentSeq, serverNextSeq);
                return packetData;
            }
        }
        return null;
    }

    private void sendSYNACK() throws Exception {
        long initialSeq = JavaUtils.getUnsignedInt(ThreadLocalRandom.current().nextInt());
        serverNextSeq.set(initialSeq);
        serverSentSeq.set(initialSeq);

        byte flags = (byte) (PacketParser.TCPPacket.SYN | PacketParser.TCPPacket.ACK);
        byte[] responsePacket = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq.get(), clientNextSeq.get(), flags, serverWindow, null);

        log.info("发送 SYN-ACK: {}, {}, {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(responsePacket, responsePacket.length)), serverNextSeq, clientNextSeq);

        serverNextSeq.addAndGet(1);
        tunProxy.submitPacket(responsePacket, () -> {
            serverSentSeq.addAndGet(1);
        });
    }

    private PacketParser.TCPPacket waitForACK() throws InterruptedException, SessionException.SessionResetException {
        log.info("等待 ACK 包...");
        long endTime = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < endTime) { // 5秒超时
            PacketParser.TCPPacket packetData = packetQueue.poll(100, TimeUnit.MILLISECONDS);
            if (packetData == null) continue;

            if (packetData.ackNumber != serverSentSeq.get() || packetData.sequenceNumber != clientNextSeq.get()) {
                log.info("收到 无效ACK 包 {}, {}, {}", packetData, serverSentSeq, serverSentSeq);
                return null;
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                clientRSTReceived = true;
                log.info("收到 RST 包 {}, {}, {}", packetData, serverSentSeq, serverSentSeq);
                throw new SessionException.SessionResetException();
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.ACK)) {
                log.info("收到 有效ACK 包 {}, {}, {}", packetData, serverSentSeq, serverSentSeq);
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

            if (packetData.ackNumber != serverSentSeq.get() || packetData.sequenceNumber != clientNextSeq.get()) {
                log.info("收到 无效包 {}, {}, {}", packetData, serverSentSeq, serverNextSeq);
                return null;
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.RST)) {
                clientRSTReceived = true;
                log.info("收到 RST 包 {}, {}, {}", packetData, serverSentSeq, serverSentSeq);
                throw new SessionException.SessionResetException();
            }

            if (packetData.hasFlag(PacketParser.TCPPacket.FIN)) {
                log.info("收到 有效FIN 包 {}, {}, {}", packetData, serverSentSeq, serverSentSeq);
                return packetData;
            }
        }
        return null;
    }


    private void sendFin() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.FIN);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq.get(),
                clientNextSeq.get(), flags, serverWindow, null);

        log.info("发送 FIN: {}, {}, {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)), serverNextSeq, clientNextSeq);

        serverNextSeq.addAndGet(1);
        tunProxy.submitPacket(packet, () -> {
            serverSentSeq.addAndGet(1);
            serverFINSent = true;
        });
    }

    private void sendRST() throws Exception {
        byte flags = (byte) (PacketParser.TCPPacket.RST | PacketParser.TCPPacket.ACK);
        byte[] packet = PacketBuilder.createTCPPacket(dstIP, srcIP, dstPort, srcPort, serverNextSeq.get(),
                clientNextSeq.get(), flags, 0, null);

        log.info("发送 RST: {}, {}, {}", PacketParser.parseTCPPacket(PacketParser.parseIPPacket(packet, packet.length)), serverNextSeq, clientNextSeq);

        tunProxy.submitPacket(packet, () -> {
        });
    }
}
