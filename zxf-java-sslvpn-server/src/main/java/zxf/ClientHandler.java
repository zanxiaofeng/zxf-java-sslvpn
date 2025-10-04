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
            handleClient();
        } catch (Exception ex) {
            log.error("SOCKS proxy error on process {}", ex.getMessage(), ex);
        } finally {
            SocketUtils.closeQuietly(clientSocket);
        }
    }

    private void handleClient() throws IOException {
        startHandshake();

        DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());

        handleRequest(clientInput, clientOutput);
    }

    private void startHandshake() throws IOException {
        // 握手
        clientSocket.startHandshake();
    }

    private void handleRequest(DataInputStream clientInput, DataOutputStream clientOutput) throws IOException {
        // 读取数据包头部
        int packetType = clientInput.readByte();
        if (packetType != 0x01) {
            sendErrorResponse(clientOutput);
            return;
        }

        handleConnectionRequest(clientInput, clientOutput);
    }


    private void handleConnectionRequest(DataInputStream clientInput, DataOutputStream clientOutput) throws IOException {
        try {
            int targetPort = clientInput.readInt();
            int dataLength = clientInput.readInt();
            if (dataLength <= 0) {
                sendErrorResponse(clientOutput);
                return;
            }
            byte[] targetHost = new byte[dataLength];
            clientInput.readFully(targetHost);

            // 连接到目标服务器
            log.info("handleConnectionRequest, {}:{}", new String(targetHost), targetPort);
            Socket targetSocket = new Socket(new String(targetHost), targetPort);
            sendSuccessResponse(clientOutput);
            // 开始数据转发
            SocketUtils.startTunnel(clientSocket, targetSocket);
        } catch (Exception ex) {
            log.error("Exception when handleConnectionRequest: {}", ex.getMessage(), ex);
            sendErrorResponse(clientOutput);
        }
    }

    private void sendSuccessResponse(DataOutputStream clientOutput) throws IOException {
        // 发送连接成功响应
        clientOutput.writeByte(0x01); // 连接响应
        clientOutput.writeInt(1); // 成功
        clientOutput.flush();
    }

    private void sendErrorResponse(DataOutputStream clientOutput) throws IOException {
        // 发送连接失败响应
        clientOutput.writeByte(0x01); // 连接响应
        clientOutput.writeInt(0); // 失败
        clientOutput.flush();
    }
}
