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
import net.luminis.tls.util.ByteUtils;

import java.util.Arrays;


public class SourceConnectionIdRegistry extends ConnectionIdRegistry {
    private static final String TAG = SourceConnectionIdRegistry.class.getSimpleName();
    private int activeConnectionIdLimit;

    public SourceConnectionIdRegistry(Integer cidLength, Logger logger) {
        super(cidLength, logger);
    }

    public ConnectionIdInfo generateNew() {
        int sequenceNr = connectionIds.keySet().stream().max(Integer::compareTo).get() + 1;
        ConnectionIdInfo newCid = new ConnectionIdInfo(sequenceNr, generateConnectionId(), ConnectionIdStatus.NEW);
        connectionIds.put(sequenceNr, newCid);
        return newCid;
    }

    /**
     * Registers a connection id for being used.
     */
    public boolean registerUsedConnectionId(byte[] connectionId) {

        if (!Arrays.equals(currentConnectionId, connectionId)) {
            // Register previous connection id as used
            connectionIds.values().stream()
                    .filter(cid -> Arrays.equals(cid.getConnectionId(), currentConnectionId))
                    .forEach(cid -> cid.setStatus(ConnectionIdStatus.USED));
            currentConnectionId = connectionId;
            // Check if new connection id is newly used
            boolean wasNew = connectionIds.values().stream()
                    .filter(cid -> Arrays.equals(cid.getConnectionId(), currentConnectionId))
                    .anyMatch(cid -> cid.getConnectionIdStatus().equals(ConnectionIdStatus.NEW));
            // Register current connection id as current
            connectionIds.values().stream()
                    .filter(cid -> Arrays.equals(cid.getConnectionId(), currentConnectionId))
                    .forEach(cid -> cid.setStatus(ConnectionIdStatus.IN_USE));
            log.info("Peer has switched to connection id " + ByteUtils.bytesToHex(currentConnectionId));
            return wasNew;
        } else {
            return false;
        }

    }

    public boolean limitReached() {
        return connectionIds.values().stream()
                .filter(cid -> cid.getConnectionIdStatus().active())
                .count() >= activeConnectionIdLimit;
    }

    public void setActiveLimit(int activeConnectionIdLimit) {
        this.activeConnectionIdLimit = activeConnectionIdLimit;
    }
}


