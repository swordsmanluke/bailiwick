package threads.lite.host;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;

import threads.lite.cid.PeerId;


public interface ConnectionHandler {
    void handleConnection(@NonNull QuicConnection connection, @NonNull PeerId peerId);
}
