package zxf.tuntap;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransparentProxy {
    private TunDevice tunDevice;
    private PacketProcessor packetProcessor;
    private ProxyServer proxyServer;
    private AtomicBoolean running;
    private Thread readThread;

    private static final int BUFFER_SIZE = 4096;
    private static final String TUN_DEVICE_NAME = "tun0";

    public TransparentProxy() {
        this.running = new AtomicBoolean(false);
        this.proxyServer = new ProxyServer();
        this.packetProcessor = new PacketProcessor(proxyServer);
        this.proxyServer.setPacketProcessor(packetProcessor);
    }

    public void start() throws IOException {
        // 创建TUN设备
        tunDevice = new TunDevice(TUN_DEVICE_NAME);
        tunDevice.createTunDevice();

        System.out.println("TUN device created: " + TUN_DEVICE_NAME);

        // 配置TUN设备
        configureTunDevice();

        running.set(true);

        // 启动读取线程
        readThread = new Thread(this::readLoop);
        readThread.start();

        System.out.println("Transparent proxy started");
    }

    private void configureTunDevice() throws IOException {
        // 配置TUN设备的IP地址和路由
        // 这里使用Runtime执行系统命令
        String ipAddress = "10.0.0.1";
        String netmask = "255.255.255.0";

        try {
            // 设置IP地址
            ProcessBuilder pb1 = new ProcessBuilder("ip", "addr", "add",
                    ipAddress + "/24", "dev", TUN_DEVICE_NAME);
            Process p1 = pb1.start();
            p1.waitFor();

            // 启用设备
            ProcessBuilder pb2 = new ProcessBuilder("ip", "link", "set",
                    TUN_DEVICE_NAME, "up");
            Process p2 = pb2.start();
            p2.waitFor();

            System.out.println("TUN device configured with IP: " + ipAddress);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Failed to configure TUN device", e);
        }
    }

    private void readLoop() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        while (running.get()) {
            try {
                buffer.clear();
                int bytesRead = tunDevice.read(buffer);

                if (bytesRead > 0) {
                    buffer.flip();
                    processIncomingPacket(buffer);
                }

            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error reading from TUN device: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void processIncomingPacket(ByteBuffer packet) {
        // 处理接收到的数据包
        packetProcessor.processPacket(packet);
    }

    public void sendPacket(ByteBuffer packet) throws IOException {
        if (tunDevice != null && running.get()) {
            tunDevice.write(packet);
        }
    }

    public void stop() {
        running.set(false);

        if (readThread != null) {
            readThread.interrupt();
            try {
                readThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        proxyServer.shutdown();

        if (tunDevice != null) {
            try {
                tunDevice.close();
            } catch (IOException e) {
                System.err.println("Error closing TUN device: " + e.getMessage());
            }
        }

        System.out.println("Transparent proxy stopped");
    }
}
