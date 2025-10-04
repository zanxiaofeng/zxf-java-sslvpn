package zxf;

import lombok.extern.slf4j.Slf4j;
import zxf.socks.Socks5ProxyServer;

@Slf4j
public class LocalSocksProxyStarter {
    private static final int LOCAL_SOCKS_PORT = 1080;

    public static void main(String[] args) throws Exception {
        new Socks5ProxyServer(LOCAL_SOCKS_PORT).start();
    }
}