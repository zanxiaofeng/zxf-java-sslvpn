package zxf;

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


    public static void main(String[] args) {
        try {
            SSLVPNServer server = new SSLVPNServer();
            server.start();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
}