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
package net.luminis.quic.run;

import net.luminis.quic.QuicClientConnectionImpl;
import net.luminis.quic.Version;
import net.luminis.quic.log.SysOutLogger;
import net.luminis.quic.stream.QuicStream;

import java.io.BufferedOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Sends an unlimited stream to server; used for robustness testing only.
 */
public class SendUnlimited {

    public static void main(String[] args) throws Exception {

        // If you want to see what happens under the hood, use a logger like this and add to builder with .logger(log)
        SysOutLogger log = new SysOutLogger();
        log.logPackets(true);
        log.logInfo(true);


        QuicClientConnectionImpl.Builder builder = QuicClientConnectionImpl.newBuilder();
        QuicClientConnectionImpl connection =
                builder.version(Version.IETF_draft_32)
                        .noServerCertificateCheck()
                        .logger(log)
                        .uri(new URI("https://localhost:4433"))
                        .build();

        connection.connect(10_000);

        QuicStream stream = connection.createStream(true);

        BufferedOutputStream outputStream = new BufferedOutputStream(stream.getOutputStream());
        outputStream.write("GET ".getBytes(StandardCharsets.UTF_8));
        while (true) {
            outputStream.write("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8));
        }
    }

}
