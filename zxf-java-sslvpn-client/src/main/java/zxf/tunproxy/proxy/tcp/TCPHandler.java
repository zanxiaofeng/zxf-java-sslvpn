package zxf.tunproxy.proxy.tcp;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.TunProxy;

import java.util.Map;
import java.util.concurrent.*;

/**
 * TCP 协议处理器
 */
@Slf4j
public class TCPHandler {
    private final Map<String, TCPSession> activeSessions = new ConcurrentHashMap<>();
    private final TunProxy tunProxy;

    public TCPHandler(TunProxy tunProxy) {
        this.tunProxy = tunProxy;
    }

    /**
     * 处理 TCP 数据包
     */
    public void handlePacket(PacketParser.TCPPacket tcpPacket) {
        String sessionKey = getSessionKey(tcpPacket);

        TCPSession existing = activeSessions.get(sessionKey);
        if (existing != null) {
            //log.debug("会话已存在，提交数据包: {}", sessionKey);
            existing.submitPacket(tcpPacket);
            return;
        }

        //log.debug("会话不存在，创建新会话: {}", sessionKey);
        TCPSession tcpSession = new TCPSession(tcpPacket, tunProxy);
        tcpSession.start(() -> {
            activeSessions.remove(sessionKey);
        });
        activeSessions.put(sessionKey, tcpSession);
    }

    /**
     * 生成会话键
     */
    private String getSessionKey(PacketParser.TCPPacket tcpPacket) {
        return String.format("%s:%d->%s:%d", tcpPacket.ipPacket.srcIP, tcpPacket.srcPort, tcpPacket.ipPacket.dstIP, tcpPacket.dstPort);
    }
}