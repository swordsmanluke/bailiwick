package threads.lite.ident;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import identify.pb.IdentifyOuterClass;
import threads.lite.IPFS;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.host.PeerInfo;
import threads.lite.utils.DataHandler;

public class IdentityService {
    public static final String TAG = IdentityService.class.getSimpleName();

    @NonNull
    public static PeerInfo getPeerInfo(@NonNull PeerId peerId, @NonNull QuicConnection conn)
            throws Exception {


        IdentifyOuterClass.Identify identify = IdentityService.getIdentity(conn);
        Objects.requireNonNull(identify);
        return getPeerInfo(peerId, identify);
    }

    public static PeerInfo getPeerInfo(@NonNull PeerId peerId,
                                       @NonNull IdentifyOuterClass.Identify identify) {

        String agent = identify.getAgentVersion();
        String version = identify.getProtocolVersion();
        Multiaddr observedAddr = null;
        if (identify.hasObservedAddr()) {
            observedAddr = new Multiaddr(identify.getObservedAddr().toByteArray());
        }

        List<String> protocols = new ArrayList<>();
        List<Multiaddr> addresses = new ArrayList<>();
        List<ByteString> entries = identify.getProtocolsList().asByteStringList();
        for (ByteString entry : entries) {
            protocols.add(entry.toStringUtf8());
        }
        entries = identify.getListenAddrsList();
        for (ByteString entry : entries) {
            addresses.add(new Multiaddr(entry.toByteArray()));
        }

        return new PeerInfo(peerId, agent, version, addresses, protocols, observedAddr);
    }

    @NonNull
    public static IdentifyOuterClass.Identify getIdentity(@NonNull QuicConnection conn)
            throws Exception {
        return requestIdentity(conn);
    }


    private static IdentifyOuterClass.Identify requestIdentity(
            @NonNull QuicConnection conn) throws Exception {


        QuicStream quicStream = conn.createStream(true, IPFS.CREATE_STREAM_TIMEOUT,
                TimeUnit.SECONDS);
        IdentityRequest identityRequest = new IdentityRequest(quicStream, IPFS.CONNECT_TIMEOUT,
                TimeUnit.SECONDS);

        // TODO quicStream.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));

        identityRequest.writeAndFlush(DataHandler.writeToken(
                IPFS.STREAM_PROTOCOL, IPFS.IDENTITY_PROTOCOL));

        identityRequest.closeOutputStream();

        return identityRequest.reading();

    }
}
