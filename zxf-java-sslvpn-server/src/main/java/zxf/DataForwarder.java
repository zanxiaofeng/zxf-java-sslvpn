package zxf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// 数据转发器
public class DataForwarder implements Runnable {
    private static final int BUFFER_SIZE = 8192;
    private InputStream input;
    private OutputStream output;

    public DataForwarder(InputStream input, OutputStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        try {
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        } catch (IOException e) {
            // 连接关闭是正常的
        }
    }
}
