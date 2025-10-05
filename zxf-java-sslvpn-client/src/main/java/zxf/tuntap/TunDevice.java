package zxf.tuntap;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class TunDevice implements AutoCloseable {
    private final String devName;
    private RandomAccessFile tunFile;
    private FileChannel channel;

    public TunDevice(String name) {
        this.devName = name;
    }

    public void createTunDevice() throws IOException {
        // 在Linux下创建TUN设备
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            createLinuxTun();
        } else {
            throw new UnsupportedOperationException("Unsupported OS");
        }
    }

    private void createLinuxTun() throws IOException {
        // 打开TUN设备文件
        File devNetTun = new File("/dev/net/tun");
        if (!devNetTun.exists()) {
            throw new IOException("TUN device not available");
        }

        tunFile = new RandomAccessFile("/dev/net/tun", "rw");
        channel = tunFile.getChannel();

        // 设置TUN设备参数
        setTunFlags();
    }

    private native void setTunFlags();

    public int read(ByteBuffer buffer) throws IOException {
        return channel.read(buffer);
    }

    public int write(ByteBuffer buffer) throws IOException {
        return channel.write(buffer);
    }

    public String getDeviceName() {
        return devName;
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
        if (tunFile != null) {
            tunFile.close();
        }
    }
}