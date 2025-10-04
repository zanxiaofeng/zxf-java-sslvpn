package zxf.socks;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

// 支持用户名/密码认证的SOCKS5处理器
public class AuthenticatedSocks5Handler extends Socks5Handler {
    private final String username;
    private final String password;

    public AuthenticatedSocks5Handler(Socket clientSocket, String username, String password) {
        super(clientSocket);
        this.username = username;
        this.password = password;
    }

    @Override
    protected boolean handleAuthentication(DataInputStream input, DataOutputStream output) throws IOException {
        int version = input.readUnsignedByte();
        int methodCount = input.readUnsignedByte();

        if (version != 0x05) {
            return false;
        }

        byte[] methods = new byte[methodCount];
        input.readFully(methods);

        // 检查是否支持用户名/密码认证 (0x02)
        boolean authSupported = false;
        for (byte method : methods) {
            if (method == 0x02) {
                authSupported = true;
                break;
            }
        }

        if (authSupported) {
            output.writeByte(0x05);
            output.writeByte(0x02); // 选择用户名/密码认证
            output.flush();
            return handleUsernamePasswordAuth(input, output);
        } else {
            output.writeByte(0x05);
            output.writeByte(0xFF); // 没有可接受的方法
            return false;
        }
    }

    private boolean handleUsernamePasswordAuth(DataInputStream input, DataOutputStream output)
            throws IOException {
        int subVersion = input.readUnsignedByte();
        if (subVersion != 0x01) {
            return false;
        }

        int usernameLength = input.readUnsignedByte();
        byte[] usernameBytes = new byte[usernameLength];
        input.readFully(usernameBytes);
        String receivedUsername = new String(usernameBytes);

        int passwordLength = input.readUnsignedByte();
        byte[] passwordBytes = new byte[passwordLength];
        input.readFully(passwordBytes);
        String receivedPassword = new String(passwordBytes);

        // 验证凭据
        boolean authenticated = receivedUsername.equals(username) && receivedPassword.equals(password);

        // 发送认证结果
        output.writeByte(0x01); // 认证版本
        output.writeByte(authenticated ? 0x00 : 0x01); // 成功或失败
        output.flush();

        return authenticated;
    }
}
