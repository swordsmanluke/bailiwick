package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.function.Consumer;

public class ServerHandler extends ApplicationProtocolConnection implements Consumer<QuicStream> {
    private static final String TAG = ServerHandler.class.getSimpleName();

    private final LiteHost liteHost;

    public ServerHandler(@NonNull LiteHost liteHost, @NonNull QuicConnection quicConnection) {
        this.liteHost = liteHost;


        /* NOT YET REQUIRED
        X509Certificate cert = quicConnection.getRemoteCertificate();
        Objects.requireNonNull(cert);
        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
        Objects.requireNonNull(pubKey);
        PeerId peerId = PeerId.fromPubKey(pubKey);
        Objects.requireNonNull(peerId); */


        quicConnection.setPeerInitiatedStreamCallback(this);

    }

    @Override
    public void accept(QuicStream quicStream) {
        new StreamHandler(quicStream, liteHost);
    }
}
