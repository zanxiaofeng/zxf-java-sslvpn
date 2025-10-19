package zxf.vpn;

import lombok.extern.slf4j.Slf4j;
import zxf.SSLSocketFactories;

import javax.net.ssl.*;
import java.io.*;

@Slf4j
public class SSLVPNClient {
    private static final String VPN_SERVER_HOST = "localhost";
    private static final int VPN_SERVER_PORT = 8443;

    public static SSLVPNConnection connectToVPNServer(String targetHost, int targetPort) throws Exception {
        try {
            SSLSocketFactory sslSocketFactory = SSLSocketFactories.sslSocketFactory();
            SSLSocket vpnSocket = (SSLSocket) sslSocketFactory.createSocket(VPN_SERVER_HOST, VPN_SERVER_PORT);
            vpnSocket.startHandshake();
            log.info("SSL VPN client, Connected to SSL VPN gateway, {}, {}, {}", vpnSocket.getLocalSocketAddress(), vpnSocket.getRemoteSocketAddress(), vpnSocket.getSession());

            requestConnection(vpnSocket, targetHost, targetPort);

            return new SSLVPNConnection(vpnSocket);
        } catch (Exception ex) {
            log.error("SSL VPN client, Exception when connect to SSL VPN Gateway {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    private static void requestConnection(SSLSocket vpnSocket, String targetHost, int targetPort) throws IOException {
        // 通过VPN隧道连接目标
        DataOutputStream vpnOutput = new DataOutputStream(vpnSocket.getOutputStream());
        DataInputStream vpnInput = new DataInputStream(vpnSocket.getInputStream());

        // 发送连接请求到VPN网关
        vpnOutput.writeByte(0x01); // 连接请求
        vpnOutput.writeInt(targetPort);
        vpnOutput.writeInt(targetHost.getBytes().length);
        vpnOutput.write(targetHost.getBytes());
        vpnOutput.flush();

        // 读取VPN网关响应
        int responseType = vpnInput.readByte();
        int success = vpnInput.readInt();

        if (responseType != 0x01 || success != 1) {
            log.error("SSL VPN client, Error response from SSL VPN gateway {}/{}", responseType, success);
            throw new RuntimeException("Error response from SSL VPN gateway");
        }
    }
}