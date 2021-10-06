package threads.lite.relay;

import androidx.annotation.NonNull;

import net.luminis.quic.ConnectionIssue;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicConstants;
import net.luminis.quic.Statistics;
import net.luminis.quic.stream.QuicStream;

import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import threads.lite.IPFS;
import threads.lite.cid.PeerId;
import threads.lite.host.LiteHost;

public class RelayConnection implements QuicConnection {

    private final PeerId peerId;
    private final LiteHost host;
    private final QuicConnection conn;

    private RelayConnection(@NonNull QuicConnection relay, @NonNull PeerId peerId, @NonNull LiteHost host) {
        this.conn = relay;
        this.peerId = peerId;
        this.host = host;
    }

    private static int getRandomNumberUsingNextInt(int max) {
        Random random = new Random();
        return random.nextInt(max);
    }


    public static RelayConnection createRandomRelayConnection(
            @NonNull PeerId peerId, @NonNull LiteHost host) {

        List<QuicConnection> relays = new ArrayList<>(host.relays().values());
        int random = getRandomNumberUsingNextInt(relays.size());
        QuicConnection relay = relays.get(random);
        Objects.requireNonNull(relay);
        return createRelayConnection(relay, peerId, host);

    }

    public static RelayConnection createRelayConnection(@NonNull QuicConnection relay,
                                                        @NonNull PeerId peerId,
                                                        @NonNull LiteHost host) {
        return new RelayConnection(relay, peerId, host);

    }

    @Override
    public void setMaxAllowedBidirectionalStreams(int max) {
        conn.setMaxAllowedBidirectionalStreams(max);
    }

    @Override
    public void setMaxAllowedUnidirectionalStreams(int max) {
        conn.setMaxAllowedUnidirectionalStreams(max);
    }

    @Override
    public void setDefaultStreamReceiveBufferSize(long size) {
        conn.setDefaultStreamReceiveBufferSize(size);
    }

    @Override
    public X509Certificate getRemoteCertificate() {
        throw new RuntimeException("not allowed");
    }

    @Override
    public QuicStream createStream(boolean bidirectional, long timeout, TimeUnit timeoutUnit)
            throws ConnectionIssue {

        try {
            return RelayService.getStream(conn, host.self(),
                    peerId, (int) timeoutUnit.toSeconds(timeout));
        } catch (ConnectionIssue connectionIssue) {
            throw connectionIssue;
        } catch (Throwable throwable) {
            throw new ConnectionIssue(throwable.getMessage());
        }
    }

    @Override
    public QuicStream createStream(boolean bidirectional) throws ConnectionIssue {
        return createStream(bidirectional, IPFS.CONNECT_TIMEOUT, TimeUnit.SECONDS);
    }

    @Override
    public void setPeerInitiatedStreamCallback(Consumer<QuicStream> streamConsumer) {
        throw new RuntimeException("not allowed");
    }

    @Override
    public void close() {
    }

    @Override
    public void close(QuicConstants.TransportErrorCode applicationError, String errorReason) {
    }

    @Override
    public Statistics getStats() {
        throw new RuntimeException("not allowed");
    }

    @Override
    public boolean isConnected() {
        return conn.isConnected();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return conn.getRemoteAddress();
    }
}
