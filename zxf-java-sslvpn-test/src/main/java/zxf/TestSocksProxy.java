package zxf;

import java.io.*;
import java.net.*;

public class TestSocksProxy {
    public static void main(String[] args) throws Exception {
        testTCPWithSocksProxy();
    }

    private static void testHttpWithSocksProxy() throws Exception {
        // Set up the authenticator
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("username", "password".toCharArray());
            }
        });

        // Create a SOCKS proxy object
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1080));

        // Open a connection using the proxy
        URL url = new URL("https://www.163.com");
        URLConnection connection = url.openConnection(proxy);

        // Read data from the connection
        try (InputStream in = connection.getInputStream()) {
            // Process the input stream
        }
    }


    private static void testTCPWithSocksProxy() throws Exception {
        // Create a SOCKS5 proxy instance
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1080));

        // Create a Socket and connect through the proxy
        Socket socket = new Socket(proxy);
        socket.connect(new InetSocketAddress("localhost", 8008));

        Thread socketThread = new Thread(() -> {
            try {
                DataInputStream clientInput = new DataInputStream(socket.getInputStream());
                DataOutputStream clientOutput = new DataOutputStream(socket.getOutputStream());

                byte[] data = "Hello".getBytes();

                while (!Thread.currentThread().isInterrupted()) {
                    clientOutput.writeInt(data.length);
                    clientOutput.write(data);
                    clientOutput.flush();

                    int count = clientInput.readInt();
                    byte[] dataR = new byte[count];
                    clientInput.readFully(dataR);
                }
            } catch (IOException e) {
                // 连接关闭时正常结束
            } finally {
                SocketUtils.closeQuietly(socket);
            }
        });

        socketThread.start();
        socketThread.join();
    }

}
