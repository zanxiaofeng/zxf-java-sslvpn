import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;
import sun.security.x509.*;

public class CertificateGenerator {

    public static void generateKeyStore() throws Exception {
        // 生成密钥对
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // 创建证书
        X509CertInfo certInfo = new X509CertInfo();
        Date from = new Date();
        Date to = new Date(from.getTime() + 365 * 24 * 60 * 60 * 1000L); // 1年

        CertificateValidity interval = new CertificateValidity(from, to);
        X500Name owner = new X500Name("CN=SSL VPN Server, O=MyCompany, C=CN");

        certInfo.set(X509CertInfo.VALIDITY, interval);
        certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new java.util.Random().nextInt()));
        certInfo.set(X509CertInfo.SUBJECT, owner);
        certInfo.set(X509CertInfo.ISSUER, owner);
        certInfo.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic()));
        certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));

        AlgorithmId algo = new AlgorithmId(AlgorithmId.sha256WithRSAEncryption_oid);
        certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

        // 签名证书
        X509CertImpl cert = new X509CertImpl(certInfo);
        cert.sign(keyPair.getPrivate(), "SHA256withRSA");

        // 保存到密钥库
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("vpnserver", keyPair.getPrivate(), "password".toCharArray(),
                new java.security.cert.Certificate[]{cert});

        try (FileOutputStream fos = new FileOutputStream("server.keystore")) {
            keyStore.store(fos, "password".toCharArray());
        }

        System.out.println("Server keystore generated successfully");
    }

    public static void main(String[] args) throws Exception {
        generateKeyStore();
    }
}