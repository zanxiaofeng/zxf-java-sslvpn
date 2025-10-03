package zxf;

import java.io.IOException;
import java.net.Socket;

public class SocketUtils {
    public static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // 忽略关闭异常
        }
    }
}
