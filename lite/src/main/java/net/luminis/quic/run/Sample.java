/*
 * Copyright © 2019, 2020 Peter Doornbosch
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

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;


/**
 * Sample with the smallest / simplest Java program to setup and use a QUIC connection,
 * assuming the server implements HTTP 0.9 protocol.
 * <p>
 * Retrieves "/" resource and safes content to file.
 * Usage: expects one argument: the address of the server, e.g. http://quicserver:4433
 */
public class Sample {

    public static void main(String[] args) throws Exception {

        // If you want to see what happens under the hood, use a logger like this and add to builder with .logger(log)
        SysOutLogger log = new SysOutLogger();
        log.logPackets(true);
        log.logInfo(true);


        QuicClientConnectionImpl.Builder builder = QuicClientConnectionImpl.newBuilder();
        QuicClientConnectionImpl connection =
                builder.version(Version.IETF_draft_32)
                        .uri(new URI(args[0]))
                        .build();

        connection.connect(10_000);

        QuicStream stream = connection.createStream(true);

        BufferedOutputStream outputStream = new BufferedOutputStream(stream.getOutputStream());
        outputStream.write("GET / \r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();

        long transferred = IOUtils.copy(stream.getInputStream(), new FileOutputStream("kwik_client_output"));

        connection.close();

        System.out.println("Received " + transferred + " bytes.");
    }
}
