package threads.lite.dht;

import androidx.annotation.NonNull;

import net.luminis.quic.stream.QuicStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import threads.lite.IPFS;
import threads.lite.core.DataLimitIssue;
import threads.lite.core.ProtocolIssue;
import threads.lite.utils.DataHandler;

public class KadDhtSend {

    private final InputStream inputStream;


    public KadDhtSend(@NonNull QuicStream quicStream, long readTimeout, TimeUnit unit) {
        this.inputStream = quicStream.getInputStream(unit.toMillis(readTimeout));

    }

    public void reading() throws IOException, ProtocolIssue, DataLimitIssue {
        DataHandler reader = new DataHandler(new HashSet<>(
                Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.DHT_PROTOCOL)
        ), IPFS.PROTOCOL_READER_LIMIT);
        byte[] buf = new byte[4096];

        int length;

        while ((length = inputStream.read(buf, 0, 4096)) > 0) {
            reader.load(Arrays.copyOfRange(buf, 0, length));
            if (reader.isDone()) {
                if (reader.getTokens().contains(IPFS.DHT_PROTOCOL)) {
                    return;
                }
            }
        }
    }
}
