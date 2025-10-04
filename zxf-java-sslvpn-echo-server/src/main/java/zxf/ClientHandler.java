package zxf;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Slf4j
public record ClientHandler(SSLSocket clientSocket) implements Runnable {

    @Override
    public void run() {
        try {
            log.info("TCP echo server - Client handler({}), Processing start", SocketUtils.socketInfo(clientSocket));
            handleClient();
        } catch (Exception ex) {
            log.error("TCP echo server - Client handler({}), Processing error {}", SocketUtils.socketInfo(clientSocket), ex.getMessage(), ex);
        } finally {
            SocketUtils.closeQuietly(clientSocket);
            log.info("TCP echo server - Client handler({}), Processing end", SocketUtils.socketInfo(clientSocket));
        }
    }

    private void handleClient() throws IOException {
        startHandshake();

        DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());

        handleRequest(clientInput, clientOutput);
    }

    private void startHandshake() throws IOException {
        log.info("TCP echo server - Client handler({}), Handshake start", SocketUtils.socketInfo(clientSocket));
        clientSocket.startHandshake();
    }

    private void handleRequest(DataInputStream clientInput, DataOutputStream clientOutput) throws IOException {
        log.info("TCP echo server - Client handler({}), Handle request start", SocketUtils.socketInfo(clientSocket));
        while (!Thread.currentThread().isInterrupted()) {
            int count = clientInput.readInt();
            byte[] data = new byte[count];
            clientInput.readFully(data);
            sendResponse(clientOutput, data);
        }
    }


    private void sendResponse(DataOutputStream clientOutput, byte[] data) throws IOException {
        log.info("TCP echo server - Client handler({}), Send response", SocketUtils.socketInfo(clientSocket));
        clientOutput.writeInt(data.length);
        clientOutput.write(data);
        clientOutput.flush();
    }
}
