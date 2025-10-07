package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.TunPacketWriter;

import java.util.Map;
import java.util.concurrent.*;

/**
 * TCP 协议处理器
 */
@Slf4j
public class TCPHandler {
    private final TunPacketWriter packetWriter;
    private final Map<String, TCPProxySession> activeSessions;

    public TCPHandler(TunPacketWriter packetWriter) {
        this.packetWriter = packetWriter;
        this.activeSessions = new ConcurrentHashMap<>();
    }

    /**
     * 处理 TCP 数据包
     */
    public void handlePacket(PacketParser.IPPacket ipPacket) {
        String sessionKey = getSessionKey(ipPacket);

        // 获取或创建会话
        TCPProxySession session = activeSessions.computeIfAbsent(sessionKey, k -> {
            TCPProxySession tcpProxySession = new TCPProxySession(ipPacket, packetWriter);
            tcpProxySession.start();
            return tcpProxySession;
        });

        // 处理数据包
        session.handlePacket(PacketParser.parseTCPPacket(ipPacket));
    }

    /**
     * 生成会话键
     */
    private String getSessionKey(PacketParser.IPPacket ipPacket) {
        return String.format("%s:%d->%s:%d", ipPacket.sourceIP, ipPacket.sourcePort, ipPacket.destIP, ipPacket.destPort);
    }
}