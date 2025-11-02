package zxf;

import org.apache.commons.io.IOUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class TestTunProxy {
    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.debug", "all");

        testTCP();

        //testHttp();
    }

    private static void testHttp() throws Exception {
        // Open a connection using the proxy
        URL url = new URL("https://www.sina.com");
        URLConnection connection = url.openConnection();

        // Read data from the connection
        try (InputStream in = connection.getInputStream()) {
            String result = IOUtils.toString(in, StandardCharsets.UTF_8);
            System.out.println(result);
        }
    }


    private static void testTCP() throws Exception {
        // Create a Socket and connect through the proxy
        Socket socket = new Socket("113.137.54.1", 8008);

        Thread socketThread = new Thread(() -> {
            try {
                DataInputStream clientInput = new DataInputStream(socket.getInputStream());
                DataOutputStream clientOutput = new DataOutputStream(socket.getOutputStream());

                byte[] data = LocalDateTime.now().toString().getBytes();

                for (int i = 0; i < 40; i++) {
                    //while (!Thread.currentThread().isInterrupted()) {
                    clientOutput.writeInt(data.length);
                    clientOutput.write(data);
                    clientOutput.flush();

                    int count = clientInput.readInt();
                    byte[] dataR = new byte[count];
                    clientInput.readFully(dataR);
                    System.out.println("Received: " + new String(dataR));

                    Thread.currentThread().sleep(1000);
                    //}
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
