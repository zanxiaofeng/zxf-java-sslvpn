package zxf.socks;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

@Slf4j
public class Socks5ProxyServer {
    private final int port;
    private final ExecutorService executorService;

    public Socks5ProxyServer(int port) {
        this.port = port;
        this.executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("socks-processor-%d").build());
    }

    public void start(SSLSocket vpnSocket) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("SOCKS proxy started on port {}", port);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                log.info("SOCKS proxy, New client connected:  {}, {}", clientSocket.getLocalSocketAddress(), clientSocket.getRemoteSocketAddress());
                executorService.submit(new Socks5Handler(clientSocket, vpnSocket));
            }
        } catch (IOException ex) {
            log.error("SOCKS proxy error {}", ex.getMessage(), ex);
        } finally {
            executorService.shutdown();
        }
    }
}
