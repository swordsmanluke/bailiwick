/*
 * Copyright © 2020, 2021 Peter Doornbosch
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
package net.luminis.quic.cid;

import net.luminis.quic.log.Logger;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public abstract class ConnectionIdRegistry {

    public static final int DEFAULT_CID_LENGTH = 8;
    protected final Logger log;
    private final Random random = new Random();
    private final int connectionIdLength;
    protected Map<Integer, ConnectionIdInfo> connectionIds = new ConcurrentHashMap<>();
    protected volatile byte[] currentConnectionId;

    public ConnectionIdRegistry(Logger log) {
        this(DEFAULT_CID_LENGTH, log);
    }

    public ConnectionIdRegistry(Integer cidLength, Logger logger) {
        connectionIdLength = cidLength != null ? cidLength : DEFAULT_CID_LENGTH;
        this.log = logger;
        currentConnectionId = generateConnectionId();
        connectionIds.put(0, new ConnectionIdInfo(0, currentConnectionId, ConnectionIdStatus.IN_USE));
    }

    public void retireConnectionId(int sequenceNr) {
        if (connectionIds.containsKey(sequenceNr)) {
            connectionIds.get(sequenceNr).setStatus(ConnectionIdStatus.RETIRED);
        }
    }

    public byte[] getCurrent() {
        return currentConnectionId;
    }

    public Map<Integer, ConnectionIdInfo> getAll() {
        return connectionIds;
    }

    protected int currentIndex() {
        return connectionIds.entrySet().stream()
                .filter(entry -> entry.getValue().getConnectionId().equals(currentConnectionId))
                .mapToInt(entry -> entry.getKey())
                .findFirst().getAsInt();
    }

    protected byte[] generateConnectionId() {
        byte[] connectionId = new byte[connectionIdLength];
        random.nextBytes(connectionId);
        return connectionId;
    }

    public int getConnectionIdlength() {
        return connectionIdLength;
    }
}

