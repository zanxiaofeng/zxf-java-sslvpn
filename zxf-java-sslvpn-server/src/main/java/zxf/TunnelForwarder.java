package zxf;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TunnelForwarder implements Runnable {
    private final SSLSocket clientSocket;
    private final Socket targetSocket;

    public TunnelForwarder(SSLSocket clientSocket, Socket targetSocket) {
        this.clientSocket = clientSocket;
        this.targetSocket = targetSocket;
    }

    @Override
    public void run() {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();
            InputStream targetInput = targetSocket.getInputStream();
            OutputStream targetOutput = targetSocket.getOutputStream();

            // 启动双向数据转发
            Thread clientToTarget = new Thread(new DataForwarder(clientInput, targetOutput));
            Thread targetToClient = new Thread(new DataForwarder(targetInput, clientOutput));

            clientToTarget.start();
            targetToClient.start();

            clientToTarget.join();
            targetToClient.join();

        } catch (Exception e) {
            e.printStackTrace(System.out);
        } finally {
            try {
                targetSocket.close();
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }
    }
}
