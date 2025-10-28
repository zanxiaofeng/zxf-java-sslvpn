package zxf;

public class JavaUtils {
    public static int getUnsignedByte(byte b) {
        return b & 0x0FF;
    }

    public static int getUnsignedShort(short data) {
        return data & 0x0FFFF;
    }

    public static long getUnsignedInt(int data) {
        // data & 0xFFFFFFFF 和 data & 0xFFFFFFFFL 结果是不同的，需要注意，有可能与 JDK 版本有关
        return data & 0xFFFFFFFFL;
    }
}
