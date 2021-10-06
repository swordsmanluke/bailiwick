/*
 * Copyright © 2019, 2020, 2021 Peter Doornbosch
 *
 * This file is part of Kwik, an implementation of the QUIC protocol in Java.
 *
 * Kwik is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Kwik is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.quic.stream;


import static net.luminis.quic.EncryptionLevel.App;

import net.luminis.quic.ConnectionIssue;
import net.luminis.quic.EncryptionLevel;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicConnectionImpl;
import net.luminis.quic.Version;
import net.luminis.quic.frame.MaxStreamDataFrame;
import net.luminis.quic.frame.QuicFrame;
import net.luminis.quic.frame.StreamFrame;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.NullLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import threads.lite.IPFS;
import threads.lite.LogUtils;


public class QuicStream extends BaseStream {
    protected static final float receiverMaxDataIncrementFactor = 0.10f;
    private static final String TAG = QuicStream.class.getSimpleName();
    protected static long waitForNextFrameTimeout = Long.MAX_VALUE;
    protected final Version quicVersion;
    protected final int streamId;
    protected final QuicConnectionImpl connection;
    protected final FlowControl flowController;
    protected final Logger log;
    private final StreamOutputStream outputStream;
    private final long receiverMaxDataIncrement;
    private final Object addMonitor = new Object();
    private StreamInputStream inputStream;
    private volatile boolean aborted;
    private volatile Thread blocking;
    private long receiverFlowControlLimit;
    private long lastCommunicatedMaxData;
    private volatile int lastOffset = -1;
    private int sendBufferSize = IPFS.CHUNK_SIZE + 1024; // 1024 is puffer


    public QuicStream(int streamId, QuicConnectionImpl connection, FlowControl flowController) {
        this(Version.getDefault(), streamId, connection, flowController, new NullLogger());
    }

    public QuicStream(int streamId, QuicConnectionImpl connection, FlowControl flowController, Logger log) {
        this(Version.getDefault(), streamId, connection, flowController, log);
    }

    public QuicStream(Version quicVersion, int streamId, QuicConnectionImpl connection, FlowControl flowController, Logger log) {
        this(quicVersion, streamId, connection, flowController, log, null);
    }

    QuicStream(Version quicVersion, int streamId, QuicConnectionImpl connection, FlowControl flowController, Logger log, Integer sendBufferSize) {
        this.quicVersion = quicVersion;
        this.streamId = streamId;
        this.connection = connection;
        this.flowController = flowController;
        if (sendBufferSize != null && sendBufferSize > 0) {
            this.sendBufferSize = sendBufferSize;
        }
        this.log = log;

        outputStream = createStreamOutputStream();

        flowController.streamOpened(this);
        receiverFlowControlLimit = connection.getInitialMaxStreamData();
        lastCommunicatedMaxData = receiverFlowControlLimit;
        receiverMaxDataIncrement = (long) (receiverFlowControlLimit * receiverMaxDataIncrementFactor);
    }

    public QuicConnection getConnection() {
        return connection;
    }

    public InputStream getInputStream() {
        return getInputStream(waitForNextFrameTimeout);
    }

    public InputStream getInputStream(long readTimeout) {
        if (inputStream == null) {
            inputStream = new StreamInputStream(readTimeout);
        }
        return inputStream;
    }

    public QuicOutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Adds a newly received frame to the stream.
     * <p>
     * This method is intentionally package-protected, as it should only be called by the (Stream)Packet processor.
     *
     * @param frame
     */
    void add(StreamFrame frame) {
        synchronized (addMonitor) {
            super.add(frame);
            if (frame.isFinal()) {
                lastOffset = frame.getUpToOffset();
            }
            addMonitor.notifyAll();
        }
    }

    @Override
    protected boolean isStreamEnd(int offset) {
        return lastOffset >= 0 && offset >= lastOffset;
    }

    public int getStreamId() {
        return streamId;
    }

    public boolean isUnidirectional() {
        // https://tools.ietf.org/html/draft-ietf-quic-transport-23#section-2.1
        // "The second least significant bit (0x2) of the stream ID distinguishes
        //   between bidirectional streams (with the bit set to 0) and
        //   unidirectional streams (with the bit set to 1)."
        return (streamId & 0x0002) == 0x0002;
    }

    public boolean isClientInitiatedBidirectional() {
        // "Client-initiated streams have even-numbered stream IDs (with the bit set to 0)"
        return (streamId & 0x0003) == 0x0000;
    }

    public boolean isServerInitiatedBidirectional() {
        // "server-initiated streams have odd-numbered stream IDs"
        return (streamId & 0x0003) == 0x0001;
    }


    @Override
    public String toString() {
        return "Stream " + streamId;
    }

    protected StreamOutputStream createStreamOutputStream() {
        return new StreamOutputStream();
    }

    /**
     * Resets the output stream so data can again be send from the start of the stream (offset 0). Note that in such
     * cases the caller must (again) provide the data to be sent.
     */
    protected void resetOutputStream() {
        // TODO: this is currently not thread safe, see comment in EarlyDataStream how to fix.
        outputStream.restart();
    }

    void abort() {
        aborted = true;
        if (blocking != null) {
            blocking.interrupt();
        }
    }

    protected class StreamInputStream extends InputStream {
        private final long readTimeout;

        public StreamInputStream(long readTimeout) {
            this.readTimeout = readTimeout;
        }
        /*
        @Override
        public void close() throws IOException {
            abort();
        }*/

        @Override
        public int available() throws IOException {
            return Integer.max(0, QuicStream.this.bytesAvailable());
        }

        // InputStream.read() contract:
        // - The value byte is returned as an int in the range 0 to 255.
        // - If no byte is available because the end of the stream has been reached, the value -1 is returned.
        // - This method blocks until input data is available, the end of the stream is detected, or an exception is thrown.
        @Override
        public int read() throws IOException {
            byte[] data = new byte[1];
            int bytesRead = read(data, 0, 1);
            if (bytesRead == 1) {
                return data[0] & 0xff;
            } else if (bytesRead < 0) {
                // End of stream
                return -1;
            } else {
                // Impossible
                throw new RuntimeException();
            }
        }

        // InputStream.read() contract:
        // - An attempt is made to read the requested number of bytes, but a smaller number may be read.
        // - This method blocks until input data is available, end of file is detected, or an exception is thrown.
        // - If requested number of bytes is greater than zero, an attempt is done to read at least one byte.
        // - If no byte is available because the stream is at end of file, the value -1 is returned;
        //   otherwise, at least one byte is read and stored into the given byte array.
        @Override
        public int read(byte[] buffer, int offset, int len) throws IOException {
            Instant readAttemptStarted = Instant.now();
            long waitTimeout = readTimeout;
            while (true) {

                if (aborted) {
                    throw new ConnectionIssue("Connection aborted");
                }

                synchronized (addMonitor) {
                    try {
                        blocking = Thread.currentThread();

                        int bytesRead = QuicStream.this.read(ByteBuffer.wrap(buffer, offset, len));
                        if (bytesRead > 0) {
                            updateAllowedFlowControl(bytesRead);
                            return bytesRead;
                        } else if (bytesRead < 0) {
                            // End of stream
                            return -1;
                        }

                        // Nothing read: block until bytes can be read, read timeout or abort
                        try {
                            addMonitor.wait(waitTimeout);
                        } catch (InterruptedException e) {
                            if (aborted) {
                                throw new ConnectionIssue("Connection aborted");
                            }
                        }
                    } finally {
                        blocking = null;
                    }
                }

                if (bytesAvailable() <= 0) {
                    long waited = Duration.between(readAttemptStarted, Instant.now()).toMillis();
                    if (waited > readTimeout) {
                        long readOffset = readOffset();
                        LogUtils.verbose(TAG, "Read timeout on stream " + streamId + "; read up to " + readOffset);
                        throw new ConnectionIssue("Read timeout on stream " + streamId + "; read up to " + readOffset);
                    } else {
                        waitTimeout = Long.max(1, readTimeout - waited);
                    }
                }
            }
        }

        private void updateAllowedFlowControl(int bytesRead) {
            // Slide flow control window forward (with as much bytes as are read)
            receiverFlowControlLimit += bytesRead;
            connection.updateConnectionFlowControl(bytesRead);
            // Avoid sending flow control updates with every single read; check diff with last send max data
            if (receiverFlowControlLimit - lastCommunicatedMaxData > receiverMaxDataIncrement) {
                connection.send(new MaxStreamDataFrame(streamId, receiverFlowControlLimit), this::retransmitMaxData);
                lastCommunicatedMaxData = receiverFlowControlLimit;
            }
        }

        private void retransmitMaxData(QuicFrame lostFrame) {
            connection.send(new MaxStreamDataFrame(streamId, receiverFlowControlLimit), this::retransmitMaxData);
            log.recovery("Retransmitted max stream data, because lost frame " + lostFrame);
        }
    }

    protected class StreamOutputStream extends QuicOutputStream implements FlowControlUpdateListener {

        // Minimum stream frame size: frame type (1), stream id (1..8), offset (1..8), length (1..2), data (1...)
        // Note that in practice stream id and offset will seldom / never occupy 8 bytes, so the minimum leaves more room for data.
        private static final int MIN_FRAME_SIZE = 1 + 8 + 8 + 2 + 1;

        private final ByteBuffer END_OF_STREAM_MARKER = ByteBuffer.allocate(0);
        private final Object lock = new Object();
        private final int maxBufferSize;
        private final AtomicInteger bufferedBytes;
        private final ReentrantLock bufferLock;
        private final Condition notFull;
        // Send queue contains stream bytes to send in order. The position of the first byte buffer in the queue determines the next byte(s) to send.
        private final Queue<ByteBuffer> sendQueue = new ConcurrentLinkedDeque<>();
        // Current offset is the offset of the next byte in the stream that will be sent.
        // Thread safety: only used by sender thread, so no synchronization needed.
        private int currentOffset;
        // Closed indicates whether the OutputStream is closed, meaning that no more bytes can be written by caller.
        // Thread safety: only use by caller
        private boolean closed;
        // Send request queued indicates whether a request to send a stream frame is queued with the sender. Is used to avoid multiple requests being queued.
        // Thread safety: read/set by caller and by sender thread, so must be synchronized; guarded by lock
        private volatile boolean sendRequestQueued;

        StreamOutputStream() {
            maxBufferSize = sendBufferSize;
            bufferedBytes = new AtomicInteger();
            bufferLock = new ReentrantLock();
            notFull = bufferLock.newCondition();

            flowController.register(QuicStream.this, this);
        }

        @Override
        public void write(byte[] data) throws IOException {
            write(data, 0, data.length);
        }

        @Override
        public void write(byte[] data, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("already closed");
            }

            if (len > maxBufferSize) {
                // Buffering all would break the contract (because this method copies _all_ data) but splitting and
                // writing smaller chunks (and waiting for each individual chunk to be buffered successfully) does not.
                int halfBuffersize = maxBufferSize / 2;
                int times = len / halfBuffersize;
                for (int i = 0; i < times; i++) {
                    // Each individual write will probably block, but by splitting the writes in half buffer sizes
                    // avoids that the buffer needs to be emptied completely before a new block can be added (which
                    // could have severed negative impact on performance as the sender might have to wait for the caller
                    // to fill the buffer again).
                    write(data, off + i * halfBuffersize, halfBuffersize);
                }
                int rest = len % halfBuffersize;
                if (rest > 0) {
                    write(data, off + times * halfBuffersize, rest);
                }
                return;
            }

            int availableBufferSpace = maxBufferSize - bufferedBytes.get();
            if (len > availableBufferSpace) {
                // Wait for enough buffer space to become available
                bufferLock.lock();
                try {
                    while (maxBufferSize - bufferedBytes.get() < len) {
                        try {
                            notFull.await();
                        } catch (InterruptedException e) {
                            throw new InterruptedIOException();
                        }
                    }
                } finally {
                    bufferLock.unlock();
                }
            }

            sendQueue.add(ByteBuffer.wrap(Arrays.copyOfRange(data, off, off + len)));
            bufferedBytes.getAndAdd(len);
            synchronized (lock) {
                if (!sendRequestQueued) {
                    sendRequestQueued = true;
                    connection.send(this::sendFrame, MIN_FRAME_SIZE, getEncryptionLevel(), this::retransmitStreamFrame, true);
                }
            }
        }

        @Override
        public void write(int dataByte) throws IOException {
            // Terrible for performance of course, but that is calling this method anyway.
            byte[] data = new byte[]{(byte) dataByte};
            write(data, 0, 1);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new IOException("already closed");
            }

            // No-op, this implementation sends data as soon as possible.
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                sendQueue.add(END_OF_STREAM_MARKER);
                closed = true;
                synchronized (lock) {
                    if (!sendRequestQueued) {
                        sendRequestQueued = true;
                        connection.send(this::sendFrame, MIN_FRAME_SIZE, getEncryptionLevel(), this::retransmitStreamFrame, true);
                    }
                }
            }
        }

        QuicFrame sendFrame(int maxFrameSize) {
            synchronized (lock) {
                sendRequestQueued = false;
            }

            if (!sendQueue.isEmpty()) {
                int flowControlLimit = (int) (flowController.getFlowControlLimit(QuicStream.this));
                assert (flowControlLimit >= currentOffset);

                int maxBytesToSend = bufferedBytes.get();
                if (flowControlLimit > currentOffset || maxBytesToSend == 0) {
                    int nrOfBytes = 0;
                    StreamFrame dummy = new StreamFrame(quicVersion, streamId, currentOffset, new byte[0], false);
                    maxBytesToSend = Integer.min(maxBytesToSend, maxFrameSize - dummy.getBytes().length - 1);  // Take one byte extra for length field var int
                    int maxAllowedByFlowControl = (int) (flowController.increaseFlowControlLimit(QuicStream.this, currentOffset + maxBytesToSend) - currentOffset);
                    maxBytesToSend = Integer.min(maxAllowedByFlowControl, maxBytesToSend);

                    byte[] dataToSend = new byte[maxBytesToSend];
                    boolean finalFrame = false;
                    while (nrOfBytes < maxBytesToSend && !sendQueue.isEmpty()) {
                        ByteBuffer buffer = sendQueue.peek();
                        int position = nrOfBytes;
                        if (buffer.remaining() <= maxBytesToSend - nrOfBytes) {
                            // All bytes remaining in buffer will fit in stream frame
                            nrOfBytes += buffer.remaining();
                            buffer.get(dataToSend, position, buffer.remaining());
                            sendQueue.poll();
                        } else {
                            // Just part of the buffer will fit in (and will fill up) the stream frame
                            buffer.get(dataToSend, position, maxBytesToSend - nrOfBytes);
                            nrOfBytes = maxBytesToSend;  // Short form of: nrOfBytes += (maxBytesToSend - nrOfBytes)
                        }
                    }
                    if (!sendQueue.isEmpty() && sendQueue.peek() == END_OF_STREAM_MARKER) {
                        finalFrame = true;
                        sendQueue.poll();
                    }
                    if (nrOfBytes == 0 && !finalFrame) {
                        // Nothing to send really
                        return null;
                    }

                    bufferedBytes.getAndAdd(-1 * nrOfBytes);
                    bufferLock.lock();
                    try {
                        notFull.signal();
                    } finally {
                        bufferLock.unlock();
                    }

                    if (nrOfBytes < maxBytesToSend) {
                        // This can happen when not enough data is buffer to fill a stream frame, or length field is 1 byte (instead of 2 that was counted for)
                        dataToSend = Arrays.copyOfRange(dataToSend, 0, nrOfBytes);
                    }
                    StreamFrame streamFrame = new StreamFrame(quicVersion, streamId, currentOffset, dataToSend, finalFrame);
                    currentOffset += nrOfBytes;

                    if (!sendQueue.isEmpty()) {
                        synchronized (lock) {
                            sendRequestQueued = true;
                        }
                        // There is more to send, so queue a new send request.
                        connection.send(this::sendFrame, MIN_FRAME_SIZE, getEncryptionLevel(), this::retransmitStreamFrame, true);
                    }

                    if (streamFrame.isFinal()) {
                        // Done! Retransmissions may follow, but don't need flow control.
                        flowController.unregister(QuicStream.this);
                        flowController.streamClosed(QuicStream.this);
                    }
                    return streamFrame;
                } else {
                    // So flowControlLimit <= currentOffset
                    // TODO: Should send DATA_BLOCKED or STREAM_DATA_BLOCKED.
                    log.info("Stream " + streamId + " is blocked by flow control at " + flowControlLimit + " (note to self: should send DATA_BLOCKED or STREAM_DATA_BLOCKED ;-))");
                }
            }
            return null;
        }

        public void streamNotBlocked(int streamId) {
            // Stream might have been blocked (or it might have filled the flow control window exactly), queue send request
            // and let sendFrame method determine whether there is more to send or not.
            connection.send(this::sendFrame, MIN_FRAME_SIZE, getEncryptionLevel(), this::retransmitStreamFrame, false);
        }

        private void retransmitStreamFrame(QuicFrame frame) {
            connection.send(frame, this::retransmitStreamFrame, true);
            log.recovery("Retransmitted lost stream frame " + frame);
        }

        protected EncryptionLevel getEncryptionLevel() {
            return App;
        }

        private void restart() {
            currentOffset = 0;
            sendQueue.clear();
            sendRequestQueued = false;
        }
    }

}
