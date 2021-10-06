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
package net.luminis.quic;

import static net.luminis.quic.EncryptionLevel.App;
import static java.time.temporal.ChronoUnit.SECONDS;

import net.luminis.quic.frame.PingFrame;
import net.luminis.quic.send.Sender;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import threads.lite.LogUtils;


public class KeepAliveActor {
    private static final String TAG = KeepAliveActor.class.getSimpleName();
    private final Version quicVersion;
    private final int keepAliveTime;
    private final int peerIdleTimeout;
    private final Sender sender;
    private final Instant started;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final int pingInterval;
    private volatile ScheduledFuture<?> scheduledTask;

    public KeepAliveActor(Version quicVersion, int keepAliveTime, int peerIdleTimeout, Sender sender) {
        this.quicVersion = quicVersion;
        this.keepAliveTime = keepAliveTime;
        this.peerIdleTimeout = peerIdleTimeout;
        this.sender = sender;
        started = Instant.now();
        pingInterval = peerIdleTimeout / 2;
        LogUtils.debug(TAG, "PingInterval " + pingInterval);
        scheduleNextPing();
    }

    private void ping() {
        LogUtils.debug(TAG, "ping");
        sender.send(new PingFrame(quicVersion), App);
        scheduleNextPing();
    }

    private void scheduleNextPing() {
        Instant now = Instant.now();
        if (Duration.between(started, now).compareTo(Duration.of(keepAliveTime - pingInterval, SECONDS)) < 0) {
            scheduledTask = scheduler.schedule(this::ping, pingInterval, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
