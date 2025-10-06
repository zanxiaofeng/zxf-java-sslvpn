package zxf.tunproxy.jna;

import com.sun.jna.*;

public interface CLibrary extends Library {
    CLibrary INSTANCE = (CLibrary) Native.load(Platform.C_LIBRARY_NAME, CLibrary.class);

    // 文件操作
    int open(String pathname, int flags);

    int close(int fd);

    int read(int fd, byte[] buf, int count);

    int write(int fd, byte[] buf, int count);

    // IO 控制
    int ioctl(int fd, int request, Object... args);

    // 网络接口配置
    int socket(int domain, int type, int protocol);

    int setsockopt(int sockfd, int level, int optname, Pointer optval, int optlen);
}