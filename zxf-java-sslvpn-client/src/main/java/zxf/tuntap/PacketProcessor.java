package zxf.tuntap;

import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class PacketProcessor {

    // IP头结构常量
    private static final int IP_HEADER_LENGTH_OFFSET = 0;
    private static final int IP_PROTOCOL_OFFSET = 9;
    private static final int IP_SRC_ADDR_OFFSET = 12;
    private static final int IP_DST_ADDR_OFFSET = 16;

    // 协议类型
    private static final byte PROTOCOL_TCP = 6;
    private static final byte PROTOCOL_UDP = 17;

    private ProxyServer proxyServer;

    public PacketProcessor(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    public void processPacket(ByteBuffer packet) {
        if (!isValidIPPacket(packet)) {
            return;
        }

        byte protocol = getProtocol(packet);
        InetAddress srcAddr = getSourceAddress(packet);
        InetAddress dstAddr = getDestinationAddress(packet);

        System.out.printf("Packet: %s -> %s, Protocol: %d ",
                srcAddr.getHostAddress(), dstAddr.getHostAddress(), protocol);

        // 根据协议类型处理
        switch (protocol) {
            case PROTOCOL_TCP:
                processTcpPacket(packet, srcAddr, dstAddr);
                break;
            case PROTOCOL_UDP:
                processUdpPacket(packet, srcAddr, dstAddr);
                break;
            default:
                // 其他协议直接转发
                forwardPacket(packet);
                break;
        }
    }

    private boolean isValidIPPacket(ByteBuffer packet) {
        if (packet.remaining() < 20) {
            return false;
        }

        // 检查IP版本
        byte version = (byte) ((packet.get(0) >> 4) & 0x0F);
        return version == 4; // IPv4
    }

    private byte getProtocol(ByteBuffer packet) {
        return packet.get(IP_PROTOCOL_OFFSET);
    }

    private InetAddress getSourceAddress(ByteBuffer packet) {
        byte[] addr = new byte[4];
        for (int i = 0; i < 4; i++) {
            addr[i] = packet.get(IP_SRC_ADDR_OFFSET + i);
        }
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private InetAddress getDestinationAddress(ByteBuffer packet) {
        byte[] addr = new byte[4];
        for (int i = 0; i < 4; i++) {
            addr[i] = packet.get(IP_DST_ADDR_OFFSET + i);
        }
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private void processTcpPacket(ByteBuffer packet, InetAddress src, InetAddress dst) {
        // 提取TCP端口等信息
        int ipHeaderLength = (packet.get(0) & 0x0F) * 4;
        if (packet.remaining() < ipHeaderLength + 20) {
            return; // 不是完整的TCP包
        }

        int srcPort = ((packet.get(ipHeaderLength) & 0xFF) << 8) |
                (packet.get(ipHeaderLength + 1) & 0xFF);
        int dstPort = ((packet.get(ipHeaderLength + 2) & 0xFF) << 8) |
                (packet.get(ipHeaderLength + 3) & 0xFF);

        System.out.printf("TCP: %s:%d -> %s:%d ",
                src.getHostAddress(), srcPort, dst.getHostAddress(), dstPort);

        // 交给代理服务器处理
        proxyServer.handleTcpPacket(packet, src, dst, srcPort, dstPort);
    }

    private void processUdpPacket(ByteBuffer packet, InetAddress src, InetAddress dst) {
        // UDP包处理逻辑
        forwardPacket(packet);
    }

    private void forwardPacket(ByteBuffer packet) {
        // 默认转发逻辑
        System.out.println("Forwarding packet: " + packet.remaining() + " bytes");
    }
}
