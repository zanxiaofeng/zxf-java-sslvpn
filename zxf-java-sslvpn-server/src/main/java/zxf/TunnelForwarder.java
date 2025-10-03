package zxf;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Slf4j
public record TunnelForwarder(SSLSocket clientSocket, Socket targetSocket) implements Runnable {

    @Override
    public void run() {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();
            InputStream targetInput = targetSocket.getInputStream();
            OutputStream targetOutput = targetSocket.getOutputStream();

            // 启动双向数据转发
            Thread clientToTarget = new Thread(new DataForwarder(clientInput, targetOutput), clientSocket.getRemoteSocketAddress().toString() + "-" + targetSocket.getRemoteSocketAddress());
            Thread targetToClient = new Thread(new DataForwarder(targetInput, clientOutput), targetSocket.getRemoteSocketAddress().toString() + "-" + clientSocket.getRemoteSocketAddress());

            clientToTarget.start();
            targetToClient.start();

            clientToTarget.join();
            targetToClient.join();

        } catch (Exception ex) {
            log.info("Exception when start data forward {}", ex.getMessage(), ex);
        } finally {
            try {
                targetSocket.close();
            } catch (IOException ex) {
                log.info("Exception when close target socket {}", ex.getMessage(), ex);
            }
        }
    }
}
