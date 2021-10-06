package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;

import bitswap.pb.MessageOuterClass;
import identify.pb.IdentifyOuterClass;
import relay.pb.Relay;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.bitswap.BitSwapMessage;
import threads.lite.core.ProtocolIssue;
import threads.lite.utils.DataHandler;


public class StreamHandler {
    private static final String TAG = StreamHandler.class.getSimpleName();
    protected final int streamId;
    private final LiteHost host;

    @NonNull
    private final QuicStream quicStream;
    @NonNull
    private final DataHandler reader = new DataHandler(new HashSet<>(
            Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.PUSH_PROTOCOL, IPFS.BITSWAP_PROTOCOL,
                    IPFS.IDENTITY_PROTOCOL, IPFS.DHT_PROTOCOL, IPFS.RELAY_PROTOCOL,
                    IPFS.PING_PROTOCOL, IPFS.MESHSUB_PROTOCOL, IPFS.NOISE_PROTOCOL,
                    IPFS.SIM_CONNECT_PROTOCOL)
    ), IPFS.MESSAGE_SIZE_MAX);
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public StreamHandler(@NonNull QuicStream quicStream, @NonNull LiteHost host) {
        this.inputStream = quicStream.getInputStream();
        this.outputStream = quicStream.getOutputStream();
        this.streamId = quicStream.getStreamId();
        this.quicStream = quicStream;
        this.host = host;
        new Thread(this::reading).start();
        LogUtils.debug(TAG, "Instance" + " StreamId " + streamId +
                " Connection " + quicStream.getConnection().getRemoteAddress().toString());
    }


    protected void reading() {
        byte[] buf = new byte[4096];
        try {
            int length;

            while ((length = inputStream.read(buf, 0, 4096)) > 0) {
                byte[] data = Arrays.copyOfRange(buf, 0, length);
                channelRead0(data);
            }

        } catch (Throwable throwable) {
            exceptionCaught(throwable);
        }
    }


    public void exceptionCaught(@NonNull Throwable cause) {
        LogUtils.error(TAG, "Error" + " StreamId " + streamId +
                " Connection " + quicStream.getConnection().getRemoteAddress().toString() + " " + cause);
        reader.clear();
    }

    public void closeOutputStream() {
        try {
            outputStream.close();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void channelRead0(@NonNull byte[] msg) throws Exception {

        try {
            reader.load(msg);

            if (reader.isDone()) {
                for (String token : reader.getTokens()) {

                    LogUtils.debug(TAG, "Token " + token + " StreamId " + streamId);

                    switch (token) {
                        case IPFS.RELAY_PROTOCOL:
                            outputStream.write(DataHandler.writeToken(IPFS.RELAY_PROTOCOL));
                            break;
                        case IPFS.STREAM_PROTOCOL:
                            outputStream.write(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
                            break;
                        case IPFS.PUSH_PROTOCOL:
                            outputStream.write(DataHandler.writeToken(IPFS.PUSH_PROTOCOL));
                            break;
                        case IPFS.BITSWAP_PROTOCOL:
                            outputStream.write(DataHandler.writeToken(IPFS.BITSWAP_PROTOCOL));
                            break;
                        case IPFS.IDENTITY_PROTOCOL:
                            outputStream.write(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                            IdentifyOuterClass.Identify response =
                                    host.createIdentity(
                                            quicStream.getConnection().getRemoteAddress());

                            outputStream.write(DataHandler.encode(response));
                            closeOutputStream();
                            return;
                        default:
                            LogUtils.debug(TAG, "Ignore " + token + " StreamId " + streamId);
                            outputStream.write(DataHandler.writeToken(IPFS.NA));
                            closeOutputStream();
                            return;
                    }
                }
                byte[] message = reader.getMessage();

                if (message != null) {
                    String protocol = reader.getProtocol();
                    if (protocol != null) {
                        switch (protocol) {
                            case IPFS.BITSWAP_PROTOCOL: {
                                BitSwapMessage response = host.message(quicStream,
                                        MessageOuterClass.Message.parseFrom(message));
                                if (response != null) {
                                    outputStream.write(DataHandler.encode(response.ToProtoV1()));
                                }
                                closeOutputStream();
                                break;
                            }
                            case IPFS.PUSH_PROTOCOL: {
                                host.push(quicStream.getConnection(), message);
                                closeOutputStream();
                                break;
                            }
                            case IPFS.RELAY_PROTOCOL: {
                                Relay.CircuitRelay circuitRelay = Relay.CircuitRelay.parseFrom(message);

                                if (circuitRelay.getType() == Relay.CircuitRelay.Type.STOP) {
                                    outputStream.write(DataHandler.encode(Relay.CircuitRelay.newBuilder()
                                            .setType(Relay.CircuitRelay.Type.STATUS)
                                            .setCode(Relay.CircuitRelay.Status.SUCCESS)
                                            .build()));
                                } else {
                                    outputStream.write(DataHandler.encode(Relay.CircuitRelay.newBuilder()
                                            .setType(Relay.CircuitRelay.Type.STATUS)
                                            .setCode(Relay.CircuitRelay.Status.STOP_RELAY_REFUSED)
                                            .build()));
                                }
                                break;
                            }
                            default:
                                throw new Exception("StreamHandler invalid protocol");
                        }
                    } else {
                        throw new Exception("StreamHandler invalid protocol");
                    }
                }
            }

        } catch (ProtocolIssue protocolIssue) {
            LogUtils.error(TAG, protocolIssue.getMessage() + " StreamId " + streamId);
            outputStream.write(DataHandler.writeToken(IPFS.NA));
            closeOutputStream();
            throw protocolIssue;
        }
    }
}
