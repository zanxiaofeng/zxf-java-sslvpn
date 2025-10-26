package zxf.vpn;

import lombok.Getter;
import zxf.SocketUtils;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class SSLVPNConnection implements AutoCloseable {
    @Getter
    private final SSLSocket sslSocket;
    private volatile InputStream inputStream;
    private volatile OutputStream outputStream;

    public SSLVPNConnection(SSLSocket sslSocket) {
        this.sslSocket = sslSocket;
    }

    public void write(byte[] buffer) {
        try {
            if (outputStream == null) {
                outputStream = sslSocket.getOutputStream();
            }
            outputStream.write(buffer, 0, buffer.length);
            outputStream.flush();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public int read(byte[] buffer) throws IOException {
        try {
            if (inputStream == null) {
                inputStream = sslSocket.getInputStream();
            }

            return inputStream.read(buffer);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() throws Exception {
        SocketUtils.closeQuietly(sslSocket);
    }
}
