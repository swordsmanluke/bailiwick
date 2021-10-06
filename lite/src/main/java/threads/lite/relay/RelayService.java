package threads.lite.relay;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import net.luminis.quic.ConnectionIssue;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicStream;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import relay.pb.Relay;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.lite.core.DataLimitIssue;
import threads.lite.core.ProtocolIssue;
import threads.lite.utils.DataHandler;

public class RelayService {

    public static final String TAG = RelayService.class.getSimpleName();


    public static boolean canHop(@NonNull QuicConnection conn) {

        try {
            Relay.CircuitRelay message = Relay.CircuitRelay.newBuilder()
                    .setType(Relay.CircuitRelay.Type.CAN_HOP)
                    .build();

            long time = System.currentTimeMillis();

            QuicStream quicStream = conn.createStream(true, IPFS.CREATE_STREAM_TIMEOUT,
                    TimeUnit.SECONDS);
            RelayRequest relayRequest = new RelayRequest(quicStream, IPFS.CONNECT_TIMEOUT,
                    TimeUnit.SECONDS);

            // TODO quicStream.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_URGENT, false));

            relayRequest.writeAndFlush(DataHandler.writeToken(
                    IPFS.STREAM_PROTOCOL, IPFS.RELAY_PROTOCOL));
            relayRequest.writeAndFlush(DataHandler.encode(message));
            relayRequest.closeOutputStream();

            Relay.CircuitRelay msg = relayRequest.reading();

            LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
            Objects.requireNonNull(msg);

            return msg.getType() == Relay.CircuitRelay.Type.STATUS;

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    @NonNull
    public static QuicStream getStream(@NonNull QuicConnection conn, @NonNull PeerId self,
                                       @NonNull PeerId peerId, int timeout)
            throws IOException, ProtocolIssue, DataLimitIssue {

        Relay.CircuitRelay.Peer src = Relay.CircuitRelay.Peer.newBuilder()
                .setId(ByteString.copyFrom(self.getBytes())).build();
        Relay.CircuitRelay.Peer dest = Relay.CircuitRelay.Peer.newBuilder()
                .setId(ByteString.copyFrom(peerId.getBytes())).build();

        Relay.CircuitRelay message = Relay.CircuitRelay.newBuilder()
                .setType(Relay.CircuitRelay.Type.HOP)
                .setSrcPeer(src)
                .setDstPeer(dest)
                .build();

        long time = System.currentTimeMillis();


        QuicStream quicStream = conn.createStream(true,
                IPFS.CREATE_STREAM_TIMEOUT, TimeUnit.SECONDS);

        RelayRequest relayRequest = new RelayRequest(quicStream, timeout, TimeUnit.SECONDS);

        // TODO streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_URGENT, false));
        relayRequest.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL, IPFS.RELAY_PROTOCOL));
        relayRequest.writeAndFlush(DataHandler.encode(message));

        Relay.CircuitRelay msg = relayRequest.reading();
        LogUtils.info(TAG, "Request took " + (System.currentTimeMillis() - time));
        Objects.requireNonNull(msg);


        if (msg.getType() != Relay.CircuitRelay.Type.STATUS) {
            relayRequest.closeOutputStream();
            throw new ConnectionIssue(msg.getType().name());
        }

        if (msg.getCode() != Relay.CircuitRelay.Status.SUCCESS) {
            relayRequest.closeOutputStream();
            throw new ConnectionIssue(msg.getCode().name());
        }

        LogUtils.error(TAG, "Success Relay Stream to " + peerId.toBase58());
        return quicStream;


    }

}
