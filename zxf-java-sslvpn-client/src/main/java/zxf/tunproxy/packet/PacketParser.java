package zxf.tunproxy.packet;

/**
 * IP 数据包解析器
 */
public class PacketParser {
    private static final int IP_HEADER_MIN_LENGTH = 20;
    private static final int IPV6_HEADER_LENGTH = 40;

    /**
     * 解析 IP 数据包
     */
    public static IPPacket parseIPPacket(byte[] packet, int length) {
        if (length < IP_HEADER_MIN_LENGTH) {
            return null;
        }

        // 检查 IP 版本
        int version = (packet[0] & 0xF0) >> 4;

        if (version == 4) {
            return parseIPv4Packet(packet, length);
        } else if (version == 6) {
            return parseIPv6Packet(packet, length);
        } else {
            System.err.println("不支持的 IP 版本: " + version);
            return null;
        }
    }

    /**
     * 解析 TCP 头
     */
    public static TCPPacket parseTCPPacket(IPPacket ipPacket) {
        if (ipPacket.payload == null || ipPacket.payload.length < 20) return null;

        TCPPacket header = new TCPPacket();
        header.ipPacket = ipPacket;

        // 源端口和目标端口
        header.srcPort = ((ipPacket.payload[0] & 0xFF) << 8) | (ipPacket.payload[1] & 0xFF);
        header.dstPort = ((ipPacket.payload[2] & 0xFF) << 8) | (ipPacket.payload[3] & 0xFF);

        // 序列号
        header.sequenceNumber = ((ipPacket.payload[4] & 0xFFL) << 24) |
                ((ipPacket.payload[5] & 0xFFL) << 16) |
                ((ipPacket.payload[6] & 0xFFL) << 8) |
                (ipPacket.payload[7] & 0xFFL);

        // 确认号
        header.ackNumber = ((ipPacket.payload[8] & 0xFFL) << 24) |
                ((ipPacket.payload[9] & 0xFFL) << 16) |
                ((ipPacket.payload[10] & 0xFFL) << 8) |
                (ipPacket.payload[11] & 0xFFL);

        // 数据偏移和标志
        header.headerLength = ((ipPacket.payload[12] & 0xF0) >> 4) * 4;
        header.flags = ipPacket.payload[13] & 0xFF;

        // 窗口大小
        header.windowSize = ((ipPacket.payload[14] & 0xFF) << 8) | (ipPacket.payload[15] & 0xFF);

        // 提取 TCP 载荷
        if (ipPacket.payload.length > header.headerLength) {
            int payloadLength = ipPacket.payload.length - header.headerLength;
            header.payload = new byte[payloadLength];
            System.arraycopy(ipPacket.payload, header.headerLength, header.payload, 0, payloadLength);
        }

        return header;
    }

    /**
     * 解析 UDP 头
     */
    public static UDPHeader parseUDPHeader(byte[] udpData) {
        if (udpData == null || udpData.length < 8) return null;

        UDPHeader header = new UDPHeader();

        // 源端口和目标端口
        header.srcPort = ((udpData[0] & 0xFF) << 8) | (udpData[1] & 0xFF);
        header.dstPort = ((udpData[2] & 0xFF) << 8) | (udpData[3] & 0xFF);

        // 长度
        header.length = ((udpData[4] & 0xFF) << 8) | (udpData[5] & 0xFF);

        // 校验和
        header.checksum = ((udpData[6] & 0xFF) << 8) | (udpData[7] & 0xFF);

        // 提取 UDP 载荷
        if (udpData.length > 8) {
            int payloadLength = udpData.length - 8;
            header.payload = new byte[payloadLength];
            System.arraycopy(udpData, 8, header.payload, 0, payloadLength);
        }

        return header;
    }


    /**
     * 解析 IPv4 数据包
     */
    private static IPPacket parseIPv4Packet(byte[] packet, int length) {
        IPPacket ipPacket = new IPPacket();
        ipPacket.version = 4;

        // IP 头长度（以 32 位字为单位）
        int ihl = (packet[0] & 0x0F);
        ipPacket.headerLength = ihl * 4;

        // 服务类型
        ipPacket.dscp = (packet[1] & 0xFC) >> 2;
        ipPacket.ecn = packet[1] & 0x03;

        // 总长度
        ipPacket.totalLength = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);

        // 标识
        ipPacket.identification = ((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF);

        // 标志和分片偏移
        int flagsAndOffset = ((packet[6] & 0xFF) << 8) | (packet[7] & 0xFF);
        ipPacket.flags = (flagsAndOffset & 0xE000) >> 13;
        ipPacket.fragmentOffset = flagsAndOffset & 0x1FFF;

        // TTL
        ipPacket.ttl = packet[8] & 0xFF;

        // 协议
        ipPacket.protocol = packet[9] & 0xFF;
        ipPacket.protocolName = getProtocolName(ipPacket.protocol);

        // 头部校验和
        ipPacket.headerChecksum = ((packet[10] & 0xFF) << 8) | (packet[11] & 0xFF);

        // 源 IP 地址
        ipPacket.srcIP = bytesToIPv4(packet, 12);

        // 目标 IP 地址
        ipPacket.dstIP = bytesToIPv4(packet, 16);

        // 选项（如果有）
        if (ihl > 5) {
            int optionsLength = (ihl - 5) * 4;
            ipPacket.options = new byte[optionsLength];
            System.arraycopy(packet, 20, ipPacket.options, 0, optionsLength);
        }

        // 数据载荷
        int dataLength = ipPacket.totalLength - ipPacket.headerLength;
        if (dataLength > 0 && ipPacket.headerLength + dataLength <= length) {
            ipPacket.payload = new byte[dataLength];
            System.arraycopy(packet, ipPacket.headerLength, ipPacket.payload, 0, dataLength);
        }

        // 解析传输层信息
        parseTransportInfo(ipPacket);

        return ipPacket;
    }

    /**
     * 解析 IPv6 数据包
     */
    private static IPPacket parseIPv6Packet(byte[] packet, int length) {
        if (length < IPV6_HEADER_LENGTH) {
            System.err.println("IPv6 数据包长度不足");
            return null;
        }

        IPPacket ipPacket = new IPPacket();
        ipPacket.version = 6;
        ipPacket.headerLength = IPV6_HEADER_LENGTH;

        // 流量类别和流标签
        ipPacket.trafficClass = ((packet[0] & 0x0F) << 4) | ((packet[1] & 0xF0) >> 4);
        ipPacket.flowLabel = ((packet[1] & 0x0F) << 16) | ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);

        // 载荷长度
        ipPacket.totalLength = ((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF) + IPV6_HEADER_LENGTH;

        // 下一个头和跳数限制
        ipPacket.protocol = packet[6] & 0xFF;
        ipPacket.protocolName = getProtocolName(ipPacket.protocol);
        ipPacket.ttl = packet[7] & 0xFF;

        // 源 IPv6 地址
        ipPacket.srcIP = bytesToIPv6(packet, 8);

        // 目标 IPv6 地址
        ipPacket.dstIP = bytesToIPv6(packet, 24);

        // 数据载荷
        int dataLength = length - IPV6_HEADER_LENGTH;
        if (dataLength > 0) {
            ipPacket.payload = new byte[dataLength];
            System.arraycopy(packet, IPV6_HEADER_LENGTH, ipPacket.payload, 0, dataLength);
        }

        // 解析传输层信息
        parseTransportInfo(ipPacket);

        return ipPacket;
    }

    /**
     * 解析传输层信息
     */
    private static void parseTransportInfo(IPPacket ipPacket) {
        if (ipPacket.payload == null || ipPacket.payload.length < 4) {
            return;
        }

        byte[] payload = ipPacket.payload;

        switch (ipPacket.protocol) {
            case 6: // TCP
                if (payload.length >= 20) {
                    ipPacket.srcPort = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    ipPacket.dstPort = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
                    ipPacket.transportInfo = String.format("TCP %d->%d",
                            ipPacket.srcPort, ipPacket.dstPort);
                }
                break;
            case 17: // UDP
                if (payload.length >= 8) {
                    ipPacket.srcPort = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    ipPacket.dstPort = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
                    int udpLength = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
                    ipPacket.transportInfo = String.format("UDP %d->%d len=%d",
                            ipPacket.srcPort, ipPacket.dstPort, udpLength);
                }
                break;
            case 1: // ICMP
                if (payload.length >= 4) {
                    int type = payload[0] & 0xFF;
                    int code = payload[1] & 0xFF;
                    ipPacket.transportInfo = String.format("ICMP type=%d code=%d", type, code);
                }
                break;
            case 58: // ICMPv6
                if (payload.length >= 4) {
                    int type = payload[0] & 0xFF;
                    int code = payload[1] & 0xFF;
                    ipPacket.transportInfo = String.format("ICMPv6 type=%d code=%d", type, code);
                }
                break;
            default:
                ipPacket.transportInfo = ipPacket.protocolName;
        }
    }

    /**
     * 字节数组转 IPv4 地址字符串
     */
    private static String bytesToIPv4(byte[] bytes, int offset) {
        return String.format("%d.%d.%d.%d",
                bytes[offset] & 0xFF,
                bytes[offset + 1] & 0xFF,
                bytes[offset + 2] & 0xFF,
                bytes[offset + 3] & 0xFF);
    }

    /**
     * 字节数组转 IPv6 地址字符串
     */
    private static String bytesToIPv6(byte[] bytes, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) sb.append(":");
            sb.append(String.format("%02x%02x",
                    bytes[offset + i] & 0xFF,
                    bytes[offset + i + 1] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 获取协议名称
     */
    private static String getProtocolName(int protocol) {
        return switch (protocol) {
            case 1 -> "ICMP";
            case 2 -> "IGMP";
            case 6 -> "TCP";
            case 17 -> "UDP";
            case 41 -> "IPv6";
            case 47 -> "GRE";
            case 50 -> "ESP";
            case 51 -> "AH";
            case 58 -> "ICMPv6";
            case 89 -> "OSPF";
            case 132 -> "SCTP";
            default -> "Unknown(" + protocol + ")";
        };
    }

    /**
     * IP 数据包信息类
     */
    public static class IPPacket {
        public int version;
        public int headerLength;
        public int totalLength;
        public int dscp;
        public int ecn;
        public int identification;
        public int flags;
        public int fragmentOffset;
        public int ttl;
        public int protocol;
        public String protocolName;
        public int headerChecksum;
        public String srcIP;
        public String dstIP;
        public byte[] options;
        public byte[] payload;
        public int srcPort;
        public int dstPort;
        public String transportInfo;
        public int trafficClass;
        public int flowLabel;

        @Override
        public String toString() {
            return String.format("IP Packet: %s -> %s protocol=%s size=%d", srcIP, dstIP, protocolName, totalLength);
        }
    }

    /**
     * TCP 头结构
     */
    public static class TCPPacket {
        public IPPacket ipPacket;
        public int srcPort;
        public int dstPort;
        public long sequenceNumber;
        public long ackNumber;
        public int flags;
        public int windowSize;
        public int headerLength;
        public byte[] payload;

        // TCP 标志位
        public static final int FIN = 0x01;
        public static final int SYN = 0x02;
        public static final int RST = 0x04;
        public static final int PSH = 0x08;
        public static final int ACK = 0x10;
        public static final int URG = 0x20;

        public boolean hasPayload() {
            return payload != null && payload.length > 0;
        }

        public boolean hasFlag(int flag) {
            return (flags & flag) != 0;
        }

        public String getFlagsString() {
            StringBuilder sb = new StringBuilder();
            if (hasFlag(FIN)) sb.append("FIN ");
            if (hasFlag(SYN)) sb.append("SYN ");
            if (hasFlag(RST)) sb.append("RST ");
            if (hasFlag(PSH)) sb.append("PSH ");
            if (hasFlag(ACK)) sb.append("ACK ");
            if (hasFlag(URG)) sb.append("URG ");
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return String.format("TCP Packet: %s:%d -> %s:%d seq=%d ack=%d flags=%s size=%d", ipPacket.srcIP, srcPort, ipPacket.dstIP, dstPort, sequenceNumber, ackNumber, getFlagsString(), payload == null ? 0 : payload.length);
        }
    }

    /**
     * UDP 头结构
     */
    public static class UDPHeader {
        public int srcPort;
        public int dstPort;
        public int length;
        public int checksum;
        public byte[] payload;

        @Override
        public String toString() {
            return String.format("UDP: %d->%d 长度: %d", srcPort, dstPort, length);
        }
    }
}