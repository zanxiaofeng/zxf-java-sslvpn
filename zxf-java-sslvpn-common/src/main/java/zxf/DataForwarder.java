package zxf;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Slf4j
public record DataForwarder(Socket source, Socket target) {

    public Thread start() {
        String info = forwardInfo();
        Thread clientToTarget = new Thread(() -> {
            try {
                log.info("{} - Data forward start", info);
                InputStream clientInput = source.getInputStream();
                OutputStream targetOutput = target.getOutputStream();
                pipeStreams(clientInput, targetOutput, info);
            } catch (IOException e) {
                if (!source.isClosed() && !target.isClosed()) {
                    log.warn("{} - Unexpected forwarding error: {}", info, e.getMessage());
                }
            } finally {
                SocketUtils.closeQuietly(target);
                log.info("{} - Data forward end", info);
            }
        }, "fwd-" + source.getRemoteSocketAddress() + "->" + target.getRemoteSocketAddress());

        clientToTarget.start();
        return clientToTarget;
    }

    private void pipeStreams(InputStream input, OutputStream output, String info) throws IOException {
        long bytesCount = 0;
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            output.flush();
            bytesCount += bytesRead;
            log.debug("{} - Data forwarded {} bytes", info, bytesCount);
        }
    }

    private String forwardInfo() {
        return String.format("%s -> %s -> %s -> %s", source.getRemoteSocketAddress(), source.getLocalSocketAddress(), target.getLocalSocketAddress(), target.getRemoteSocketAddress());
    }
}
