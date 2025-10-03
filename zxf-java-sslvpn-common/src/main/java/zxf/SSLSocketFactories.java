package zxf;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

@Slf4j
public class SSLSocketFactories {
    private static final String SERVER_KEY_STORE_PATH = "keystore/keystore-server.jks";
    private static final String SERVER_KEY_STORE_PASS_PHRASE = "changeit";
    private static final String SERVER_TRUST_STORE_PATH = "keystore/truststore-server.jks";
    private static final String SERVER_TRUST_STORE_PASS_PHRASE = "changeit";
    private static final String CLIENT_KEY_STORE_PATH = "keystore/keystore-client.jks";
    private static final String CLIENT_KEY_STORE_PASS_PHRASE = "changeit";
    private static final String CLIENT_TRUST_STORE_PATH = "keystore/truststore-client.jks";
    private static final String CLIENT_TRUST_STORE_PASS_PHRASE = "changeit";
    private static volatile SSLServerSocketFactory sslServerSocketFactory;
    private static volatile SSLSocketFactory sslSocketFactory;


    public static SSLServerSocketFactory sslServerSocketFactory() throws Exception {
        if (sslServerSocketFactory != null) {
            return sslServerSocketFactory;
        }
        // 启用 SSL 调试日志
        //System.setProperty("javax.net.debug", "all");

        //Key-Store
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLSocketFactories.class.getClassLoader().getResourceAsStream(SERVER_KEY_STORE_PATH)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(trustStoreInputStream, SERVER_KEY_STORE_PASS_PHRASE.toCharArray());
            keyManagerFactory.init(keyStore, SERVER_KEY_STORE_PASS_PHRASE.toCharArray());
        }

        //Trust-Store
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLSocketFactories.class.getClassLoader().getResourceAsStream(SERVER_TRUST_STORE_PATH)) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(trustStoreInputStream, SERVER_TRUST_STORE_PASS_PHRASE.toCharArray());
            trustManagerFactory.init(trustStore);
        }

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

        sslServerSocketFactory = sslContext.getServerSocketFactory();
        return sslServerSocketFactory;
    }

    public static SSLSocketFactory sslSocketFactory() throws Exception {
        if (sslSocketFactory != null) {
            return sslSocketFactory;
        }

        // 启用 SSL 调试日志
        //System.setProperty("javax.net.debug", "all");

        //Key-Store
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLSocketFactories.class.getClassLoader().getResourceAsStream(CLIENT_KEY_STORE_PATH)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(trustStoreInputStream, CLIENT_KEY_STORE_PASS_PHRASE.toCharArray());
            keyManagerFactory.init(keyStore, CLIENT_KEY_STORE_PASS_PHRASE.toCharArray());
        }

        //Trust-Store
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLSocketFactories.class.getClassLoader().getResourceAsStream(CLIENT_TRUST_STORE_PATH)) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(trustStoreInputStream, CLIENT_TRUST_STORE_PASS_PHRASE.toCharArray());
            trustManagerFactory.init(trustStore);
        }

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

        sslSocketFactory = sslContext.getSocketFactory();
        return sslSocketFactory;
    }
}
