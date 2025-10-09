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
    public void handlePacket(PacketParser.TCPPacket tcpPacket) {
        String sessionKey = getSessionKey(tcpPacket);

        if (activeSessions.containsKey(sessionKey)) {
            log.debug("会话已存在，提交数据包: {}", sessionKey);
            activeSessions.get(sessionKey).submitPacket(tcpPacket);
            return;
        }

        log.debug("会话不存在，创建新会话: {}", sessionKey);
        TCPProxySession tcpProxySession = new TCPProxySession(tcpPacket, packetWriter);
        tcpProxySession.start();
        activeSessions.put(sessionKey, tcpProxySession);
    }

    /**
     * 生成会话键
     */
    private String getSessionKey(PacketParser.TCPPacket tcpPacket) {
        return String.format("%s:%d->%s:%d", tcpPacket.ipPacket.srcIP,tcpPacket.ipPacket.srcPort, tcpPacket.srcPort, tcpPacket.dstPort);
    }
}