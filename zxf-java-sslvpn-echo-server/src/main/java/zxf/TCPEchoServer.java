package zxf;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.concurrent.*;

@Slf4j
public class TCPEchoServer {
    private static final int SERVER_PORT = 8008;
    private final ExecutorService executorService;

    public TCPEchoServer() {
        this.executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("echo-server-processor-%d").build());
    }

    public void start() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            log.info("TCP echo server, Started on port {}", SERVER_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                log.info("TCP echo server, New client connected:  {}", SocketUtils.socketInfo(clientSocket));
                // 为每个客户端创建新线程
                executorService.submit(new ClientHandler(clientSocket));
            }
        } catch (Exception ex) {
            log.error("TCP echo server, Error {}", ex.getMessage(), ex);
        } finally {
            executorService.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        new TCPEchoServer().start();
    }
}