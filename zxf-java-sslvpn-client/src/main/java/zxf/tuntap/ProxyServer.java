package zxf.tuntap;

import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, ConnectionHandler> connections;
    private PacketProcessor packetProcessor;

    public ProxyServer() {
        this.executor = Executors.newCachedThreadPool();
        this.connections = new ConcurrentHashMap<>();
    }

    public void setPacketProcessor(PacketProcessor processor) {
        this.packetProcessor = processor;
    }

    public void handleTcpPacket(ByteBuffer packet, InetAddress src, InetAddress dst, int srcPort, int dstPort) {
        String connectionKey = generateConnectionKey(src, srcPort, dst, dstPort);

        ConnectionHandler handler = connections.computeIfAbsent(connectionKey, k -> {
            ConnectionHandler newHandler = new ConnectionHandler(src, srcPort, dst, dstPort);
            executor.submit(newHandler);
            return newHandler;
        });

        handler.handlePacket(packet);
    }

    private String generateConnectionKey(InetAddress src, int srcPort, InetAddress dst, int dstPort) {
        return src.getHostAddress() + ":" + srcPort + "-" + dst.getHostAddress() + ":" + dstPort;
    }

    public void shutdown() {
        executor.shutdown();
        connections.values().forEach(ConnectionHandler::close);
        connections.clear();
    }

    // 连接处理器内部类
    private static class ConnectionHandler implements Runnable {
        private final InetAddress srcAddr;
        private final int srcPort;
        private final InetAddress dstAddr;
        private final int dstPort;
        private volatile boolean running = true;

        public ConnectionHandler(InetAddress src, int srcPort, InetAddress dst, int dstPort) {
            this.srcAddr = src;
            this.srcPort = srcPort;
            this.dstAddr = dst;
            this.dstPort = dstPort;
        }

        @Override
        public void run() {
            System.out.printf("Starting connection handler for %s:%d -> %s:%d ", srcAddr.getHostAddress(), srcPort, dstAddr.getHostAddress(), dstPort);

            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("Connection handler stopped");
        }

        public void handlePacket(ByteBuffer packet) {
            // 处理接收到的数据包
            // 这里可以实现具体的代理逻辑
            System.out.println("Handling packet in connection: " + packet.remaining() + " bytes");
        }

        public void close() {
            running = false;
        }
    }
}
