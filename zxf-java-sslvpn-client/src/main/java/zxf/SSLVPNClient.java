package zxf;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

@Slf4j
public class SSLVPNClient {
    private static final String VPN_SERVER_HOST = "localhost";
    private static final int VPN_SERVER_PORT = 8443;

    public SSLSocket connectToVPNServer() throws Exception {
        try {
            SSLSocketFactory sslSocketFactory = SSLSocketFactories.sslSocketFactory();
            SSLSocket vpnSocket = (SSLSocket) sslSocketFactory.createSocket(VPN_SERVER_HOST, VPN_SERVER_PORT);
            vpnSocket.startHandshake();
            log.info("Connected to SSL VPN gateway, {}, {}, {}", vpnSocket.getLocalSocketAddress(), vpnSocket.getRemoteSocketAddress(), vpnSocket.getSession());

            return vpnSocket;
        } catch (Exception ex) {
            log.error("Exception when connect to VPN Gateway {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}