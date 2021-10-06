package net.luminis.quic;

import java.io.IOException;

public class ConnectionIssue extends IOException {
    public ConnectionIssue(String msg) {
        super(msg);
    }

    public ConnectionIssue() {
        super();
    }
}
