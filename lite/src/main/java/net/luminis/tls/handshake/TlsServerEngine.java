/*
 * Copyright © 2020, 2021 Peter Doornbosch
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

import static net.luminis.tls.TlsConstants.CipherSuite.TLS_AES_128_GCM_SHA256;
import static net.luminis.tls.TlsConstants.NamedGroup.x25519;
import static net.luminis.tls.TlsConstants.SignatureScheme.rsa_pss_rsae_sha256;

import net.luminis.tls.ProtectionKeysType;
import net.luminis.tls.TlsConstants;
import net.luminis.tls.TlsProtocolException;
import net.luminis.tls.TlsState;
import net.luminis.tls.TranscriptHash;
import net.luminis.tls.alert.DecryptErrorAlert;
import net.luminis.tls.alert.HandshakeFailureAlert;
import net.luminis.tls.alert.IllegalParameterAlert;
import net.luminis.tls.alert.MissingExtensionAlert;
import net.luminis.tls.alert.UnexpectedMessageAlert;
import net.luminis.tls.extension.Extension;
import net.luminis.tls.extension.KeyShareExtension;
import net.luminis.tls.extension.SignatureAlgorithmsExtension;
import net.luminis.tls.extension.SupportedGroupsExtension;
import net.luminis.tls.extension.SupportedVersionsExtension;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TlsServerEngine extends TlsEngine implements ServerMessageProcessor {

    private final ArrayList<TlsConstants.CipherSuite> supportedCiphers;
    private final ArrayList<Extension> extensions;
    private final List<X509Certificate> serverCertificateChain;
    private final PrivateKey certificatePrivateKey;
    private final TranscriptHash transcriptHash;
    private final List<Extension> serverExtensions;
    protected TlsStatusEventHandler statusHandler;
    private ServerMessageSender serverMessageSender;
    private TlsConstants.CipherSuite selectedCipher;
    private X509Certificate clientCertificate;


    public TlsServerEngine(List<X509Certificate> certificates, PrivateKey certificateKey, ServerMessageSender serverMessageSender, TlsStatusEventHandler tlsStatusHandler) {
        this.serverCertificateChain = certificates;
        this.certificatePrivateKey = certificateKey;
        this.serverMessageSender = serverMessageSender;
        this.statusHandler = tlsStatusHandler;
        supportedCiphers = new ArrayList<>();
        supportedCiphers.add(TLS_AES_128_GCM_SHA256);
        extensions = new ArrayList<>();
        serverExtensions = new ArrayList<>();
        transcriptHash = new TranscriptHash(32);
    }

    public TlsServerEngine(X509Certificate serverCertificate, PrivateKey certificateKey, ServerMessageSender serverMessageSender, TlsStatusEventHandler tlsStatusHandler) {
        this(Arrays.asList(serverCertificate), certificateKey, serverMessageSender, tlsStatusHandler);
    }

    @Override
    public void received(CertificateMessage certificateMessage, ProtectionKeysType protectedBy) throws TlsProtocolException {
        if (protectedBy != ProtectionKeysType.Handshake) {
            throw new UnexpectedMessageAlert("incorrect protection level");
        }
        /*if (status != TlsClientEngine.Status.EncryptedExtensionsReceived && status != TlsClientEngine.Status.CertificateRequestReceived) {
            // https://tools.ietf.org/html/rfc8446#section-4.4
            // "TLS generally uses a common set of messages for authentication, key confirmation, and handshake
            //   integrity: Certificate, CertificateVerify, and Finished.  (...)  These three messages are always
            //   sent as the last messages in their handshake flight."
            throw new UnexpectedMessageAlert("unexpected certificate message");
        }*/

        if (certificateMessage.getRequestContext().length > 0) {
            // https://tools.ietf.org/html/rfc8446#section-4.4.2
            // "If this message is in response to a CertificateRequest, the value of certificate_request_context in that
            // message. Otherwise (in the case of server authentication), this field SHALL be zero length."
            throw new IllegalParameterAlert("certificate request context should be zero length");
        }
        if (certificateMessage.getEndEntityCertificate() == null) {
            throw new IllegalParameterAlert("missing certificate");
        }

        clientCertificate = certificateMessage.getEndEntityCertificate();
        //clientCertificateChain = certificateMessage.getCertificateChain();
        transcriptHash.recordServer(certificateMessage);
        // status = TlsClientEngine.Status.CertificateReceived;
    }

    @Override
    public void received(ClientHello clientHello, ProtectionKeysType protectedBy) throws TlsProtocolException, IOException {
        // Find first cipher that server supports
        selectedCipher = clientHello.getCipherSuites().stream()
                .filter(it -> supportedCiphers.contains(it))
                .findFirst()
                // https://tools.ietf.org/html/rfc8446#section-4.1.1
                // "If the server is unable to negotiate a supported set of parameters (...) it MUST abort the handshake
                // with either a "handshake_failure" or "insufficient_security" fatal alert "
                .orElseThrow(() -> new HandshakeFailureAlert("Failed to negotiate a cipher (server only supports " + supportedCiphers.stream().map(c -> c.toString()).collect(Collectors.joining(", ")) + ")"));

        SupportedGroupsExtension supportedGroupsExt = (SupportedGroupsExtension) clientHello.getExtensions().stream()
                .filter(ext -> ext instanceof SupportedGroupsExtension)
                .findFirst()
                .orElseThrow(() -> new MissingExtensionAlert("supported groups extension is required in Client Hello"));

        // This implementation (yet) only supports secp256r1 and x25519
        List<TlsConstants.NamedGroup> serverSupportedGroups = Arrays.asList(TlsConstants.NamedGroup.secp256r1, x25519);
        if (!supportedGroupsExt.getNamedGroups().stream()
                .filter(serverSupportedGroups::contains)
                .findFirst()
                .isPresent()) {
            throw new HandshakeFailureAlert(String.format("Failed to negotiate supported group (server only supports %s)", serverSupportedGroups));
        }

        KeyShareExtension keyShareExtension = (KeyShareExtension) clientHello.getExtensions().stream()
                .filter(ext -> ext instanceof KeyShareExtension)
                .findFirst()
                .orElseThrow(() -> new MissingExtensionAlert("key share extension is required in Client Hello"));

        KeyShareExtension.KeyShareEntry keyShareEntry = keyShareExtension.getKeyShareEntries().stream()
                .filter(entry -> serverSupportedGroups.contains(entry.getNamedGroup()))
                .findFirst()
                .orElseThrow(() -> new IllegalParameterAlert("key share named group no supported (and no HelloRetryRequest support)"));

        SignatureAlgorithmsExtension signatureAlgorithmsExtension = (SignatureAlgorithmsExtension) clientHello.getExtensions().stream()
                .filter(ext -> ext instanceof SignatureAlgorithmsExtension)
                .findFirst()
                .orElseThrow(() -> new MissingExtensionAlert("signature algorithms extension is required in Client Hello"));

        // This implementation (yet) only supports rsa_pss_rsae_sha256 (non compliant, see https://tools.ietf.org/html/rfc8446#section-9.1)
        if (!signatureAlgorithmsExtension.getSignatureAlgorithms().contains(rsa_pss_rsae_sha256)) {
            throw new HandshakeFailureAlert("Failed to negotiate signature algorithm (server only supports rsa_pss_rsae_sha256");
        }

        // So: ClientHello is valid and negotiation was successful, as far as this engine is concerned.
        // Use callback to let context check other prerequisites, for example appropriate ALPN extension
        statusHandler.extensionsReceived(clientHello.getExtensions());

        generateKeys(keyShareEntry.getNamedGroup());

        // Start building TLS state and prepare response
        state = new TlsState(privateKey, transcriptHash);
        transcriptHash.record(clientHello);


        state.computeEarlyTrafficSecret();
        statusHandler.earlySecretsKnown();

        ServerHello serverHello = new ServerHello(selectedCipher, Arrays.asList(
                new SupportedVersionsExtension(TlsConstants.HandshakeType.server_hello),
                new KeyShareExtension(publicKey, keyShareEntry.getNamedGroup(), TlsConstants.HandshakeType.server_hello)
        ));
        // Send server hello back to client
        serverMessageSender.send(serverHello);

        // Update state
        transcriptHash.record(serverHello);
        state.setPeerKey(keyShareEntry.getKey());

        // Compute keys
        state.computeSharedSecret();
        state.computeHandshakeSecrets();
        statusHandler.handshakeSecretsKnown();

        EncryptedExtensions encryptedExtensions = new EncryptedExtensions(serverExtensions);
        serverMessageSender.send(encryptedExtensions);
        transcriptHash.record(encryptedExtensions);

        /* TODO REWI activate again
        CertificateRequestMessage request = new CertificateRequestMessage(
                new SignatureAlgorithmsExtension(rsa_pss_rsae_sha256));
        serverMessageSender.send(request);
        transcriptHash.recordServer(request);*/

        CertificateMessage certificate = new CertificateMessage(serverCertificateChain);
        serverMessageSender.send(certificate);
        transcriptHash.recordServer(certificate);

        // "The content that is covered under the signature is the hash output as described in Section 4.4.1, namely:
        //      Transcript-Hash(Handshake Context, Certificate)
        byte[] hash = transcriptHash.getServerHash(TlsConstants.HandshakeType.certificate);
        byte[] signature = computeSignature(hash, certificatePrivateKey, rsa_pss_rsae_sha256, false);
        CertificateVerifyMessage certificateVerify = new CertificateVerifyMessage(rsa_pss_rsae_sha256, signature);
        serverMessageSender.send(certificateVerify);
        transcriptHash.recordServer(certificateVerify);

        byte[] hmac = computeFinishedVerifyData(transcriptHash.getServerHash(TlsConstants.HandshakeType.certificate_verify), state.getServerHandshakeTrafficSecret());
        FinishedMessage finished = new FinishedMessage(hmac);
        serverMessageSender.send(finished);
        transcriptHash.recordServer(finished);
        state.computeApplicationSecrets();
    }

    @Override
    public void received(FinishedMessage clientFinished, ProtectionKeysType protectedBy) throws TlsProtocolException, IOException {
        if (protectedBy != ProtectionKeysType.Handshake) {
            throw new UnexpectedMessageAlert("incorrect protection level");
        }
        // https://tools.ietf.org/html/rfc8446#section-4.4
        // "   | Mode      | Handshake Context       | Base Key                    |
        //     +-----------+-------------------------+-----------------------------+
        //     | Client    | ClientHello ... later   | client_handshake_traffic_   |
        //     |           | of server               | secret                      |
        //     |           | Finished/EndOfEarlyData |                             |

        byte[] serverHmac = computeFinishedVerifyData(transcriptHash.getServerHash(TlsConstants.HandshakeType.finished), state.getClientHandshakeTrafficSecret());
        // https://tools.ietf.org/html/rfc8446#section-4.4
        // "Recipients of Finished messages MUST verify that the contents are correct and if incorrect MUST terminate the connection with a "decrypt_error" alert."
        // TODO REWI deactivate again
        if (!Arrays.equals(clientFinished.getVerifyData(), serverHmac)) {
            throw new DecryptErrorAlert("incorrect finished message");
        } else {
            statusHandler.handshakeFinished();
        }
        // TODO REWI statusHandler.handshakeFinished();
    }

    public void addSupportedCiphers(List<TlsConstants.CipherSuite> cipherSuites) {
        supportedCiphers.addAll(cipherSuites);
    }

    public void setServerMessageSender(ServerMessageSender serverMessageSender) {
        this.serverMessageSender = serverMessageSender;
    }

    public void setStatusHandler(TlsStatusEventHandler statusHandler) {
        this.statusHandler = statusHandler;
    }

    @Override
    public TlsConstants.CipherSuite getSelectedCipher() {
        return selectedCipher;
    }

    @Override
    public X509Certificate getRemoteCertificate() {
        return clientCertificate;
    }

    public List<Extension> getServerExtensions() {
        return serverExtensions;
    }

    // TODO: remove this
    public TlsState getState() {
        return state;
    }

    public void addServerExtensions(Extension extension) {
        serverExtensions.add(extension);
    }
}

