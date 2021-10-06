package threads.lite.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.MessageLite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Charsets;
import threads.lite.cid.Multihash;
import threads.lite.core.DataLimitIssue;
import threads.lite.core.ProtocolIssue;

public class DataHandler {
    private static final String TAG = DataHandler.class.getSimpleName();
    private final List<String> tokens = new ArrayList<>();
    private final Set<String> expected;
    private final int maxLength;
    private ByteArrayOutputStream temp = new ByteArrayOutputStream();
    private boolean isDone = false;
    private byte[] message = null;
    private int expectedLength;

    public DataHandler(Set<String> expected, int maxLength) {
        this.expected = expected;
        this.expectedLength = 0;
        this.maxLength = maxLength;
    }


    public static void reading(@NonNull InputStream inputStream,
                               @NonNull Set<String> tokens,
                               @NonNull Consumer<byte[]> consumer,
                               int maxSize)
            throws DataLimitIssue, IOException, ProtocolIssue {
        DataHandler reader = new DataHandler(tokens, maxSize);

        byte[] buf = new byte[4096];

        int length;

        while ((length = inputStream.read(buf, 0, 4096)) > 0) {
            reader.load(Arrays.copyOfRange(buf, 0, length));
            if (reader.isDone()) {
                byte[] message = reader.getMessage();
                if (message != null) {
                    consumer.accept(message);
                }
            }
        }
    }

    private static int copy(InputStream source, OutputStream sink, int length) throws IOException {
        int nread = 0;
        byte[] buf = new byte[length];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
            if (nread == length) {
                break;
            }
        }
        return nread;
    }

    private static int copy(InputStream source, OutputStream sink) throws IOException {
        int nread = 0;
        byte[] buf = new byte[4096];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }


    public static byte[] encode(@NonNull MessageLite message) {
        return encode(message.toByteArray());
    }

    public static byte[] encode(@NonNull byte[] data) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            Multihash.putUvarint(buf, data.length);
            buf.write(data);
            return buf.toByteArray();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static byte[] writeToken(String... tokens) {

        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            for (String token : tokens) {
                byte[] data = token.getBytes(Charsets.UTF_8);
                Multihash.putUvarint(buf, data.length + 1);
                buf.write(data);
                buf.write('\n');
            }
            return buf.toByteArray();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw new RuntimeException(throwable);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "DataHandler{" +
                "tokens=" + tokens +
                ", expected=" + expected +
                ", maxLength=" + maxLength +
                ", temp=" + temp +
                ", isDone=" + isDone +
                ", message=" + Arrays.toString(message) +
                ", expectedLength=" + expectedLength +
                '}';
    }

    public int hasRead() {
        return temp.size();
    }

    public byte[] getMessage() {
        return message;
    }

    public boolean isDone() {
        return isDone;
    }


    public List<String> getTokens() {
        return tokens;
    }

    public void load(@NonNull byte[] data)
            throws IOException, ProtocolIssue, DataLimitIssue {


        temp.write(data);

        // LogUtils.error(TAG, "expected length " + expectedLength + " temp " + temp.size());

        // shortcut
        if (temp.size() < expectedLength) {
            // no reading required
            return;
        }


        try (InputStream inputStream = new ByteArrayInputStream(temp.toByteArray())) {
            expectedLength = (int) Multihash.readVarint(inputStream);
            if (expectedLength > maxLength) {
                LogUtils.error(TAG, "expected length " + expectedLength + " max length " + maxLength);
                throw new DataLimitIssue("expected length " + expectedLength + " max length " + maxLength);
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int read = copy(inputStream, outputStream, expectedLength);
            if (read == expectedLength) {
                // token found
                byte[] tokenData = outputStream.toByteArray();
                // expected to be for a token
                if (tokenData[0] == '/' && tokenData[read - 1] == '\n') {
                    String token = new String(tokenData, Charsets.UTF_8);
                    token = token.substring(0, read - 1);
                    if (!expected.contains(token)) {
                        LogUtils.debug(TAG, "not expected token " + token);
                        throw new ProtocolIssue("not handled token " + token);
                    }
                    if (!tokens.contains(token)) {
                        tokens.add(token);
                    }
                    //LogUtils.error(TAG, "token  " + token);
                } else if (tokenData[0] == 'n' && tokenData[1] == 'a' && tokenData[read - 1] == '\n') {
                    LogUtils.error(TAG, "na token");
                    throw new ProtocolIssue("na token");
                } else if (tokenData[0] == 'l' && tokenData[1] == 's' && tokenData[read - 1] == '\n') {
                    LogUtils.error(TAG, "ls token");
                    if (!tokens.contains(IPFS.LS)) {
                        tokens.add(IPFS.LS);
                    }
                } else {
                    //LogUtils.error(TAG, "token ??? " + new String(tokenData));
                    message = tokenData;
                }
                // check if still data to read
                ByteArrayOutputStream rest = new ByteArrayOutputStream();
                int restRead = copy(inputStream, rest);
                if (restRead == 0) {
                    temp.reset();
                    expectedLength = 0;
                    isDone = true;
                } else {
                    DataHandler sub = new DataHandler(expected, maxLength);
                    sub.load(rest.toByteArray());
                    this.merge(sub);
                }
            }
        }

    }

    public void clear() {
        isDone = false;
        expectedLength = 0;
        message = null;
        tokens.clear();
        try {
            temp.close();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private void merge(DataHandler dataReader) throws IOException {
        this.expectedLength = dataReader.expectedLength;
        this.isDone = dataReader.isDone;
        this.tokens.addAll(dataReader.tokens);
        this.message = dataReader.message;
        this.temp.close();
        this.temp = dataReader.temp;
    }

    public int expectedBytes() {
        return expectedLength;

    }

    @Nullable
    public String getProtocol() {
        int last = tokens.size() - 1;
        if (last >= 0) {
            return tokens.get(last);
        }
        return null;
    }
}
