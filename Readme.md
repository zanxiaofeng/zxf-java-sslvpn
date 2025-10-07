# Core classes of SSLContext
- javax.net.ssl.SSLContext
- public static SSLContext getInstance(String protocol)
- public final void init(KeyManager[] km, TrustManager[] tm, SecureRandom random)
- public final SSLSocketFactory getSocketFactory()
- public final SSLServerSocketFactory getServerSocketFactory()
- #KeyManager#
- javax.net.ssl.KeyManager
- javax.net.ssl.X509KeyManager
- javax.net.ssl.X509ExtendedKeyManager
- sun.security.ssl.X509KeyManagerImpl
- sun.security.ssl.SunX509KeyManagerImpl
- #TrustManager#
- javax.net.ssl.TrustManager
- javax.net.ssl.X509TrustManager
- javax.net.ssl.X509ExtendedTrustManager
- sun.security.ssl.X509TrustManagerImpl

# Core class of SSL socket server:
- javax.net.ssl.SSLServerSocketFactory
- sun.security.ssl.SSLServerSocketFactoryImpl
- javax.net.ssl.SSLServerSocket
- sun.security.ssl.SSLServerSocketImpl

# Core class of SSL socket client:
- javax.net.ssl.SSLSocketFactory
- sun.security.ssl.SSLSocketFactoryImpl
- javax.net.ssl.SSLSocket
- sun.security.ssl.BaseSSLSocketImpl
- sun.security.ssl.SSLSocketImpl

# SEQ & ACK
- In TCP, the Sequence Number (SEQ) tracks the order of bytes sent by a host, ensuring data is received in the correct sequence. The Acknowledgement Number (ACK) is used by the receiver to tell the sender which bytes it expects next, based on the data it has already received. These two numbers coordinate the reliable, ordered transfer of data during a TCP connection.

## Sequence Number (SEQ)
- Purpose: To keep a count of every byte sent by the TCP sender.
- Initialization: When a TCP connection is established, a unique Initial Sequence Number (ISN) is randomly generated for each side of the connection to avoid conflicts with previous connections.
- Increment: When a segment of data is sent, the sequence number is incremented by the number of data bytes in that segment. For special segments like SYN (synchronize) or FIN (finish), the sequence number is incremented by 1.

## Acknowledgement Number (ACK)
- Purpose: To acknowledge the reception of data and indicate the sequence number of the next expected byte.
- Calculation: The receiver sets the ACK number to the sequence number of the segment it received plus the number of bytes it received in that segment.
- Flag: The ACK flag in the TCP header must be set to 1 for the ACK number field to be considered valid.

## How they work together
- Sender sends data: A sender transmits a TCP segment with its sequence number and the data payload.
- Receiver acknowledges data: The receiver receives the segment and sends back an acknowledgment (ACK) packet.
- ACK Number shows next expected byte: The ACK number in the receiver's packet indicates the sequence number of the next byte the receiver is expecting to receive.
- Data integrity: This "positive acknowledgment" mechanism, combined with retransmission and timeouts, ensures that data is delivered reliably and in the correct order, even if packets are lost or arrive out of sequence.

