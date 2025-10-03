package zxf.socks;

import lombok.extern.slf4j.Slf4j;
import zxf.DataForwarder;
import zxf.SocketUtils;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;

@Slf4j
public class Socks5Handler implements Runnable {
    private final Socket clientSocket;
    private final SSLSocket vpnSocket;

    public Socks5Handler(Socket clientSocket, SSLSocket vpnSocket) {
        this.clientSocket = clientSocket;
        this.vpnSocket = vpnSocket;
    }

    @Override
    public void run() {
        try {
            handleClient();
        } catch (IOException ex) {
            log.error("SOCKS proxy error on process {}", ex.getMessage(), ex);
        } finally {
            SocketUtils.closeQuietly(clientSocket);
        }
    }

    private void handleClient() throws IOException {
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
        // 读取版本和方法数量
        int version = input.readUnsignedByte();
        int methodCount = input.readUnsignedByte();

        if (version != 0x05) {
            log.error("SOCKS proxy error on process: unsupported SOCKS version {}", version);
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

    private void handleRequest(DataInputStream input, DataOutputStream output) throws IOException {
        // 读取请求头
        int version = input.readUnsignedByte();
        int command = input.readUnsignedByte();
        int reserved = input.readUnsignedByte();
        int addressType = input.readUnsignedByte();

        if (version != 0x05) {
            sendErrorResponse(output, 0x01); // 常规SOCKS服务器故障
            return;
        }

        // 只支持CONNECT命令 (0x01)
        if (command != 0x01) {
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
                sendErrorResponse(output, 0x08); // 不支持的地址类型
                return;
        }

        // 读取目标端口
        int targetPort = input.readUnsignedShort();

        log.info("SOCKS proxy process, connect to {}:{}", targetHost, targetPort);

        // 连接到目标服务器
        try (Socket targetSocket = new Socket(targetHost, targetPort)) {
            // 发送成功响应
            sendSuccessResponse(output, targetSocket.getLocalAddress(), targetSocket.getLocalPort());

            // 开始数据转发
            startTunnel(clientSocket, targetSocket);

        } catch (IOException e) {
            System.err.println("连接目标失败: " + e.getMessage());
            sendErrorResponse(output, 0x05); // 连接被拒绝
        }
    }

    private void sendSuccessResponse(DataOutputStream output, InetAddress bindAddress, int bindPort) throws IOException {
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
        output.writeByte(0x05); // SOCKS版本
        output.writeByte(errorCode); // 错误代码
        output.writeByte(0x00); // 保留
        output.writeByte(0x01); // IPv4地址类型
        output.write(new byte[4]); // 空IP地址
        output.writeShort(0); // 空端口
        output.flush();
    }

    private void startTunnel(Socket client, Socket target) {
        // 客户端到目标服务器的数据转发
        Thread clientToTarget = new DataForwarder(client, target).start();
        // 目标服务器到客户端的数据转发
        Thread targetToClient = new DataForwarder(target, client).start();

        try {
            clientToTarget.join();
            targetToClient.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}