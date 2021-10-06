package threads.lite.push;

import androidx.annotation.NonNull;

import net.luminis.quic.QuicConnection;

public interface Push {
    void push(@NonNull QuicConnection connection, @NonNull String content);
}
