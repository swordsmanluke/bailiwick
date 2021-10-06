/*
 * Copyright © 2019, 2020, 2021 Peter Doornbosch
 *
 * This file is part of Agent15, an implementation of TLS 1.3 in Java.
 *
 * Agent15 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Agent15 is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.tls.handshake;

import net.luminis.tls.TlsConstants;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class TlsServerEngineFactory {

    private final List<X509Certificate> serverCertificates;
    private final PrivateKey certificateKey;

    public TlsServerEngineFactory(InputStream certificateFile, InputStream certificateKeyFile) throws IOException, CertificateException, InvalidKeySpecException {
        this.serverCertificates = readCertificates(certificateFile);
        this.certificateKey = readPrivateKey(certificateKeyFile);
    }

    private static List<X509Certificate> readCertificates(InputStream file) throws IOException, CertificateException {
        byte[] bytes = IOUtils.toByteArray(file);
        String fileContent = new String(bytes, Charset.defaultCharset());
        String[] chunks = fileContent.split("-----END CERTIFICATE-----\n");

        List<X509Certificate> certs = new ArrayList<>();

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < chunks.length; i++) {
            if (chunks[i].startsWith("-----BEGIN CERTIFICATE-----")) {
                String encodedCertificate = chunks[i]
                        .replace("-----BEGIN CERTIFICATE-----", "")
                        .replaceAll(System.lineSeparator(), "")
                        .replace("-----END CERTIFICATE-----", "");
                Certificate certificate = certificateFactory.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(encodedCertificate)));
                certs.add((X509Certificate) certificate);
            }
        }

        return certs;
    }

    public TlsServerEngine createServerEngine(ServerMessageSender serverMessageSender, TlsStatusEventHandler tlsStatusHandler) {
        TlsServerEngine tlsServerEngine = new TlsServerEngine(serverCertificates, certificateKey, serverMessageSender, tlsStatusHandler);
        tlsServerEngine.addSupportedCiphers(Collections.singletonList(TlsConstants.CipherSuite.TLS_AES_128_GCM_SHA256));
        return tlsServerEngine;
    }

    private RSAPrivateKey readPrivateKey(InputStream file) throws IOException, InvalidKeySpecException {
        byte[] bytes = IOUtils.toByteArray(file);
        String key = new String(bytes, Charset.defaultCharset());

        String privateKeyPEM = key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll(System.lineSeparator(), "")
                .replace("-----END PRIVATE KEY-----", "");
        byte[] encoded = Base64.getMimeDecoder().decode(privateKeyPEM);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Missing key algorithm RSA");
        }
    }
}
