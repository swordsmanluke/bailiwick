package net.luminis.quic;

// https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-20
public class QuicError extends Exception {

    public QuicError(String msg) {
        super(msg);
    }
}
