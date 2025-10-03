package zxf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public record DataForwarder(Socket source, Socket target) {

    public Thread start() {
        Thread clientToTarget = new Thread(() -> {
            try {
                InputStream clientInput = source.getInputStream();
                OutputStream targetOutput = target.getOutputStream();
                pipeStreams(clientInput, targetOutput);
            } catch (IOException e) {
                // 连接关闭时正常结束
            } finally {
                SocketUtils.closeQuietly(target);
            }
        }, source.getRemoteSocketAddress().toString() + "-" + target.getRemoteSocketAddress());

        clientToTarget.start();
        return clientToTarget;
    }

    private void pipeStreams(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            output.flush();
        }
    }
}
