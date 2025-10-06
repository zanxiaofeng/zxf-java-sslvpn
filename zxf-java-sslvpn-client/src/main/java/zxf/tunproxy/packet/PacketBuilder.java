package zxf.tunproxy.packet;

import java.util.Random;

/**
 * 数据包构造器 - 用于创建各种类型的 IP 数据包
 */
public class PacketBuilder {
    private static final Random random = new Random();

    /**
     * 计算 IP 头校验和
     */
    public static short calculateIPChecksum(byte[] header, int offset, int length) {
        int sum = 0;
        int i = offset;

        // 处理 16 位字
        while (i < offset + length - 1) {
            sum += ((header[i] & 0xFF) << 8) | (header[i + 1] & 0xFF);
            i += 2;
        }

        // 如果长度是奇数，处理最后一个字节
        if (i == offset + length - 1) {
            sum += (header[i] & 0xFF) << 8;
        }

        // 将进位加到低16位
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        return (short) ~sum;
    }

    /**
     * 计算 UDP 校验和
     */
    public static short calculateUDPChecksum(byte[] ipHeader, byte[] udpHeader, byte[] payload) {
        int sum = 0;

        // 伪头部：源IP + 目标IP + 协议 + UDP长度
        // 源IP (12-15字节)
        for (int i = 12; i < 16; i += 2) {
            sum += ((ipHeader[i] & 0xFF) << 8) | (ipHeader[i + 1] & 0xFF);
        }

        // 目标IP (16-19字节)
        for (int i = 16; i < 20; i += 2) {
            sum += ((ipHeader[i] & 0xFF) << 8) | (ipHeader[i + 1] & 0xFF);
        }

        // 协议和UDP长度
        sum += (17 << 8); // 协议类型 UDP
        int udpLength = udpHeader.length + payload.length;
        sum += udpLength & 0xFFFF;

        // UDP头（校验和字段设为0）
        for (int i = 0; i < udpHeader.length; i += 2) {
            if (i == 6) continue; // 跳过校验和字段
            int word = ((udpHeader[i] & 0xFF) << 8);
            if (i + 1 < udpHeader.length) {
                word |= (udpHeader[i + 1] & 0xFF);
            }
            sum += word;
        }

        // 载荷数据
        for (int i = 0; i < payload.length; i += 2) {
            int word = ((payload[i] & 0xFF) << 8);
            if (i + 1 < payload.length) {
                word |= (payload[i + 1] & 0xFF);
            }
            sum += word;
        }

        // 处理进位
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        return (short) ~sum;
    }

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
        int identification = random.nextInt(0xFFFF);
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
        short ipChecksum = calculateIPChecksum(ipHeader, 0, ipHeader.length);
        ipHeader[10] = (byte) ((ipChecksum >> 8) & 0xFF);
        ipHeader[11] = (byte) (ipChecksum & 0xFF);

        // 组合 IP 头和载荷
        byte[] packet = new byte[totalLength];
        System.arraycopy(ipHeader, 0, packet, 0, ipHeader.length);
        System.arraycopy(payload, 0, packet, ipHeader.length, payload.length);

        return packet;
    }

    /**
     * 创建 UDP 数据包
     */
    public static byte[] createUDPPacket(String srcIP, String dstIP, int srcPort, int dstPort, byte[] data) {
        // UDP 头 8 字节
        byte[] udpHeader = new byte[8];
        int udpLength = udpHeader.length + data.length;

        // 源端口
        udpHeader[0] = (byte) ((srcPort >> 8) & 0xFF);
        udpHeader[1] = (byte) (srcPort & 0xFF);

        // 目标端口
        udpHeader[2] = (byte) ((dstPort >> 8) & 0xFF);
        udpHeader[3] = (byte) (dstPort & 0xFF);

        // UDP 长度
        udpHeader[4] = (byte) ((udpLength >> 8) & 0xFF);
        udpHeader[5] = (byte) (udpLength & 0xFF);

        // 校验和 (先设为0)
        udpHeader[6] = 0;
        udpHeader[7] = 0;

        // 创建 IP 包
        byte[] ipPacket = createIPv4Packet(srcIP, dstIP, 17,
                concatenate(udpHeader, data));

        // 计算 UDP 校验和
        byte[] ipHeader = new byte[20];
        System.arraycopy(ipPacket, 0, ipHeader, 0, 20);

        short udpChecksum = calculateUDPChecksum(ipHeader, udpHeader, data);

        // 设置 UDP 校验和
        ipPacket[26] = (byte) ((udpChecksum >> 8) & 0xFF);
        ipPacket[27] = (byte) (udpChecksum & 0xFF);

        return ipPacket;
    }

    /**
     * 创建 TCP 数据包 (简化版)
     */
    public static byte[] createTCPPacket(String srcIP, String dstIP, int srcPort, int dstPort, int seqNum, int ackNum, byte flags, byte[] data) {
        // TCP 头 20 字节 (无选项)
        byte[] tcpHeader = new byte[20];
        int tcpLength = tcpHeader.length + data.length;

        // 源端口
        tcpHeader[0] = (byte) ((srcPort >> 8) & 0xFF);
        tcpHeader[1] = (byte) (srcPort & 0xFF);

        // 目标端口
        tcpHeader[2] = (byte) ((dstPort >> 8) & 0xFF);
        tcpHeader[3] = (byte) (dstPort & 0xFF);

        // 序列号
        tcpHeader[4] = (byte) ((seqNum >> 24) & 0xFF);
        tcpHeader[5] = (byte) ((seqNum >> 16) & 0xFF);
        tcpHeader[6] = (byte) ((seqNum >> 8) & 0xFF);
        tcpHeader[7] = (byte) (seqNum & 0xFF);

        // 确认号
        tcpHeader[8] = (byte) ((ackNum >> 24) & 0xFF);
        tcpHeader[9] = (byte) ((ackNum >> 16) & 0xFF);
        tcpHeader[10] = (byte) ((ackNum >> 8) & 0xFF);
        tcpHeader[11] = (byte) (ackNum & 0xFF);

        // 数据偏移和保留 (数据偏移5，表示20字节头部)
        tcpHeader[12] = (byte) 0x50;

        // 标志位
        tcpHeader[13] = flags;

        // 窗口大小
        tcpHeader[14] = (byte) 0x16; // 5840 字节
        tcpHeader[15] = (byte) 0xD0;

        // 校验和 (先设为0)
        tcpHeader[16] = 0;
        tcpHeader[17] = 0;

        // 紧急指针
        tcpHeader[18] = 0;
        tcpHeader[19] = 0;

        // 创建 IP 包
        byte[] ipPacket = createIPv4Packet(srcIP, dstIP, 6,
                concatenate(tcpHeader, data));

        // TCP 校验和计算比较复杂，这里简化处理
        // 在实际应用中需要实现完整的 TCP 校验和计算

        return ipPacket;
    }

    /**
     * 创建 ICMP Echo Request 数据包
     */
    public static byte[] createICMPEchoRequest(String srcIP, String dstIP, int identifier, int sequence, byte[] data) {
        // ICMP 头 8 字节
        byte[] icmpHeader = new byte[8];

        // 类型 (8 = Echo Request)
        icmpHeader[0] = 8;

        // 代码
        icmpHeader[1] = 0;

        // 校验和 (先设为0)
        icmpHeader[2] = 0;
        icmpHeader[3] = 0;

        // 标识符
        icmpHeader[4] = (byte) ((identifier >> 8) & 0xFF);
        icmpHeader[5] = (byte) (identifier & 0xFF);

        // 序列号
        icmpHeader[6] = (byte) ((sequence >> 8) & 0xFF);
        icmpHeader[7] = (byte) (sequence & 0xFF);

        // 计算 ICMP 校验和 (包括头和数据)
        byte[] icmpPacket = concatenate(icmpHeader, data);
        short icmpChecksum = calculateIPChecksum(icmpPacket, 0, icmpPacket.length);

        // 设置校验和
        icmpPacket[2] = (byte) ((icmpChecksum >> 8) & 0xFF);
        icmpPacket[3] = (byte) (icmpChecksum & 0xFF);

        // 创建 IP 包
        return createIPv4Packet(srcIP, dstIP, 1, icmpPacket);
    }

    /**
     * 创建 ICMP Echo Reply 数据包
     */
    public static byte[] createICMPEchoReply(String srcIP, String dstIP, int identifier, int sequence, byte[] data) {
        // ICMP 头 8 字节
        byte[] icmpHeader = new byte[8];

        // 类型 (0 = Echo Reply)
        icmpHeader[0] = 0;

        // 代码
        icmpHeader[1] = 0;

        // 校验和 (先设为0)
        icmpHeader[2] = 0;
        icmpHeader[3] = 0;

        // 标识符
        icmpHeader[4] = (byte) ((identifier >> 8) & 0xFF);
        icmpHeader[5] = (byte) (identifier & 0xFF);

        // 序列号
        icmpHeader[6] = (byte) ((sequence >> 8) & 0xFF);
        icmpHeader[7] = (byte) (sequence & 0xFF);

        // 计算 ICMP 校验和
        byte[] icmpPacket = concatenate(icmpHeader, data);
        short icmpChecksum = calculateIPChecksum(icmpPacket, 0, icmpPacket.length);

        // 设置校验和
        icmpPacket[2] = (byte) ((icmpChecksum >> 8) & 0xFF);
        icmpPacket[3] = (byte) (icmpChecksum & 0xFF);

        // 创建 IP 包
        return createIPv4Packet(srcIP, dstIP, 1, icmpPacket);
    }

    /**
     * 创建自定义载荷的数据包
     */
    public static byte[] createCustomPacket(String srcIP, String dstIP, int protocol, String payload) {
        byte[] payloadBytes = payload.getBytes();
        return createIPv4Packet(srcIP, dstIP, protocol, payloadBytes);
    }

    /**
     * 字节数组拼接工具方法
     */
    private static byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * 生成随机数据
     */
    public static byte[] generateRandomData(int length) {
        byte[] data = new byte[length];
        random.nextBytes(data);
        return data;
    }

    /**
     * 生成测试字符串数据
     */
    public static byte[] generateTestData(String prefix, int length) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(" - ");

        while (sb.length() < length) {
            sb.append("0123456789ABCDEF");
        }

        // 截取到指定长度
        String result = sb.substring(0, length);
        return result.getBytes();
    }
}
