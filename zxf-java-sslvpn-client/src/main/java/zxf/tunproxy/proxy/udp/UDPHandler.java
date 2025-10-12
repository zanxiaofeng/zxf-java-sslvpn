//package zxf.tunproxy.proxy.udp;
//
//import zxf.tunproxy.packet.PacketParser;
//
//import java.net.*;
//import java.util.Map;
//import java.util.concurrent.*;
//
///**
// * UDP 协议处理器
// */
//public class UDPHandler {
//    private final TunPacketWriter packetWriter;
//    private final Map<String, DatagramSocket> udpSockets;
//    private volatile boolean running;
//
//    public UDPHandler(TunPacketWriter packetWriter) {
//        this.packetWriter = packetWriter;
//        this.udpSockets = new ConcurrentHashMap<>();
//        this.running = true;
//    }
//
//    /**
//     * 处理 UDP 数据包
//     */
//    public void handlePacket(PacketParser.IPPacket ipPacket, byte[] packet) {
//        if (!running) return;
//
//        String sessionKey = getSessionKey(ipPacket);
//
//        try {
//            // 获取或创建 UDP socket
//            DatagramSocket socket = udpSockets.get(sessionKey);
//            if (socket == null) {
//                socket = new DatagramSocket();
//                udpSockets.put(sessionKey, socket);
//            }
//
//            // 提取 UDP 载荷
//            if (ipPacket.payload != null && ipPacket.payload.length >= 8) {
//                byte[] udpPayload = new byte[ipPacket.payload.length - 8];
//                System.arraycopy(ipPacket.payload, 8, udpPayload, 0, udpPayload.length);
//
//                // 发送到真实目标
//                InetAddress targetAddr = InetAddress.getByName(ipPacket.dstIP);
//                DatagramPacket udpPacket = new DatagramPacket(udpPayload, udpPayload.length,
//                        targetAddr, ipPacket.dstPort);
//                socket.send(udpPacket);
//
//                // 更新连接活动
//
//                System.out.printf("UDP 数据转发: %s:%d -> %s:%d 长度: %d ",
//                        ipPacket.srcIP, ipPacket.srcPort,
//                        ipPacket.dstIP, ipPacket.dstPort, udpPayload.length);
//            }
//
//        } catch (Exception e) {
//            System.err.printf("处理 UDP 数据包失败: %s ", e.getMessage());
//        }
//    }
//
//    /**
//     * 生成会话键
//     */
//    private String getSessionKey(PacketParser.IPPacket ipPacket) {
//        return String.format("%s:%d->%s:%d",
//                ipPacket.srcIP, ipPacket.srcPort,
//                ipPacket.dstIP, ipPacket.dstPort);
//    }
//
//    /**
//     * 停止处理器
//     */
//    public void stop() {
//        running = false;
//
//        // 关闭所有 UDP socket
//        for (DatagramSocket socket : udpSockets.values()) {
//            socket.close();
//        }
//        udpSockets.clear();
//
//        System.out.println("UDP 处理器已停止");
//    }
//}
