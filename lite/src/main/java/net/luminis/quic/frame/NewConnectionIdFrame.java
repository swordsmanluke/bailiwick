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
package net.luminis.quic.frame;

import net.luminis.quic.InvalidIntegerEncodingException;
import net.luminis.quic.VariableLengthInteger;
import net.luminis.quic.Version;
import net.luminis.quic.log.Logger;
import net.luminis.quic.packet.QuicPacket;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Random;

public class NewConnectionIdFrame extends QuicFrame {

    private static final Random random = new Random();
    private final Version quicVersion;
    private int sequenceNr;
    private int retirePriorTo;
    private byte[] connectionId;
    private byte[] statelessResetToken;

    public NewConnectionIdFrame(Version quicVersion) {
        this.quicVersion = quicVersion;
    }

    public NewConnectionIdFrame(Version quicVersion, int sequenceNr, int retirePriorTo, byte[] newSourceConnectionId) {
        this.quicVersion = quicVersion;
        this.sequenceNr = sequenceNr;
        this.retirePriorTo = retirePriorTo;
        connectionId = newSourceConnectionId;
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(30);
        buffer.put((byte) 0x18);
        VariableLengthInteger.encode(sequenceNr, buffer);
        VariableLengthInteger.encode(retirePriorTo, buffer);
        buffer.put((byte) connectionId.length);
        buffer.put(connectionId);
        random.ints(16).forEach(i -> buffer.put((byte) i));

        byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return bytes;
    }

    public NewConnectionIdFrame parse(ByteBuffer buffer, Logger log) throws InvalidIntegerEncodingException {
        buffer.get();

        sequenceNr = VariableLengthInteger.parse(buffer);
        retirePriorTo = VariableLengthInteger.parse(buffer);
        int connectionIdLength = buffer.get();
        connectionId = new byte[connectionIdLength];
        buffer.get(connectionId);

        statelessResetToken = new byte[128 / 8];
        buffer.get(statelessResetToken);

        return this;
    }

    @Override
    public String toString() {
        return "NewConnectionIdFrame[" + sequenceNr + "," + retirePriorTo + "]";
    }

    public int getSequenceNr() {
        return sequenceNr;
    }

    public byte[] getConnectionId() {
        return connectionId;
    }

    public int getRetirePriorTo() {
        return retirePriorTo;
    }

    public byte[] getStatelessResetToken() {
        return statelessResetToken;
    }

    @Override
    public void accept(FrameProcessor3 frameProcessor, QuicPacket packet, Instant timeReceived) {
        frameProcessor.process(this, packet, timeReceived);
    }
}
