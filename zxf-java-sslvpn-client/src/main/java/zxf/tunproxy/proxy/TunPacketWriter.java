package zxf.tunproxy.proxy;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.jna.TunDevice;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TUN 设备数据包写入器
 */
@Slf4j
public class TunPacketWriter {
    private final int tunFd;
    private final BlockingQueue<byte[]> packetQueue;
    private final AtomicLong packetsSent;
    private final AtomicLong bytesSent;
    private Thread writerThread;

    public TunPacketWriter(int tunFd) {
        this.tunFd = tunFd;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.packetsSent = new AtomicLong(0);
        this.bytesSent = new AtomicLong(0);
    }

    /**
     * 启动写入器
     */
    public void start() {
        writerThread = new Thread(this::writeLoop, "TunPacketWriter");
        writerThread.start();
        log.info("=== TUN 数据包写入器已启动 === ");
    }

    /**
     * 写入数据包到队列
     */
    public void writePacket(byte[] packet) {
        if (packet != null) {
            try {
                packetQueue.put(packet);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 停止写入器
     */
    public void stop() {
        if (writerThread != null) {
            writerThread.interrupt();
            writerThread = null;
            packetQueue.clear();
        }
        log.info("=== TUN 数据包写入器已停止(发送数据包 {} 个, 总字节数 {}) ===", packetsSent.get(), bytesSent.get());
    }

    /**
     * 写入循环
     */
    private void writeLoop() {
        while (true) {
            try {
                byte[] packet = packetQueue.take();
                TunDevice.writePacket(tunFd, packet);
                packetsSent.incrementAndGet();
                bytesSent.addAndGet(packet.length);
                log.info("已发送数据包: {} 个，总字节数: {}，队列: {} ", packetsSent.get(), bytesSent.get(), packetQueue.size());
            } catch (InterruptedException ex) {
                log.error("TUN 数据包写入器被中断", ex);
                break;
            } catch (Exception ex) {
                log.error("TUN 数据包写入器发生错误", ex);
            }
        }
    }
}