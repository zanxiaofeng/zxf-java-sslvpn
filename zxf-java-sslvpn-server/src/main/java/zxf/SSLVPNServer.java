package zxf;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.concurrent.*;

@Slf4j
public class SSLVPNServer {
    private static final int SERVER_PORT = 8443;
    private final ExecutorService executorService;

    public SSLVPNServer() {
        this.executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("vpn-server-processor-%d").build());
    }

    public void start() throws Exception {
        SSLServerSocketFactory sslServerSocketFactory = SSLSocketFactories.sslServerSocketFactory();
        try (SSLServerSocket serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(SERVER_PORT)) {
            serverSocket.setNeedClientAuth(false); // 不需要客户端证书
            log.info("SSL VPN gateway started on port {}", SERVER_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                log.info("SSL VPN gateway, New client connected:  {}, {}, {}", clientSocket.getLocalSocketAddress(), clientSocket.getRemoteSocketAddress(), clientSocket.getSession());
                // 为每个客户端创建新线程
                executorService.submit(new ClientHandler(clientSocket));
            }
        } catch (Exception ex) {
            log.error("SSL VPN gateway error {}", ex.getMessage(), ex);
        } finally {
            executorService.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        new SSLVPNServer().start();
    }
}