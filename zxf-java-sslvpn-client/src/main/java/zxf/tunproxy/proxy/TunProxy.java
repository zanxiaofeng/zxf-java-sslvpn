package zxf.tunproxy.proxy;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.jna.TunDevice;
import zxf.tunproxy.packet.PacketParser;
import zxf.tunproxy.proxy.tcp.TCPHandler;
import zxf.tunproxy.tun.TunDeviceManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class TunProxy implements AutoCloseable {
    private final BlockingQueue<byte[]> packetQueue = new LinkedBlockingQueue<>();
    private final Thread readerThread = new Thread(this::readLoop, "tun-proxy-reader");
    private final Thread writerThread = new Thread(this::writeLoop, "tun-proxy-writer");
    private final int tunFd;
    private final TCPHandler tcpHandler;

    public TunProxy(int tunFd) {
        this.tunFd = tunFd;
        this.tcpHandler = new TCPHandler(this);
        readerThread.start();
        writerThread.start();
    }

    public static TunProxy open() throws Exception {
        int tunFd = TunDeviceManager.createTunDeviceAndSetupRoute();
        log.info("=== 基于 TUN 的代理服务器启动 ===");
        return new TunProxy(tunFd);
    }

    public void join() throws Exception {
        writerThread.join();
        readerThread.join();
    }

    public void close() throws Exception {
        if (tunFd > 0) {
            TunDeviceManager.cleanupRouteAndCloseTunDevice(tunFd);
        }

        writerThread.interrupt();

        readerThread.interrupt();

        log.info("=== 基于 TUN 的代理服务器停止 ===");
    }


    public void submitPacket(byte[] packet) throws Exception {
        packetQueue.put(packet);
    }

    /**
     * 读取循环
     */
    private void readLoop() {
        while (true) {
            try {
                PacketParser.IPPacket ipPacket = readPacket();
                if (ipPacket == null) {
                    log.error("无法解析 IP 数据包");
                    return;
                }

                // 根据协议类型分发处理
                switch (ipPacket.protocol) {
                    case 6: // TCP
                        PacketParser.TCPPacket tcpPacket = PacketParser.parseTCPPacket(ipPacket);
                        tcpHandler.handlePacket(tcpPacket);
                        break;
                    default:
                        //log.info("{}", ipPacket);
                        break;
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
     * 写入循环
     */
    private void writeLoop() {
        while (true) {
            try {
                byte[] packet = packetQueue.take();
                writePacket(packet);
            } catch (InterruptedException ex) {
                break;
            } catch (Exception ex) {
                log.error("TUN 数据包写入器发生错误", ex);
            }
        }
    }

    private PacketParser.IPPacket readPacket() throws Exception {
        byte[] packet = TunDevice.readPacket(tunFd, 65536);
        if (packet == null) {
            log.error("无法读取到IP数据包");
            return null;
        }

        PacketParser.IPPacket ipPacket = PacketParser.parseIPPacket(packet, packet.length);
        if (ipPacket == null) {
            log.error("无法解析IP数据包");
            return null;
        }
        return ipPacket;
    }

    private void writePacket(byte[] packet) throws Exception {
        TunDevice.writePacket(tunFd, packet);
    }
}
