package threads.lite.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import bitswap.pb.MessageOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;
import threads.lite.host.LiteHost;
import threads.lite.utils.DataHandler;


public class BitSwap implements Exchange {

    private static final String TAG = BitSwap.class.getSimpleName();

    @NonNull
    private final BitSwapManager bitSwapManager;
    @NonNull
    private final BitSwapEngine engine;

    public BitSwap(@NonNull BlockStore blockstore, @NonNull LiteHost host) {
        bitSwapManager = new BitSwapManager(this, blockstore, host);
        engine = new BitSwapEngine(blockstore, host.self());
    }

    @Nullable
    @Override
    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
        return bitSwapManager.getBlock(closeable, cid, root);
    }

    @Override
    public void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
        bitSwapManager.loadBlocks(closeable, cids);
    }

    @Override
    public void reset() {
        bitSwapManager.reset();
    }

    @Nullable
    public BitSwapMessage receiveMessage(@NonNull QuicStream quicStream, @NonNull BitSwapMessage bsm) {

        receiveMessage(quicStream.getConnection(), bsm);

        if (IPFS.BITSWAP_ENGINE_ACTIVE) {
            return engine.messageReceived(bsm);
        }
        return null;

    }


    private void receiveMessage(@NonNull QuicConnection conn, @NonNull BitSwapMessage bsm) {

        LogUtils.debug(TAG, "ReceiveMessage " +
                conn.getRemoteAddress().toString());

        List<Block> wanted = bsm.Blocks();
        List<Cid> haves = bsm.Haves();
        if (wanted.size() > 0 || haves.size() > 0) {
            for (Block block : wanted) {
                LogUtils.info(TAG, "Block Received " + block.getCid().String() + " " +
                        conn.getRemoteAddress().toString());
                bitSwapManager.blockReceived(block);
            }

            bitSwapManager.haveReceived(conn, haves);
        }

    }

    void sendHaveMessage(@NonNull QuicConnection conn, @NonNull List<Cid> haves) {
        sendHaves(conn, haves, (bsm) -> receiveMessage(conn, bsm));
    }


    private void sendHaves(@NonNull QuicConnection conn, @NonNull List<Cid> haves,
                           @NonNull Consumer<BitSwapMessage> consumer) {
        if (haves.size() == 0) {
            return;
        }

        int priority = Integer.MAX_VALUE;

        BitSwapMessage message = BitSwapMessage.New(false);

        for (Cid c : haves) {

            // Broadcast wants are sent as want-have
            MessageOuterClass.Message.Wantlist.WantType wantType =
                    MessageOuterClass.Message.Wantlist.WantType.Have;

            message.AddEntry(c, priority, wantType, false);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        writeMessage(conn, message, consumer, IPFS.PRIORITY_URGENT);


    }

    void sendWantsMessage(@NonNull QuicConnection conn, @NonNull List<Cid> wants) {
        sendWants(conn, wants, (bsm) -> receiveMessage(conn, bsm));
    }


    public void sendWants(@NonNull QuicConnection conn, @NonNull List<Cid> wants,
                          @NonNull Consumer<BitSwapMessage> consumer) {

        if (wants.size() == 0) {
            return;
        }
        BitSwapMessage message = BitSwapMessage.New(false);

        int priority = Integer.MAX_VALUE;

        for (Cid c : wants) {

            message.AddEntry(c, priority,
                    MessageOuterClass.Message.Wantlist.WantType.Block, true);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        writeMessage(conn, message, consumer, IPFS.PRIORITY_URGENT);

    }

    public void writeMessage(@NonNull QuicConnection conn,
                             @NonNull BitSwapMessage message,
                             @NonNull Consumer<BitSwapMessage> consumer,
                             short priority) {

        if (IPFS.BITSWAP_REQUEST_ACTIVE) {
            boolean success = false;

            long time = System.currentTimeMillis();

            try {
                QuicStream quicStream = conn.createStream(true,
                        IPFS.BITSWAP_IDLE_TIMEOUT, TimeUnit.SECONDS);


                OutputStream outputStream = quicStream.getOutputStream();

                // TODO streamChannel.updatePriority(new QuicStreamPriority(priority, false));

                outputStream.write(DataHandler.writeToken(
                        IPFS.STREAM_PROTOCOL, IPFS.BITSWAP_PROTOCOL));
                outputStream.write(DataHandler.encode(message.ToProtoV1()));
                outputStream.close();

                InputStream inputStream = quicStream.getInputStream(
                        TimeUnit.SECONDS.toMillis(IPFS.BITSWAP_IDLE_TIMEOUT));

                DataHandler.reading(inputStream, new HashSet<>(Arrays.asList(
                        IPFS.STREAM_PROTOCOL, IPFS.RELAY_PROTOCOL,
                        IPFS.BITSWAP_PROTOCOL)), (data) -> {
                    try {
                        LogUtils.debug(TAG, "Data " + data.length);
                        consumer.accept(BitSwapMessage.newMessageFromProto(
                                MessageOuterClass.Message.parseFrom(data)));
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                }, IPFS.MESSAGE_SIZE_MAX);


                success = true;
            } catch (Throwable throwable) {
                LogUtils.error(TAG, "" + throwable.getMessage());
            } finally {
                LogUtils.debug(TAG, "Send took " + success + " " +
                        +(System.currentTimeMillis() - time));
            }
        }

    }

}

