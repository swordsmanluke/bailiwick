package threads.lite.push;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicStream;

import java.util.concurrent.TimeUnit;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.utils.DataHandler;

public class PushService {
    private static final String TAG = PushService.class.getSimpleName();

    public static void notify(@NonNull QuicConnection conn, @NonNull String content) throws Exception {
        long time = System.currentTimeMillis();


        try {
            QuicStream streamChannel = conn.createStream(true,
                    IPFS.CREATE_STREAM_TIMEOUT, TimeUnit.SECONDS);
            PushSend pushSend = new PushSend(streamChannel,
                    IPFS.CONNECT_TIMEOUT, TimeUnit.SECONDS);

            // TODO streamChannel.updatePriority(new QuicStreamPriority(IPFS.PRIORITY_HIGH, false));

            pushSend.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL, IPFS.PUSH_PROTOCOL));
            pushSend.writeAndFlush(DataHandler.encode(content.getBytes()));
            pushSend.closeOutputStream();
        } finally {
            LogUtils.debug(TAG, "Send took " + (System.currentTimeMillis() - time));
        }
    }
}
