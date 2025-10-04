package zxf.socks;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

@Slf4j
public class Socks5ProxyServer {
    private static final int LOCAL_SOCKS_PORT = 1080;
    private final ExecutorService executorService;

    public Socks5ProxyServer() {
        this.executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("socks-processor-%d").build());
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(LOCAL_SOCKS_PORT)) {
            log.info("SOCKS proxy started on port {}", LOCAL_SOCKS_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                log.info("SOCKS proxy, New client connected:  {}, {}", clientSocket.getLocalSocketAddress(), clientSocket.getRemoteSocketAddress());
                executorService.submit(new Socks5Handler(clientSocket));
            }
        } catch (Exception ex) {
            log.error("SOCKS proxy error {}", ex.getMessage(), ex);
        } finally {
            executorService.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        new Socks5ProxyServer().start();
    }
}
