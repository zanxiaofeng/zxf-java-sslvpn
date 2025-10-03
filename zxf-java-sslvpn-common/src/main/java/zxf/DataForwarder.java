package zxf;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public record DataForwarder(InputStream input, OutputStream output) implements Runnable {
    private static final int BUFFER_SIZE = 8192;

    @Override
    public void run() {
        log.info("run");

        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        } catch (IOException e) {
            // 连接关闭是正常的
        }
    }
}
