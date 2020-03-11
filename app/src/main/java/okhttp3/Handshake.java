/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3;

import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import okhttp3.internal.Util;

/**
 * A record of a TLS handshake. For HTTPS clients, the client is <i>local</i> and the remote server
 * is its <i>peer</i>.
 * <p>
 * <p>This value object describes a completed handshake. Use {@link ConnectionSpec} to set policy
 * for new handshakes.
 */
public final class Handshake {
    private final TlsVersion tlsVersion;
    private final CipherSuite cipherSuite;
    private final List<Certificate> peerCertificates;
    private final List<Certificate> localCertificates;

    private Handshake(TlsVersion tlsVersion, CipherSuite cipherSuite,
                      List<Certificate> peerCertificates, List<Certificate> localCertificates) {
        this.tlsVersion = tlsVersion;
        this.cipherSuite = cipherSuite;
        this.peerCertificates = peerCertificates;
        this.localCertificates = localCertificates;
    }

    public static Handshake get(SSLSession session) {
        String cipherSuiteString = session.getCipherSuite();
        if (cipherSuiteString == null) throw new IllegalStateException("cipherSuite == null");
        CipherSuite cipherSuite = CipherSuite.forJavaName(cipherSuiteString);

        String tlsVersionString = session.getProtocol();
        if (tlsVersionString == null) throw new IllegalStateException("tlsVersion == null");
        TlsVersion tlsVersion = TlsVersion.forJavaName(tlsVersionString);

        Certificate[] peerCertificates;
        try {
            peerCertificates = session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException ignored) {
            peerCertificates = null;
        }
        List<Certificate> peerCertificatesList = peerCertificates != null
                ? Util.immutableList(peerCertificates)
                : Collections.<Certificate>emptyList();

        Certificate[] localCertificates = session.getLocalCertificates();
        List<Certificate> localCertificatesList = localCertificates != null
                ? Util.immutableList(localCertificates)
                : Collections.<Certificate>emptyList();

        return new Handshake(tlsVersion, cipherSuite, peerCertificatesList, localCertificatesList);
    }

    public static Handshake get(TlsVersion tlsVersion, CipherSuite cipherSuite,
                                List<Certificate> peerCertificates, List<Certificate> localCertificates) {
        if (tlsVersion == null) throw new NullPointerException("tlsVersion == null");
        if (cipherSuite == null) throw new NullPointerException("cipherSuite == null");
        return new Handshake(tlsVersion, cipherSuite, Util.immutableList(peerCertificates),
                Util.immutableList(localCertificates));
    }

    /**
     * Returns the TLS version used for this connection. This value wasn't tracked prior to OkHttp
     * 3.0. For responses cached by preceding versions this returns {@link TlsVersion#SSL_3_0}.
     */
    public TlsVersion tlsVersion() {
        return tlsVersion;
    }

    /**
     * Returns the cipher suite used for the connection.
     */
    public CipherSuite cipherSuite() {
        return cipherSuite;
    }

    /**
     * Returns a possibly-empty list of certificates that identify the remote peer.
     *
     *
     * Certificate
     *
     * 用于管理各种标识证书的抽象类。
     *   标识证书是主体与公钥的绑定，该公钥
     *   由另一个委托人担保。 （主体表示
     *   实体，如单个用户、组或公司。
     *
     * 此类是具有不同
     * 格式，但重要的常见用途。 例如，不同类型的证书（如 X.509 和 PGP）共享常规证书
     *   功能（如编码和验证）和
     *   某些类型的信息（如公钥）。
     *
     * X.509、PGP 和 SDSI 证书都可以由
     *   对证书类进行子类化，即使它们包含不同的
     *   信息集，它们以不同的方式存储和检索信息。
     */
    public List<Certificate> peerCertificates() {
        return peerCertificates;
    }

    /**
     * Returns the remote peer's principle, or null if that peer is anonymous.
     */
    public @Nullable
    Principal peerPrincipal() {
        return !peerCertificates.isEmpty()
                ? ((X509Certificate) peerCertificates.get(0)).getSubjectX500Principal()
                : null;
    }

    /**
     * Returns a possibly-empty list of certificates that identify this peer.
     */
    public List<Certificate> localCertificates() {
        return localCertificates;
    }

    /**
     * Returns the local principle, or null if this peer is anonymous.
     */
    public @Nullable
    Principal localPrincipal() {
        return !localCertificates.isEmpty()
                ? ((X509Certificate) localCertificates.get(0)).getSubjectX500Principal()
                : null;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof Handshake)) return false;
        Handshake that = (Handshake) other;
        return tlsVersion.equals(that.tlsVersion)
                && cipherSuite.equals(that.cipherSuite)
                && peerCertificates.equals(that.peerCertificates)
                && localCertificates.equals(that.localCertificates);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + tlsVersion.hashCode();
        result = 31 * result + cipherSuite.hashCode();
        result = 31 * result + peerCertificates.hashCode();
        result = 31 * result + localCertificates.hashCode();
        return result;
    }
}
