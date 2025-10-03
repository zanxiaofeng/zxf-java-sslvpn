package zxf;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

@Slf4j
public record ClientHandler(SSLSocket clientSocket) implements Runnable {

    @Override
    public void run() {
        log.info("run");
        try {
            // 握手
            clientSocket.startHandshake();

            try (DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream())) {
                while (true) {
                    // 读取数据包头部
                    int packetType = clientInput.readByte();
                    int targetPort = clientInput.readInt();
                    int dataLength = clientInput.readInt();

                    if (dataLength > 0) {
                        byte[] data = new byte[dataLength];
                        clientInput.readFully(data);

                        // 处理数据包
                        if (packetType == 0x01) {
                            // 连接请求
                            handleConnectionRequest(clientOutput, data, targetPort);
                        } else if (packetType == 0x02) {
                            // 数据转发
                            handleDataForward(clientOutput, data, targetPort);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Exception when process client socket: {}", ex.getMessage(), ex);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ex) {
                log.error("Exception when close client socket: {}", ex.getMessage(), ex);
            }
        }
    }

    private void handleConnectionRequest(DataOutputStream clientOutput, byte[] targetHost, int targetPort) throws IOException {
        log.info("handleConnectionRequest, {}:{}", new String(targetHost), targetPort);
        try {
            // 连接到目标服务器
            Socket targetSocket = new Socket(new String(targetHost), targetPort);

            // 发送连接成功响应
            clientOutput.writeByte(0x01); // 连接响应
            clientOutput.writeInt(1); // 成功
            clientOutput.flush();

            // 启动数据转发线程
            new Thread(new TunnelForwarder(clientSocket, targetSocket)).start();
        } catch (Exception ex) {
            log.error("Exception when handleConnectionRequest: {}", ex.getMessage(), ex);
            // 发送连接失败响应
            clientOutput.writeByte(0x01); // 连接响应
            clientOutput.writeInt(0); // 失败
            clientOutput.flush();
        }
    }

    private void handleDataForward(DataOutputStream clientOutput, byte[] data, int targetPort) {
        log.info("handleDataForward, {}, {}", data, targetPort);
        // 这里处理数据转发逻辑
        // 在实际实现中，需要维护目标连接映射
    }
}
