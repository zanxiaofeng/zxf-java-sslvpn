package zxf;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;

public class Tests {
    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.debug", "all");

        // Create a Socket and connect through the proxy
        Socket socket = new Socket("localhost", 8008);

        Thread socketThread = new Thread(() -> {
            try {
                DataInputStream clientInput = new DataInputStream(socket.getInputStream());
                DataOutputStream clientOutput = new DataOutputStream(socket.getOutputStream());

                for (int i = 0; i < 2; i++) {
                    byte[] data = LocalDateTime.now().toString().getBytes();

                    clientOutput.writeInt(data.length);
                    clientOutput.write(data);
                    clientOutput.flush();

                    int count = clientInput.readInt();
                    byte[] dataR = new byte[count];
                    clientInput.readFully(dataR);
                    System.out.println("Received: " + new String(dataR));

                    Thread.currentThread().sleep(3000);
                }

            } catch (IOException | InterruptedException e) {
                // 连接关闭时正常结束
            } finally {
                SocketUtils.closeQuietly(socket);
            }
        });

        socketThread.start();
        socketThread.join();
    }
}
