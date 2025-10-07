package zxf.tunproxy;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.proxy.TunPacketProcessor;
import zxf.tunproxy.tun.TunDeviceManager;

@Slf4j
public class ProxyServer {

    public static void main(String[] args) throws Exception {
        new ProxyServer().start();
    }

    public void start() throws Exception {
        int tunFd = 0;
        TunPacketProcessor packetProcessor = null;
        try {
            log.info("=== 基于 TUN 的代理服务器启动 ===");
            tunFd = TunDeviceManager.createTunDeviceAndSetupRoute();
            packetProcessor = new TunPacketProcessor(tunFd);
            packetProcessor.startProcess();
        } catch (Exception ex) {
            log.error("代理服务器启动失败: {}", ex.getMessage(), ex);
        } finally {
            if (packetProcessor != null) {
                packetProcessor.stopProcess();
            }

            if (tunFd > 0) {
                TunDeviceManager.cleanupRouteAndCloseTunDevice(tunFd);
            }

            log.info("=== 基于 TUN 的代理服务器停止 ===");
        }
    }
}