package zxf;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class SSLVPNClient {

    private static final String VPN_SERVER_HOST = "localhost";
    private static final int VPN_SERVER_PORT = 8443;

    public SSLSocket connectToVPNServer() throws Exception {
        SSLSocketFactory sslSocketFactory = SSLSocketFactories.sslSocketFactory();
        SSLSocket vpnSocket = (SSLSocket) sslSocketFactory.createSocket(VPN_SERVER_HOST, VPN_SERVER_PORT);
        vpnSocket.startHandshake();
        System.out.println("Connected to SSL VPN gateway");

        return vpnSocket;
    }
}