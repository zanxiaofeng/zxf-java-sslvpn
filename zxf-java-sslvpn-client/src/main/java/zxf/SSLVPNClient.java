package zxf;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.*;

public class SSLVPNClient {
    private static final String VPN_SERVER_HOST = "localhost";
    private static String KEY_STORE_PATH = "keystore/keystore-client.jks";
    private static String KEY_STORE_PASS_PHRASE = "changeit";
    private static String TRUST_STORE_PATH = "keystore/truststore-client.jks";
    private static String TRUST_STORE_PASS_PHRASE = "changeit";
    private static final int VPN_SERVER_PORT = 8443;
    private static final int LOCAL_SOCKS_PORT = 1080;

    private SSLSocket vpnSocket;
    private ServerSocket localServerSocket;

    public void connect() throws Exception {
        // 创建SSL上下文
        SSLContext sslContext = createSSLContext();
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        // 连接到VPN网关
        vpnSocket = (SSLSocket) sslSocketFactory.createSocket(VPN_SERVER_HOST, VPN_SERVER_PORT);
        vpnSocket.startHandshake();

        System.out.println("Connected to SSL VPN gateway");

        // 启动本地SOCKS代理服务器
        startLocalSocksProxy();
    }

    private SSLContext createSSLContext() throws Exception {
        // 启用 SSL 调试日志
        System.setProperty("javax.net.debug", "all");

        //Key-Store
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLVPNClient.class.getClassLoader().getResourceAsStream(KEY_STORE_PATH)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(trustStoreInputStream, KEY_STORE_PASS_PHRASE.toCharArray());
            keyManagerFactory.init(keyStore, KEY_STORE_PASS_PHRASE.toCharArray());
        }

        //Trust-Store
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLVPNClient.class.getClassLoader().getResourceAsStream(TRUST_STORE_PATH)) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(trustStoreInputStream, TRUST_STORE_PASS_PHRASE.toCharArray());
            trustManagerFactory.init(trustStore);
        }

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    private void startLocalSocksProxy() throws IOException {
        localServerSocket = new ServerSocket(LOCAL_SOCKS_PORT);
        System.out.println("SOCKS proxy started on port " + LOCAL_SOCKS_PORT);

        ExecutorService threadPool = Executors.newCachedThreadPool();

        while (true) {
            try {
                Socket clientSocket = localServerSocket.accept();
                threadPool.execute(new SocksHandler(clientSocket));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // SOCKS协议处理器
    private class SocksHandler implements Runnable {
        private Socket clientSocket;

        public SocksHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());

                // SOCKS5握手
                int version = clientInput.readByte();
                int nmethods = clientInput.readByte();
                clientInput.skipBytes(nmethods);

                // 响应握手
                clientOutput.writeByte(0x05); // SOCKS5
                clientOutput.writeByte(0x00); // 无需认证
                clientOutput.flush();

                // 读取连接请求
                version = clientInput.readByte();
                int cmd = clientInput.readByte();
                int rsv = clientInput.readByte();
                int atype = clientInput.readByte();

                String targetHost;
                int targetPort;

                if (atype == 0x01) { // IPv4地址
                    byte[] ipBytes = new byte[4];
                    clientInput.readFully(ipBytes);
                    targetHost = (ipBytes[0] & 0xFF) + "." + (ipBytes[1] & 0xFF) + "." +
                            (ipBytes[2] & 0xFF) + "." + (ipBytes[3] & 0xFF);
                    targetPort = clientInput.readShort() & 0xFFFF;
                } else if (atype == 0x03) { // 域名
                    int domainLength = clientInput.readByte() & 0xFF;
                    byte[] domainBytes = new byte[domainLength];
                    clientInput.readFully(domainBytes);
                    targetHost = new String(domainBytes);
                    targetPort = clientInput.readShort() & 0xFFFF;
                } else {
                    throw new IOException("Unsupported address type");
                }

                System.out.println("SOCKS request: " + targetHost + ":" + targetPort);

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

                if (success == 1) {
                    // 发送SOCKS成功响应
                    clientOutput.writeByte(0x05); // SOCKS5
                    clientOutput.writeByte(0x00); // 成功
                    clientOutput.writeByte(0x00); // RSV
                    clientOutput.writeByte(0x01); // IPv4
                    clientOutput.write(new byte[]{0, 0, 0, 0}); // 绑定地址
                    clientOutput.writeShort(0); // 绑定端口
                    clientOutput.flush();

                    // 启动数据转发
                    startTunneling(clientSocket, vpnSocket);
                } else {
                    clientOutput.writeByte(0x05);
                    clientOutput.writeByte(0x01); // 常规SOCKS服务器故障
                    clientOutput.flush();
                    clientSocket.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        private void startTunneling(Socket clientSocket, SSLSocket vpnSocket) {
            try {
                InputStream clientInput = clientSocket.getInputStream();
                OutputStream clientOutput = clientSocket.getOutputStream();
                InputStream vpnInput = vpnSocket.getInputStream();
                OutputStream vpnOutput = vpnSocket.getOutputStream();

                // 启动双向数据转发
                Thread clientToVpn = new Thread(new DataForwarder(clientInput, vpnOutput));
                Thread vpnToClient = new Thread(new DataForwarder(vpnInput, clientOutput));

                clientToVpn.start();
                vpnToClient.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 数据转发器（与服务器端相同）
    private class DataForwarder implements Runnable {
        private InputStream input;
        private OutputStream output;

        public DataForwarder(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            int bytesRead;

            try {
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    output.flush();
                }
            } catch (IOException e) {
                // 连接关闭
            }
        }
    }

    public static void main(String[] args) {
        try {
            SSLVPNClient client = new SSLVPNClient();
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}