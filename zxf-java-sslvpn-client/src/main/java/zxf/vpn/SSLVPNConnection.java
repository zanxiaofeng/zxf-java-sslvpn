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
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public SSLVPNConnection(SSLSocket sslSocket) throws IOException {
        this.sslSocket = sslSocket;
        this.inputStream = sslSocket.getInputStream();
        this.outputStream = sslSocket.getOutputStream();
    }

    public void write(byte[] buffer) {
        try {
            outputStream.write(buffer, 0, buffer.length);
            outputStream.flush();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public int read(byte[] buffer) throws IOException {
        return inputStream.read(buffer);
    }

    @Override
    public void close() throws Exception {
        SocketUtils.closeQuietly(sslSocket);
    }
}
