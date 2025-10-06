package zxf.tunproxy;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.proxy.TunPacketProcessor;
import zxf.tunproxy.jna.TunDevice;
import zxf.tunproxy.tun.TunDeviceManager;

@Slf4j
public class ProxyServer {
    private int tunFd;
    private TunPacketProcessor packetProcessor;

    public static void main(String[] args) throws Exception {
        ProxyServer server = new ProxyServer();
        try {
            server.start();
        } finally {
            server.stop();
        }
    }

    public void start() {
        try {
            log.info("=== 基于 TUN 的代理服务器启动 ===");
            tunFd = TunDeviceManager.createTunDeviceAndSetupRoute();
            packetProcessor = new TunPacketProcessor(tunFd);
            packetProcessor.startProcess();
        } catch (Exception ex) {
            log.error("代理服务器启动失败: {}", ex.getMessage(), ex);
        }
    }

    public void stop() throws TunDevice.TunDeviceException {
        if (packetProcessor != null) {
            packetProcessor.stopProcess();
        }

        if (tunFd > 0) {
            TunDeviceManager.cleanupRouteAndCloseTunDevice(tunFd);
        }

        log.info("=== 基于 TUN 的代理服务器停止 ===");
    }
}