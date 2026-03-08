package zxf.tunproxy.packet;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 数据包构造器 - 用于创建各种类型的 IP 数据包
 */
public class PacketBuilder {

    /**
     * 创建 IPv4 数据包
     */
    public static byte[] createIPv4Packet(String srcIP, String dstIP, int protocol, byte[] payload) {
        // IP 头固定 20 字节
        byte[] ipHeader = new byte[20];
        int totalLength = ipHeader.length + payload.length;

        // 版本和头长度 (IPv4, 头长度5个32位字)
        ipHeader[0] = (byte) 0x45;

        // 服务类型 (默认)
        ipHeader[1] = 0;

        // 总长度
        ipHeader[2] = (byte) ((totalLength >> 8) & 0xFF);
        ipHeader[3] = (byte) (totalLength & 0xFF);

        // 标识
        int identification = ThreadLocalRandom.current().nextInt(0xFFFF);
        ipHeader[4] = (byte) ((identification >> 8) & 0xFF);
        ipHeader[5] = (byte) (identification & 0xFF);

        // 标志和分片偏移 (不分片)
        ipHeader[6] = (byte) 0x40; // DF 标志
        ipHeader[7] = 0; // 分片偏移

        // TTL
        ipHeader[8] = 64; // 标准 TTL

        // 协议
        ipHeader[9] = (byte) protocol;

        // 头部校验和 (先设为0)
        ipHeader[10] = 0;
        ipHeader[11] = 0;

        // 源 IP 地址
        String[] srcParts = srcIP.split("\\.");
        for (int i = 0; i < 4; i++) {
            ipHeader[12 + i] = (byte) Integer.parseInt(srcParts[i]);
        }

        // 目标 IP 地址
        String[] dstParts = dstIP.split("\\.");
        for (int i = 0; i < 4; i++) {
            ipHeader[16 + i] = (byte) Integer.parseInt(dstParts[i]);
        }

        // 计算 IP 头校验和
        short ipChecksum = calculateChecksum(ipHeader, 0, ipHeader.length);
        ipHeader[10] = (byte) ((ipChecksum >> 8) & 0xFF);
        ipHeader[11] = (byte) (ipChecksum & 0xFF);

        // 组合 IP 头和载荷
        byte[] packet = new byte[totalLength];
        System.arraycopy(ipHeader, 0, packet, 0, ipHeader.length);
        System.arraycopy(payload, 0, packet, ipHeader.length, payload.length);

        return packet;
    }


    /**
     * 创建 TCP 数据包
     */
    public static byte[] createTCPPacket(String srcIP, String dstIP,
                                         int srcPort, int dstPort,
                                         long seq, long ack, byte flags,
                                         int window, byte[] payload) {
        // TCP 头 20 字节（无选项）
        byte[] tcpHeader = new byte[20];

        // 源端口和目标端口
        tcpHeader[0] = (byte) ((srcPort >> 8) & 0xFF);
        tcpHeader[1] = (byte) (srcPort & 0xFF);
        tcpHeader[2] = (byte) ((dstPort >> 8) & 0xFF);
        tcpHeader[3] = (byte) (dstPort & 0xFF);

        // 序列号
        tcpHeader[4] = (byte) ((seq >> 24) & 0xFF);
        tcpHeader[5] = (byte) ((seq >> 16) & 0xFF);
        tcpHeader[6] = (byte) ((seq >> 8) & 0xFF);
        tcpHeader[7] = (byte) (seq & 0xFF);

        // 确认号
        tcpHeader[8] = (byte) ((ack >> 24) & 0xFF);
        tcpHeader[9] = (byte) ((ack >> 16) & 0xFF);
        tcpHeader[10] = (byte) ((ack >> 8) & 0xFF);
        tcpHeader[11] = (byte) (ack & 0xFF);

        // 数据偏移和保留
        tcpHeader[12] = (byte) 0x50; // 数据偏移 5 (20字节)

        // 标志位
        tcpHeader[13] = flags;

        // 窗口大小
        tcpHeader[14] = (byte) ((window >> 8) & 0xFF);
        tcpHeader[15] = (byte) (window & 0xFF);

        // 校验和和紧急指针（先设为0）
        tcpHeader[16] = 0;
        tcpHeader[17] = 0;
        tcpHeader[18] = 0;
        tcpHeader[19] = 0;

        // 计算 TCP 校验和（包含伪头部）
        short tcpChecksum = calculateTCPChecksum(srcIP, dstIP, tcpHeader, payload);
        tcpHeader[16] = (byte) ((tcpChecksum >> 8) & 0xFF);
        tcpHeader[17] = (byte) (tcpChecksum & 0xFF);

        // 组合 TCP 头和载荷
        byte[] tcpPacket;
        if (payload != null) {
            tcpPacket = new byte[tcpHeader.length + payload.length];
            System.arraycopy(tcpHeader, 0, tcpPacket, 0, tcpHeader.length);
            System.arraycopy(payload, 0, tcpPacket, tcpHeader.length, payload.length);
        } else {
            tcpPacket = tcpHeader;
        }

        // 创建 IP 包
        return createIPv4Packet(srcIP, dstIP, 6, tcpPacket);
    }

    /**
     * 创建 UDP 数据包
     */
    public static byte[] createUDPPacket(String srcIP, String dstIP,
                                         int srcPort, int dstPort,
                                         byte[] payload) {
        // UDP 头 8 字节
        byte[] udpHeader = new byte[8];
        int udpLength = udpHeader.length + (payload != null ? payload.length : 0);

        // 源端口和目标端口
        udpHeader[0] = (byte) ((srcPort >> 8) & 0xFF);
        udpHeader[1] = (byte) (srcPort & 0xFF);
        udpHeader[2] = (byte) ((dstPort >> 8) & 0xFF);
        udpHeader[3] = (byte) (dstPort & 0xFF);

        // 长度
        udpHeader[4] = (byte) ((udpLength >> 8) & 0xFF);
        udpHeader[5] = (byte) (udpLength & 0xFF);

        // 校验和（先设为0）
        udpHeader[6] = 0;
        udpHeader[7] = 0;

        // 计算 UDP 校验和（包含伪头部）
        short udpChecksum = calculateUDPChecksum(srcIP, dstIP, udpHeader, payload);
        udpHeader[6] = (byte) ((udpChecksum >> 8) & 0xFF);
        udpHeader[7] = (byte) (udpChecksum & 0xFF);

        // 组合 UDP 头和载荷
        byte[] udpPacket;
        if (payload != null) {
            udpPacket = new byte[udpHeader.length + payload.length];
            System.arraycopy(udpHeader, 0, udpPacket, 0, udpHeader.length);
            System.arraycopy(payload, 0, udpPacket, udpHeader.length, payload.length);
        } else {
            udpPacket = udpHeader;
        }

        // 创建 IP 包
        return createIPv4Packet(srcIP, dstIP, 17, udpPacket);
    }

    /**
     * 计算 TCP 校验和（包含伪头部）
     */
    private static short calculateTCPChecksum(String srcIP, String dstIP,
                                              byte[] tcpHeader, byte[] payload) {
        return calculateTransportChecksum(srcIP, dstIP, 6, tcpHeader, payload);
    }

    /**
     * 计算 UDP 校验和（包含伪头部）
     */
    private static short calculateUDPChecksum(String srcIP, String dstIP,
                                              byte[] udpHeader, byte[] payload) {
        return calculateTransportChecksum(srcIP, dstIP, 17, udpHeader, payload);
    }

    /**
     * 计算传输层校验和（TCP/UDP）
     */
    private static short calculateTransportChecksum(String srcIP, String dstIP,
                                                    int protocol, byte[] header, byte[] payload) {
        // 创建伪头部
        byte[] pseudoHeader = new byte[12];

        // 源 IP 地址
        String[] srcParts = srcIP.split("\\.");
        for (int i = 0; i < 4; i++) {
            pseudoHeader[i] = (byte) Integer.parseInt(srcParts[i]);
        }

        // 目标 IP 地址
        String[] dstParts = dstIP.split("\\.");
        for (int i = 0; i < 4; i++) {
            pseudoHeader[4 + i] = (byte) Integer.parseInt(dstParts[i]);
        }

        // 保留字节和协议类型
        pseudoHeader[8] = 0;
        pseudoHeader[9] = (byte) protocol;

        // 传输层长度
        int transportLength = header.length + (payload != null ? payload.length : 0);
        pseudoHeader[10] = (byte) ((transportLength >> 8) & 0xFF);
        pseudoHeader[11] = (byte) (transportLength & 0xFF);

        // 组合所有数据
        byte[] allData = new byte[pseudoHeader.length + transportLength];
        System.arraycopy(pseudoHeader, 0, allData, 0, pseudoHeader.length);
        System.arraycopy(header, 0, allData, pseudoHeader.length, header.length);
        if (payload != null) {
            System.arraycopy(payload, 0, allData, pseudoHeader.length + header.length, payload.length);
        }

        return calculateChecksum(allData, 0, allData.length);
    }

    /**
     * 计算校验和
     */
    public static short calculateChecksum(byte[] buf, int offset, int length) {
        int sum = 0;
        int i = offset;

        while (i < offset + length - 1) {
            sum += ((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF);
            i += 2;
        }

        if (i == offset + length - 1) {
            sum += (buf[i] & 0xFF) << 8;
        }

        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        return (short) ~sum;
    }
}
