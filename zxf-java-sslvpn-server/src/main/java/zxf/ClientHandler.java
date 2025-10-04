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
        try {
            log.info("SSL VPN gateway - Client handler({}), Processing start", SocketUtils.socketInfo(clientSocket));
            handleClient();
        } catch (Exception ex) {
            log.error("SSL VPN gateway - Client handler({}), Processing error {}", SocketUtils.socketInfo(clientSocket), ex.getMessage(), ex);
        } finally {
            SocketUtils.closeQuietly(clientSocket);
            log.info("SSL VPN gateway - Client handler({}), Processing end", SocketUtils.socketInfo(clientSocket));
        }
    }

    private void handleClient() throws IOException {
        startHandshake();

        DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());

        handleRequest(clientInput, clientOutput);
    }

    private void startHandshake() throws IOException {
        log.info("SSL VPN gateway - Client handler({}), Handshake start", SocketUtils.socketInfo(clientSocket));
        clientSocket.startHandshake();
    }

    private void handleRequest(DataInputStream clientInput, DataOutputStream clientOutput) throws IOException {
        log.info("SSL VPN gateway - Client handler({}), Handle request start", SocketUtils.socketInfo(clientSocket));
        // 读取数据包头部
        int packetType = clientInput.readByte();
        if (packetType != 0x01) {
            sendErrorResponse(clientOutput);
            return;
        }

        handleConnectionRequest(clientInput, clientOutput);
    }

    private void handleConnectionRequest(DataInputStream clientInput, DataOutputStream clientOutput) throws IOException {
        log.info("SSL VPN gateway - Client handler({}), Handle connection request start", SocketUtils.socketInfo(clientSocket));
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
            log.info("SSL VPN gateway - Client handler({}), Handle connection request connect to {}:{}", SocketUtils.socketInfo(clientSocket), new String(targetHost), targetPort);
            Socket targetSocket = new Socket(new String(targetHost), targetPort);
            log.info("SSL VPN gateway - Client handler({}), Handle connection request connected to {}", SocketUtils.socketInfo(clientSocket), SocketUtils.socketInfo(targetSocket));

            sendSuccessResponse(clientOutput);
            // 开始数据转发
            SocketUtils.startTunnel(clientSocket, targetSocket);
        } catch (Exception ex) {
            log.error("SSL VPN gateway - Client handler({}), Handle connection request error {}", SocketUtils.socketInfo(clientSocket), ex.getMessage(), ex);
            sendErrorResponse(clientOutput);
        }
    }

    private void sendSuccessResponse(DataOutputStream clientOutput) throws IOException {
        log.info("SSL VPN gateway - Client handler({}), Send success response", SocketUtils.socketInfo(clientSocket));
        // 发送连接成功响应
        clientOutput.writeByte(0x01); // 连接响应
        clientOutput.writeInt(1); // 成功
        clientOutput.flush();
    }

    private void sendErrorResponse(DataOutputStream clientOutput) throws IOException {
        log.info("SSL VPN gateway - Client handler({}), Send error response", SocketUtils.socketInfo(clientSocket));
        // 发送连接失败响应
        clientOutput.writeByte(0x01); // 连接响应
        clientOutput.writeInt(0); // 失败
        clientOutput.flush();
    }
}
