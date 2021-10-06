package net.luminis.quic.stream;

import java.io.OutputStream;

public abstract class QuicOutputStream extends OutputStream {
    public abstract boolean isClosed();
}
