package zxf.socks;

import lombok.extern.slf4j.Slf4j;
import zxf.SSLVPNClient;
import zxf.SocketUtils;

import java.io.*;
import java.net.*;

@Slf4j
public class Socks5Handler implements Runnable {
    private final Socket clientSocket;

    public Socks5Handler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            log.info("SOCKS proxy - Client handler({}), Processing start", SocketUtils.socketInfo(clientSocket));
            handleClient();
        } catch (Exception ex) {
            log.error("SOCKS proxy - Client handler({}), Processing error {}", SocketUtils.socketInfo(clientSocket), ex.getMessage(), ex);
        } finally {
            SocketUtils.closeQuietly(clientSocket);
            log.info("SOCKS proxy - Client handler({}), Processing end", SocketUtils.socketInfo(clientSocket));
        }
    }

    private void handleClient() throws Exception {
        DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());

        // 第一步：认证协商
        if (!handleAuthentication(clientInput, clientOutput)) {
            return;
        }

        // 第二步：处理客户端请求
        handleRequest(clientInput, clientOutput);
    }

    protected boolean handleAuthentication(DataInputStream input, DataOutputStream output) throws IOException {
        log.info("SOCKS proxy - Client handler({}), Handle authentication start", SocketUtils.socketInfo(clientSocket));

        // 读取版本和方法数量
        int version = input.readUnsignedByte();
        int methodCount = input.readUnsignedByte();

        if (version != 0x05) {
            log.error("SOCKS proxy - Client handler({}), Handle authentication error, unsupported SOCKS version {}", SocketUtils.socketInfo(clientSocket), version);
            return false;
        }

        // 读取所有支持的方法
        byte[] methods = new byte[methodCount];
        input.readFully(methods);

        // 检查是否支持无认证 (0x00)
        boolean noAuthSupported = false;
        for (byte method : methods) {
            if (method == 0x00) {
                noAuthSupported = true;
                break;
            }
        }

        // 响应选择的认证方法
        if (noAuthSupported) {
            output.writeByte(0x05); // SOCKS版本
            output.writeByte(0x00); // 选择无认证
        } else {
            output.writeByte(0x05); // SOCKS版本
            output.writeByte(0xFF); // 没有可接受的方法
            return false;
        }

        output.flush();
        return true;
    }

    private void handleRequest(DataInputStream input, DataOutputStream output) throws Exception {
        log.info("SOCKS proxy - Client handler({}), Handle request start", SocketUtils.socketInfo(clientSocket));
        // 读取请求头
        int version = input.readUnsignedByte();
        int command = input.readUnsignedByte();
        int reserved = input.readUnsignedByte();
        int addressType = input.readUnsignedByte();

        if (version != 0x05) {
            log.error("SOCKS proxy - Client handler({}), Handle request error, unsupported SOCKS version {}", SocketUtils.socketInfo(clientSocket), version);
            sendErrorResponse(output, 0x01); // 常规SOCKS服务器故障
            return;
        }

        // 只支持CONNECT命令 (0x01)
        if (command != 0x01) {
            log.error("SOCKS proxy - Client handler({}), Handle request error, unsupported SOCKS command {}", SocketUtils.socketInfo(clientSocket), command);
            sendErrorResponse(output, 0x07); // 不支持的命令
            return;
        }

        String targetHost;

        // 解析目标地址
        switch (addressType) {
            case 0x01: // IPv4地址
                byte[] ipv4 = new byte[4];
                input.readFully(ipv4);
                targetHost = InetAddress.getByAddress(ipv4).getHostAddress();
                break;
            case 0x03: // 域名
                int domainLength = input.readUnsignedByte();
                byte[] domainBytes = new byte[domainLength];
                input.readFully(domainBytes);
                targetHost = new String(domainBytes);
                break;
            case 0x04: // IPv6地址
                byte[] ipv6 = new byte[16];
                input.readFully(ipv6);
                targetHost = InetAddress.getByAddress(ipv6).getHostAddress();
                break;
            default:
                log.error("SOCKS proxy - Client handler({}), Handle request error, unsupported SOCKS address type {}", SocketUtils.socketInfo(clientSocket), addressType);
                sendErrorResponse(output, 0x08); // 不支持的地址类型
                return;
        }

        // 读取目标端口
        int targetPort = input.readUnsignedShort();

        log.info("SOCKS proxy - Client handler({}), Handle request, connect to {}:{}", SocketUtils.socketInfo(clientSocket), targetHost, targetPort);

        // 连接到目标服务器
        try (Socket targetSocket = new SSLVPNClient().connectToVPNServer(targetHost, targetPort)) {
            // 发送成功响应
            sendSuccessResponse(output, targetSocket.getLocalAddress(), targetSocket.getLocalPort());

            // 开始数据转发
            SocketUtils.startTunnel(clientSocket, targetSocket);
        } catch (Exception ex) {
            log.error("SOCKS proxy - Client handler({}), Handle request error {}", SocketUtils.socketInfo(clientSocket), ex.getMessage(), ex);
            sendErrorResponse(output, 0x05); // 连接被拒绝
        }
    }

    private void sendSuccessResponse(DataOutputStream output, InetAddress bindAddress, int bindPort) throws IOException {
        log.info("SOCKS proxy - Client handler({}), Send success response", SocketUtils.socketInfo(clientSocket));
        output.writeByte(0x05); // SOCKS版本
        output.writeByte(0x00); // 成功
        output.writeByte(0x00); // 保留

        byte[] addressBytes = bindAddress.getAddress();
        if (addressBytes.length == 4) {
            output.writeByte(0x01); // IPv4地址类型
        } else {
            output.writeByte(0x04); // IPv6地址类型
        }

        output.write(addressBytes);
        output.writeShort(bindPort);
        output.flush();
    }

    private void sendErrorResponse(DataOutputStream output, int errorCode) throws IOException {
        log.info("SOCKS proxy - Client handler({}), Send error response, {}", SocketUtils.socketInfo(clientSocket), errorCode);
        output.writeByte(0x05); // SOCKS版本
        output.writeByte(errorCode); // 错误代码
        output.writeByte(0x00); // 保留
        output.writeByte(0x01); // IPv4地址类型
        output.write(new byte[4]); // 空IP地址
        output.writeShort(0); // 空端口
        output.flush();
    }
}