package zxf;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

@Slf4j
public class LocalSocksProxyStarter {
    private static final int LOCAL_SOCKS_PORT = 1080;

    public static void main(String[] args) throws Exception {
        SSLVPNClient client = new SSLVPNClient();
        // 启动本地SOCKS代理服务器
        startLocalSocksProxy(client.connectToVPNServer());
    }

    private static void startLocalSocksProxy(SSLSocket vpnSocket) throws IOException {
        ServerSocket localServerSocket = new ServerSocket(LOCAL_SOCKS_PORT);
        log.info("SOCKS proxy started on port {}", LOCAL_SOCKS_PORT);

        ExecutorService threadPool = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("socks-processor-%d").build());

        while (true) {
            try {
                Socket clientSocket = localServerSocket.accept();
                log.info("New client connected:  {}, {}", clientSocket.getLocalSocketAddress(), clientSocket.getRemoteSocketAddress());
                threadPool.execute(new SocksHandler(clientSocket, vpnSocket));
            } catch (Exception ex) {
                log.error("Exception when wait for client connect {}", ex.getMessage(), ex);
            }
        }
    }

    // SOCKS协议处理器
    private record SocksHandler(Socket clientSocket, SSLSocket vpnSocket) implements Runnable {

        @Override
        public void run() {
            try {
                DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());

                // SOCKS5握手
                int version = clientInput.readByte();
                int nmethods = clientInput.readByte();
                clientInput.skipBytes(nmethods);

                // 响应握手
                clientOutput.writeByte(0x05); // SOCKS5
                clientOutput.writeByte(0x00); // 无需认证
                clientOutput.flush();

                // 读取连接请求
                version = clientInput.readByte();
                int cmd = clientInput.readByte();
                int rsv = clientInput.readByte();
                int atype = clientInput.readByte();

                String targetHost;
                int targetPort;

                if (atype == 0x01) { // IPv4地址
                    byte[] ipBytes = new byte[4];
                    clientInput.readFully(ipBytes);
                    targetHost = (ipBytes[0] & 0xFF) + "." + (ipBytes[1] & 0xFF) + "." +
                            (ipBytes[2] & 0xFF) + "." + (ipBytes[3] & 0xFF);
                    targetPort = clientInput.readShort() & 0xFFFF;
                } else if (atype == 0x03) { // 域名
                    int domainLength = clientInput.readByte() & 0xFF;
                    byte[] domainBytes = new byte[domainLength];
                    clientInput.readFully(domainBytes);
                    targetHost = new String(domainBytes);
                    targetPort = clientInput.readShort() & 0xFFFF;
                } else {
                    throw new IOException("Unsupported address type");
                }

                log.info("SOCKS request to {}:{}", targetHost, targetPort);

                // 通过VPN隧道连接目标
                DataOutputStream vpnOutput = new DataOutputStream(vpnSocket.getOutputStream());
                DataInputStream vpnInput = new DataInputStream(vpnSocket.getInputStream());

                // 发送连接请求到VPN网关
                vpnOutput.writeByte(0x01); // 连接请求
                vpnOutput.writeInt(targetPort);
                vpnOutput.writeInt(targetHost.getBytes().length);
                vpnOutput.write(targetHost.getBytes());
                vpnOutput.flush();

                // 读取VPN网关响应
                int responseType = vpnInput.readByte();
                int success = vpnInput.readInt();

                if (success == 1) {
                    // 发送SOCKS成功响应
                    clientOutput.writeByte(0x05); // SOCKS5
                    clientOutput.writeByte(0x00); // 成功
                    clientOutput.writeByte(0x00); // RSV
                    clientOutput.writeByte(0x01); // IPv4
                    clientOutput.write(new byte[]{0, 0, 0, 0}); // 绑定地址
                    clientOutput.writeShort(0); // 绑定端口
                    clientOutput.flush();

                    // 启动数据转发
                    startTunneling(clientSocket, vpnSocket);
                } else {
                    clientOutput.writeByte(0x05);
                    clientOutput.writeByte(0x01); // 常规SOCKS服务器故障
                    clientOutput.flush();
                    clientSocket.close();
                }

            } catch (Exception ex) {
                log.error("Exception when forward data {}", ex.getMessage(), ex);
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.error("Exception when close client socket {}", e.getMessage(), e);
                }
            }
        }

        private void startTunneling(Socket clientSocket, SSLSocket vpnSocket) {
            try {
                InputStream clientInput = clientSocket.getInputStream();
                OutputStream clientOutput = clientSocket.getOutputStream();
                InputStream vpnInput = vpnSocket.getInputStream();
                OutputStream vpnOutput = vpnSocket.getOutputStream();

                // 启动双向数据转发
                Thread clientToVpn = new Thread(new DataForwarder(clientInput, vpnOutput), clientSocket.getRemoteSocketAddress().toString() + "-" + vpnSocket.getRemoteSocketAddress());
                Thread vpnToClient = new Thread(new DataForwarder(vpnInput, clientOutput), vpnSocket.getRemoteSocketAddress().toString() + "-" + clientSocket.getRemoteSocketAddress());

                clientToVpn.start();
                vpnToClient.start();

            } catch (IOException ex) {
                log.error("Exception when start data forward {}", ex.getMessage(), ex);
            }
        }
    }
}