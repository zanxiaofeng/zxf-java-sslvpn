package zxf.tunproxy.proxy;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.jna.TunDevice;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.tcp.TCPHandler;

import java.util.concurrent.*;

/**
 * 数据包处理器 - 核心代理逻辑
 */
@Slf4j
public class TunPacketProcessor {
    private final int tunFd;
    private final TunPacketWriter packetWriter;
    private final TCPHandler tcpHandler;

    public TunPacketProcessor(int tunFd) {
        this.tunFd = tunFd;
        this.packetWriter = new TunPacketWriter(tunFd);
        this.tcpHandler = new TCPHandler(packetWriter);
    }

    /**
     * 启动处理器
     */
    public void startProcess() {
        log.info("=== TUN 数据包处理器启动 ===");

        packetWriter.start();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 从 TUN 设备读取数据包
                byte[] packet = TunDevice.readPacket(tunFd, 65536);

                if (packet != null) {
                    // 处理数据包
                    processPacket(packet);
                }

                // 短暂休眠避免 CPU 占用过高
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("读取数据包时发生错误: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * 停止处理器
     */
    public void stopProcess() {
        packetWriter.stop();
        log.info("=== TUN 数据包处理器停止 ===");
    }

    /**
     * 内部数据包处理逻辑
     */
    private void processPacket(byte[] packet) {
        try {
            // 解析 IP 包
            PacketParser.IPPacket ipPacket = PacketParser.parseIPPacket(packet, packet.length);
            if (ipPacket == null) {
                log.error("无法解析 IP 数据包");
                return;
            }

            // 记录日志
            logPacket(ipPacket);

            // 根据协议类型分发处理
            switch (ipPacket.protocol) {
                case 6: // TCP
                    tcpHandler.handlePacket(ipPacket);
                    break;
                default:
                    handleOtherProtocol(ipPacket);
                    break;
            }
        } catch (Exception ex) {
            log.error("处理数据包时发生错误: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 记录数据包日志
     */
    private void logPacket(PacketParser.IPPacket ipPacket) {
        if (ipPacket.sourcePort > 0 && ipPacket.destPort > 0) {
            log.info("数据包({}): {}:{} -> {}:{}, 协议: {}, 长度: {}", ipPacket.version, ipPacket.sourceIP, ipPacket.sourcePort, ipPacket.destIP, ipPacket.destPort,
                    ipPacket.protocolName, ipPacket.totalLength);
            return;
        }
        log.info("数据包({}): {} -> {}, 协议: {}, 长度: {}", ipPacket.version, ipPacket.sourceIP, ipPacket.destIP, ipPacket.protocolName, ipPacket.totalLength);
    }

    /**
     * 处理其他协议
     */
    private void handleOtherProtocol(PacketParser.IPPacket ipPacket) {
        log.info("不支持的协议: {} -> {} 协议: {} ", ipPacket.sourceIP, ipPacket.destIP, ipPacket.protocol);
    }
}