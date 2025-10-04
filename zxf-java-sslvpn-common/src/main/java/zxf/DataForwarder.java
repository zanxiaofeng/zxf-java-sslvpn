package zxf;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
public record DataForwarder(Socket source, Socket target) {

    public Thread start() throws Exception {
        Thread clientToTarget = new Thread(() -> {
            try {
                log.info("{} - Data forward start", forwardInfo());
                InputStream clientInput = source.getInputStream();
                OutputStream targetOutput = target.getOutputStream();
                pipeStreams(clientInput, targetOutput);
            } catch (IOException e) {
                // 连接关闭时正常结束
            } finally {
                SocketUtils.closeQuietly(target);
                log.info("{} - Data forward end", forwardInfo());
            }
        }, threadName());

        clientToTarget.start();
        return clientToTarget;
    }

    private void pipeStreams(InputStream input, OutputStream output) throws IOException {
        int bytesCount = 0;
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            output.flush();
            bytesCount += bytesRead;
            log.info("{} - Data forwarded {} bytes", forwardInfo(), bytesCount);
        }
    }

    private String threadName() throws Exception {
        MessageDigest mdInst = MessageDigest.getInstance("MD5");
        mdInst.update(forwardInfo().getBytes());
        byte[] md = mdInst.digest();
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(md);
    }

    private String forwardInfo() {
        return String.format("%s -> %s -> %s -> %s", source.getRemoteSocketAddress(), source.getLocalSocketAddress(), target.getLocalSocketAddress(), target.getRemoteSocketAddress());
    }
}
