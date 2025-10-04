package zxf;

import java.io.IOException;
import java.net.Socket;

public class SocketUtils {

    public static void startTunnel(Socket client, Socket target) {
        // 客户端到目标服务器的数据转发
        Thread clientToTarget = new DataForwarder(client, target).start();
        // 目标服务器到客户端的数据转发
        Thread targetToClient = new DataForwarder(target, client).start();

        try {
            clientToTarget.join();
            targetToClient.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // 忽略关闭异常
        }
    }
}
