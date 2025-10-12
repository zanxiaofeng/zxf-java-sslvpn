package zxf.tunproxy;

import lombok.extern.slf4j.Slf4j;
import zxf.tunproxy.proxy.TunProxy;

@Slf4j
public class ProxyServer {

    public static void main(String[] args) throws Exception {
        try (TunProxy tunProxy = TunProxy.open()) {
            tunProxy.join();
        } catch (Exception ex) {
            log.error("代理服务器启动失败: {}", ex.getMessage(), ex);
        }
    }
}