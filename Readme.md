# javax.net.ssl.SSLContext
- public static SSLContext getInstance(String protocol)
- public final void init(KeyManager[] km, TrustManager[] tm, SecureRandom random)
- public final SSLSocketFactory getSocketFactory()
- public final SSLServerSocketFactory getServerSocketFactory()

# javax.net.ssl.KeyManager
- javax.net.ssl.X509KeyManager
- javax.net.ssl.X509ExtendedKeyManager
- sun.security.ssl.X509KeyManagerImpl
- sun.security.ssl.SunX509KeyManagerImpl

# javax.net.ssl.TrustManager
- javax.net.ssl.X509TrustManager
- javax.net.ssl.X509ExtendedTrustManager
- sun.security.ssl.X509TrustManagerImpl

# javax.net.ssl.SSLServerSocketFactory
- sun.security.ssl.SSLServerSocketFactoryImpl

# javax.net.ssl.SSLServerSocket
- sun.security.ssl.SSLServerSocketImpl

# javax.net.ssl.SSLSocketFactory
- sun.security.ssl.SSLSocketFactoryImpl

# javax.net.ssl.SSLSocket
- sun.security.ssl.BaseSSLSocketImpl
- sun.security.ssl.SSLSocketImpl
