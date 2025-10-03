package zxf;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.concurrent.*;

public class SSLVPNServer {
    private static final int SERVER_PORT = 8443;
    private static String KEY_STORE_PATH = "keystore/keystore-server.jks";
    private static String KEY_STORE_PASS_PHRASE = "changeit";
    private static String TRUST_STORE_PATH = "keystore/truststore-server.jks";
    private static String TRUST_STORE_PASS_PHRASE = "changeit";

    public void start() throws Exception {
        SSLServerSocketFactory sslServerSocketFactory = createSSLServerSocketFactory();

        SSLServerSocket serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(SERVER_PORT);
        serverSocket.setNeedClientAuth(false); // 不需要客户端证书
        System.out.println("SSL VPN Gateway started on port " + SERVER_PORT);

        ExecutorService threadPool = Executors.newCachedThreadPool();
        while (true) {
            try {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                // 为每个客户端创建新线程
                threadPool.execute(new ClientHandler(clientSocket));
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
    }

    private SSLServerSocketFactory createSSLServerSocketFactory() throws Exception {
        // 启用 SSL 调试日志
        System.setProperty("javax.net.debug", "all");

        //Key-Store
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLVPNServer.class.getClassLoader().getResourceAsStream(KEY_STORE_PATH)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(trustStoreInputStream, KEY_STORE_PASS_PHRASE.toCharArray());
            keyManagerFactory.init(keyStore, KEY_STORE_PASS_PHRASE.toCharArray());
        }

        //Trust-Store
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLVPNServer.class.getClassLoader().getResourceAsStream(TRUST_STORE_PATH)) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(trustStoreInputStream, TRUST_STORE_PASS_PHRASE.toCharArray());
            trustManagerFactory.init(trustStore);
        }

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

        return sslContext.getServerSocketFactory();
    }

    public static void main(String[] args) {
        try {
            SSLVPNServer server = new SSLVPNServer();
            server.start();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
}