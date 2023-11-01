package com.example.rtsp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class NetUtils {

    private static final String TAG = "RtspThread";
    private static final boolean DEBUG = false;
    private final static int MAX_LINE_SIZE = 4098 / 2;

    @NonNull
    public static SSLSocket createSslSocketAndConnect(@NonNull String dstName, int dstPort, int timeout) throws Exception {
//        if (DEBUG)
//            Log.v(TAG, "createSslSocketAndConnect(dstName=" + dstName + ", dstPort=" + dstPort + ", timeout=" + timeout + ")");

//        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//        trustManagerFactory.init((KeyStore) null);
//        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
//        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
//           throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
//        }
//        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new FakeX509TrustManager()}, null);
        SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket();
        sslSocket.connect(new InetSocketAddress(dstName, dstPort), timeout);
        sslSocket.setSoLinger(false, 1);
        sslSocket.setSoTimeout(timeout);
        return sslSocket;
    }

    @NonNull
    public static Socket createSocketAndConnect(@NonNull String dstName, int dstPort, int timeout) throws IOException {
//        if (DEBUG)
//            Log.d(TAG, "createSocketAndConnect(dstName=" + dstName + ", dstPort=" + dstPort + ", timeout=" + timeout + ")");
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(dstName, dstPort), timeout); // todo error - need check with Andrew
        socket.setSoLinger(false, 1);
        socket.setSoTimeout(timeout);
        return socket;
    }

    public static void closeSocket(@Nullable Socket socket) throws IOException {
//        if (DEBUG)
//            Log.v(TAG, "closeSocket()");
        if (socket != null) {
            try {
                socket.shutdownInput();
            } catch (Exception ignored) {
            }
            try {
                socket.shutdownOutput();
            } catch (Exception ignored) {
            }
            socket.close();
        }
    }

//System.err: java.net.UnknownHostException: null
//System.err:     at java.net.AbstractPlainSocketImpl.connect(AbstractPlainSocketImpl.java:208)
//System.err:     at java.net.SocksSocketImpl.connect(SocksSocketImpl.java:436)
//System.err:     at java.net.Socket.connect(Socket.java:621)
//System.err:     at by.beltelecom.cctv.ui.player.rtsp.utils.NetUtils.createSocketAndConnect(NetUtils.java:98)
//System.err:     at by.beltelecom.cctv.ui.player.rtsp.RtspThread.run(RtspThread.kt:221)

    public static int readData(@NonNull InputStream inputStream, @NonNull byte[] buffer, int offset, int length) throws IOException {
        int readBytes;
        int totalReadBytes = 0;
        try {
            do {
                readBytes = inputStream.read(buffer, offset + totalReadBytes, length - totalReadBytes); // java.net.SocketTimeoutException: Read timed out
                if (readBytes > 0)
                    totalReadBytes += readBytes;
            } while (readBytes >= 0 && totalReadBytes < length);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return totalReadBytes;
    }

    public static int readData(@NonNull InputStream inputStream, @NonNull byte[] buffer, int offset, int length, ErrorCallback callback) throws IOException {
        int readBytes;
        int totalReadBytes = 0;
        try {
            do {
                readBytes = inputStream.read(buffer, offset + totalReadBytes, length - totalReadBytes); // java.net.SocketTimeoutException: Read timed out
                if (readBytes > 0)
                    totalReadBytes += readBytes;
            } while (readBytes >= 0 && totalReadBytes < length);
        } catch (IOException e) {
            e.printStackTrace();
            callback.showError();
        }
        return totalReadBytes;
    }

    public interface ErrorCallback {
        void showError();
    }

    public static final class FakeX509TrustManager implements X509TrustManager {

        /**
         * Accepted issuers for fake trust manager
         */
        final static private X509Certificate[] mAcceptedIssuers = new X509Certificate[]{};

        /**
         * Constructor for FakeX509TrustManager.
         */
        public FakeX509TrustManager() {
        }

        /**
         * @see X509TrustManager#checkClientTrusted(X509Certificate[], String authType)
         */
        public void checkClientTrusted(X509Certificate[] certificates, String authType)
                throws CertificateException {
        }

        /**
         * @see X509TrustManager#checkServerTrusted(X509Certificate[], String authType)
         */
        public void checkServerTrusted(X509Certificate[] certificates, String authType)
                throws CertificateException {
        }

        // https://github.com/square/okhttp/issues/4669
        // Called by Android via reflection in X509TrustManagerExtensions.
        @SuppressWarnings("unused")
        public List<X509Certificate> checkServerTrusted(X509Certificate[] chain, String authType, String host) throws CertificateException {
            return Arrays.asList(chain);
        }

        /**
         * @see X509TrustManager#getAcceptedIssuers()
         */
        public X509Certificate[] getAcceptedIssuers() {
            return mAcceptedIssuers;
        }
    }
}
