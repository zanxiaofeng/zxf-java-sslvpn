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

    public void start() throws Exception {
        SSLServerSocketFactory sslServerSocketFactory = SSLSocketFactories.sslServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(SERVER_PORT);
        serverSocket.setNeedClientAuth(false); // 不需要客户端证书
        log.info("SSL VPN Gateway started on port {}", SERVER_PORT);

        ExecutorService threadPool = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("vpn-server-processor-%d").build());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                log.info("New client connected:  {}, {}, {}", clientSocket.getLocalSocketAddress(), clientSocket.getRemoteSocketAddress(), clientSocket.getSession());
                // 为每个客户端创建新线程
                threadPool.execute(new ClientHandler(clientSocket));
            } catch (Exception ex) {
                log.info("Exception when wart for client connection {}", ex.getMessage(), ex);
            }
        }
    }


    public static void main(String[] args) {
        try {
            SSLVPNServer server = new SSLVPNServer();
            server.start();
        } catch (Exception ex) {
            log.error("Exception when start VPN Gateway {}", ex.getMessage(), ex);
        }
    }
}