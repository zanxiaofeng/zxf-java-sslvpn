import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.concurrent.*;

public class SSLVPNServer {
    private static final int SERVER_PORT = 8443;
    private static final int BUFFER_SIZE = 8192;
    private SSLServerSocket serverSocket;
    private ExecutorService threadPool;

    public SSLVPNServer() {
        this.threadPool = Executors.newCachedThreadPool();
    }

    public void start() throws Exception {
        // 创建SSL上下文
        SSLContext sslContext = createSSLContext();
        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();

        // 创建服务器socket
        serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(SERVER_PORT);
        serverSocket.setNeedClientAuth(false); // 不需要客户端证书

        System.out.println("SSL VPN Gateway started on port " + SERVER_PORT);

        while (true) {
            try {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // 为每个客户端创建新线程
                threadPool.execute(new ClientHandler(clientSocket));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private SSLContext createSSLContext() throws Exception {
        // 在实际应用中应该使用正式的证书
        // 这里使用自签名证书示例
        KeyStore keyStore = KeyStore.getInstance("JKS");
        char[] password = "password".toCharArray();

        // 加载密钥库 (需要提前生成)
        try (InputStream keyStoreStream = new FileInputStream("server.keystore")) {
            keyStore.load(keyStoreStream, password);
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        return sslContext;
    }

    // 客户端处理器
    private class ClientHandler implements Runnable {
        private SSLSocket clientSocket;

        public ClientHandler(SSLSocket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                // 握手
                clientSocket.startHandshake();

                DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());

                while (true) {
                    // 读取数据包头部
                    int packetType = clientInput.readByte();
                    int targetPort = clientInput.readInt();
                    int dataLength = clientInput.readInt();

                    if (dataLength > 0) {
                        byte[] data = new byte[dataLength];
                        clientInput.readFully(data);

                        // 处理数据包
                        if (packetType == 0x01) { // 连接请求
                            handleConnectionRequest(clientOutput, data, targetPort);
                        } else if (packetType == 0x02) { // 数据转发
                            handleDataForward(clientOutput, data, targetPort);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Client disconnected: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleConnectionRequest(DataOutputStream clientOutput,
                                             byte[] targetHost, int targetPort) throws IOException {
            try {
                String host = new String(targetHost);
                System.out.println("Connecting to target: " + host + ":" + targetPort);

                // 连接到目标服务器
                Socket targetSocket = new Socket(host, targetPort);

                // 发送连接成功响应
                clientOutput.writeByte(0x01); // 连接响应
                clientOutput.writeInt(1); // 成功
                clientOutput.flush();

                // 启动数据转发线程
                new Thread(new TunnelForwarder(clientSocket, targetSocket)).start();

            } catch (Exception e) {
                // 发送连接失败响应
                clientOutput.writeByte(0x01); // 连接响应
                clientOutput.writeInt(0); // 失败
                clientOutput.flush();
            }
        }

        private void handleDataForward(DataOutputStream clientOutput,
                                       byte[] data, int targetPort) {
            // 这里处理数据转发逻辑
            // 在实际实现中，需要维护目标连接映射
        }
    }

    // 隧道数据转发器
    private class TunnelForwarder implements Runnable {
        private SSLSocket clientSocket;
        private Socket targetSocket;

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
                e.printStackTrace();
            } finally {
                try {
                    targetSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 数据转发器
    private class DataForwarder implements Runnable {
        private InputStream input;
        private OutputStream output;

        public DataForwarder(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            try {
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    output.flush();
                }
            } catch (IOException e) {
                // 连接关闭是正常的
            }
        }
    }

    public static void main(String[] args) {
        try {
            SSLVPNServer server = new SSLVPNServer();
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}