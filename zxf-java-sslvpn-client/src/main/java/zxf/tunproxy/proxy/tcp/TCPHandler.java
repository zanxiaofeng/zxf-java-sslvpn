package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.TunPacketWriter;

import java.io.*;
import java.net.*;
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
    public void handlePacket(PacketParser.IPPacket ipPacket, byte[] packet) {
        String sessionKey = getSessionKey(ipPacket);

        // 获取或创建会话
        TCPProxySession session = activeSessions.get(sessionKey);
        if (session == null) {
            session = new TCPProxySession(ipPacket, packetWriter);
            activeSessions.put(sessionKey, session);
        }

        // 处理数据包
        session.handlePacket(ipPacket, packet);
    }

    /**
     * 生成会话键
     */
    private String getSessionKey(PacketParser.IPPacket ipPacket) {
        return String.format("%s:%d->%s:%d", ipPacket.sourceIP, ipPacket.sourcePort, ipPacket.destIP, ipPacket.destPort);
    }
}