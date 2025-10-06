package zxf.tunproxy.tun;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.jna.TunDevice;

@Slf4j
public class TunDeviceManager {
    private static final String TUN_DEVICE_NAME = "tun-proxy";
    private static final String TUN_IP_ADDRESS = "10.8.0.1";
    private static final String TUN_NETMASK = "255.255.255.0";

    public static int createTunDeviceAndSetupRoute() throws TunDevice.TunDeviceException {
        int tunFd = TunDevice.createTunDevice(TUN_DEVICE_NAME);
        TunDevice.configureTunDevice(TUN_DEVICE_NAME, TUN_IP_ADDRESS, TUN_NETMASK);
        setupRouting();
        return tunFd;
    }

    public static void cleanupRouteAndCloseTunDevice(int tunFd) throws TunDevice.TunDeviceException {
        cleanupRouting();
        TunDevice.closeTunDevice(tunFd);
    }

    /**
     * 设置路由规则
     */
    private static void setupRouting() {
        try {
            // 添加路由规则，将特定流量路由到 TUN 设备
            String[] commands = {
                    // 将 10.8.0.0/24 网段的流量路由到 TUN 设备
                    "sudo ip route add 10.8.0.0/24 dev " + TUN_DEVICE_NAME,
                    "sudo ip route add 198.18.0.0/24 dev " + TUN_DEVICE_NAME,
                    // 启用 IP 转发
                    "sudo sysctl -w net.ipv4.ip_forward=1",
                    // 设置 NAT 规则（如果需要）
                    // "sudo iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE"
            };

            for (String cmd : commands) {
                Process process = Runtime.getRuntime().exec(cmd.split(" "));
                process.waitFor();
            }

            log.info("路由规则设置完成");
        } catch (Exception ex) {
            log.error("设置路由失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 清理路由规则
     */
    private static void cleanupRouting() {
        try {
            String[] commands = {
                    "sudo ip route del 10.8.0.0/24 dev " + TUN_DEVICE_NAME,
                    "sudo ip route del 198.18.0.0/24 dev " + TUN_DEVICE_NAME,
                    // "sudo iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE"
            };

            for (String cmd : commands) {
                Process process = Runtime.getRuntime().exec(cmd.split(" "));
                process.waitFor();
            }

            log.info("路由规则已清理");
        } catch (Exception ex) {
            log.error("清理路由失败: {}", ex.getMessage(), ex);
        }
    }
}
