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
        synchronized (SSLSocketFactories.class) {
            if (sslServerSocketFactory != null) {
                return sslServerSocketFactory;
            }
            SSLContext sslContext = createSSLContext(SERVER_KEY_STORE_PATH, SERVER_KEY_STORE_PASS_PHRASE,
                    SERVER_TRUST_STORE_PATH, SERVER_TRUST_STORE_PASS_PHRASE);
            sslServerSocketFactory = sslContext.getServerSocketFactory();
            return sslServerSocketFactory;
        }
    }

    public static SSLSocketFactory sslSocketFactory() throws Exception {
        if (sslSocketFactory != null) {
            return sslSocketFactory;
        }
        synchronized (SSLSocketFactories.class) {
            if (sslSocketFactory != null) {
                return sslSocketFactory;
            }
            SSLContext sslContext = createSSLContext(CLIENT_KEY_STORE_PATH, CLIENT_KEY_STORE_PASS_PHRASE,
                    CLIENT_TRUST_STORE_PATH, CLIENT_TRUST_STORE_PASS_PHRASE);
            sslSocketFactory = sslContext.getSocketFactory();
            return sslSocketFactory;
        }
    }

    private static SSLContext createSSLContext(String keyStorePath, String keyStorePass,
                                               String trustStorePath, String trustStorePass) throws Exception {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try (InputStream keyStoreInputStream = SSLSocketFactories.class.getClassLoader().getResourceAsStream(keyStorePath)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(keyStoreInputStream, keyStorePass.toCharArray());
            keyManagerFactory.init(keyStore, keyStorePass.toCharArray());
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        try (InputStream trustStoreInputStream = SSLSocketFactories.class.getClassLoader().getResourceAsStream(trustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(trustStoreInputStream, trustStorePass.toCharArray());
            trustManagerFactory.init(trustStore);
        }

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
        return sslContext;
    }
}
