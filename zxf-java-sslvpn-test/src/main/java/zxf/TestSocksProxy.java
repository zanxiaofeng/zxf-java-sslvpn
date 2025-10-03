package zxf;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;

public class TestSocksProxy {
    public static void main(String[] args) throws Exception {
        /*
        // Set up the authenticator
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("username", "password".toCharArray());
            }
        });
        */

        // Create a SOCKS proxy object
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1080));

        // Open a connection using the proxy
        URL url = new URL("https://www.163.com");
        URLConnection connection = url.openConnection(proxy);

        // Read data from the connection
        try (InputStream in = connection.getInputStream()) {
            // Process the input stream
        }
    }
}
