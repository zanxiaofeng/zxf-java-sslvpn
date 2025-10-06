package zxf.tunproxy.jna;

import com.sun.jna.Structure;

/**
 * 使用 JNA 创建和管理 TUN 设备
 */
public class TunDevice {
    // Linux 特定常量
    public static final String TUN_DEVICE = "/dev/net/tun";
    public static final int O_RDWR = 02;
    public static final int IFF_TUN = 0x0001;
    public static final int IFF_TAP = 0x0002;
    public static final int IFF_NO_PI = 0x1000;
    public static final int TUNSETIFF = 0x400454ca;

    // Socket 相关常量
    public static final int AF_INET = 2;
    public static final int SOCK_DGRAM = 2;
    public static final int SIOCSIFADDR = 0x8916;
    public static final int SIOCSIFNETMASK = 0x891C;
    public static final int SIOCSIFFLAGS = 0x8914;
    public static final int IFF_UP = 0x1;
    public static final int IFF_RUNNING = 0x40;

    /**
     * ifreq 结构体定义，用于 ioctl 调用
     */
    @Structure.FieldOrder({"ifr_name", "ifr_flags"})
    public static class IfReqFlags extends Structure {
        public byte[] ifr_name = new byte[16]; // 接口名称
        public short ifr_flags; // 接口标志

        public IfReqFlags() {
            super();
        }

        public IfReqFlags(String name, short flags) {
            super();
            setName(name);
            this.ifr_flags = flags;
        }

        public void setName(String name) {
            byte[] bytes = name.getBytes();
            int length = Math.min(bytes.length, 15);
            System.arraycopy(bytes, 0, ifr_name, 0, length);
            ifr_name[length] = 0; // null 终止
        }

        public String getName() {
            int len = 0;
            while (len < ifr_name.length && ifr_name[len] != 0) {
                len++;
            }
            return new String(ifr_name, 0, len);
        }
    }

    /**
     * ifreq 结构体用于 IP 地址配置
     */
    @Structure.FieldOrder({"ifr_name", "ifr_addr"})
    public static class IfReqAddr extends Structure {
        public byte[] ifr_name = new byte[16];
        public SockAddrIn ifr_addr = new SockAddrIn();

        public IfReqAddr() {
            super();
        }

        public IfReqAddr(String name) {
            super();
            setName(name);
        }

        public void setName(String name) {
            byte[] bytes = name.getBytes();
            int length = Math.min(bytes.length, 15);
            System.arraycopy(bytes, 0, ifr_name, 0, length);
            ifr_name[length] = 0;
        }
    }

    /**
     * IPv4 地址结构体
     */
    @Structure.FieldOrder({"sin_family", "sin_port", "sin_addr", "sin_zero"})
    public static class SockAddrIn extends Structure {
        public short sin_family = AF_INET;
        public short sin_port = 0;
        public int sin_addr;
        public byte[] sin_zero = new byte[8];

        public void setAddress(String ip) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int addr = (Integer.parseInt(parts[0]) & 0xFF) |
                        ((Integer.parseInt(parts[1]) & 0xFF) << 8) |
                        ((Integer.parseInt(parts[2]) & 0xFF) << 16) |
                        ((Integer.parseInt(parts[3]) & 0xFF) << 24);
                this.sin_addr = addr;
            }
        }
    }

    /**
     * 创建 TUN 设备
     */
    public static int createTunDevice(String devName) throws TunDeviceException {
        int tunFd = CLibrary.INSTANCE.open(TUN_DEVICE, O_RDWR);

        if (tunFd < 0) {
            throw new TunDeviceException("无法打开 TUN 设备: " + TUN_DEVICE);
        }

        IfReqFlags ifr = new IfReqFlags(devName, (short) (IFF_TUN | IFF_NO_PI));

        if (CLibrary.INSTANCE.ioctl(tunFd, TUNSETIFF, ifr) < 0) {
            CLibrary.INSTANCE.close(tunFd);
            throw new TunDeviceException("ioctl TUNSETIFF 失败");
        }

        System.out.println("TUN 设备创建成功: " + ifr.getName());
        return tunFd;
    }

    /**
     * 配置 TUN 设备的 IP 地址
     */
    public static void configureTunDevice(String devName, String ip, String netmask) throws TunDeviceException {
        int sockFd = CLibrary.INSTANCE.socket(AF_INET, SOCK_DGRAM, 0);
        if (sockFd < 0) {
            throw new TunDeviceException("创建 socket 失败");
        }

        try {
            // 设置 IP 地址
            IfReqAddr ifrAddr = new IfReqAddr(devName);
            ifrAddr.ifr_addr.setAddress(ip);

            if (CLibrary.INSTANCE.ioctl(sockFd, SIOCSIFADDR, ifrAddr) < 0) {
                throw new TunDeviceException("设置 IP 地址失败");
            }

            // 设置子网掩码
            IfReqAddr ifrNetmask = new IfReqAddr(devName);
            ifrNetmask.ifr_addr.setAddress(netmask);

            if (CLibrary.INSTANCE.ioctl(sockFd, SIOCSIFNETMASK, ifrNetmask) < 0) {
                throw new TunDeviceException("设置子网掩码失败");
            }

            // 启动接口
            IfReqFlags ifrFlags = new IfReqFlags(devName, (short) (IFF_UP | IFF_RUNNING));
            if (CLibrary.INSTANCE.ioctl(sockFd, SIOCSIFFLAGS, ifrFlags) < 0) {
                throw new TunDeviceException("启动接口失败");
            }

            System.out.printf("TUN 设备配置成功: IP=%s, Netmask=%s ", ip, netmask);

        } finally {
            CLibrary.INSTANCE.close(sockFd);
        }
    }

    /**
     * 从 TUN 设备读取数据包
     */
    public static byte[] readPacket(int tunFd, int bufferSize) throws TunDeviceException {
        byte[] buffer = new byte[bufferSize];

        int bytesRead = CLibrary.INSTANCE.read(tunFd, buffer, buffer.length);
        if (bytesRead < 0) {
            throw new TunDeviceException("读取 TUN 设备失败");
        }

        if (bytesRead == 0) {
            return null; // 没有数据
        }

        // 返回实际读取的数据
        byte[] packet = new byte[bytesRead];
        System.arraycopy(buffer, 0, packet, 0, bytesRead);
        return packet;
    }

    /**
     * 向 TUN 设备写入数据包
     */
    public static void writePacket(int tunFd, byte[] packet) throws TunDeviceException {
        int bytesWritten = CLibrary.INSTANCE.write(tunFd, packet, packet.length);
        if (bytesWritten < 0) {
            throw new TunDeviceException("写入 TUN 设备失败");
        }

        if (bytesWritten != packet.length) {
            throw new TunDeviceException("写入数据不完整");
        }
    }

    /**
     * 关闭 TUN 设备
     */
    public static void closeTunDevice(int tunFd) {
        if (tunFd >= 0) {
            CLibrary.INSTANCE.close(tunFd);
        }
    }

    /**
     * TUN 设备异常类
     */
    public static class TunDeviceException extends Exception {
        public TunDeviceException(String message) {
            super(message);
        }

        public TunDeviceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}