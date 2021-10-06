package threads.lite.utils;

import threads.lite.format.Reader;

public interface Splitter {
    Reader reader();

    byte[] nextBytes();

    boolean done();
}
