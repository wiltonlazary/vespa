// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

import com.yahoo.security.KeyUtils;

import java.security.KeyPair;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;

/**
 * Key encapsulation mechanism (KEM)
 *
 * @author vekterli
 */
public interface Kem {

    record EncapResult(byte[] sharedSecret, byte[] enc) { }

    /**
     * Section 4 Cryptographic Dependencies:
     *
     * "Randomized algorithm to generate an ephemeral, fixed-length symmetric key
     * (the KEM shared secret) and a fixed-length encapsulation of that key that can
     * be decapsulated by the holder of the private key corresponding to <code>pkR</code>"
     */
    EncapResult encap(XECPublicKey pkR);

    /**
     * Section 4 Cryptographic Dependencies:
     *
     * "Deterministic algorithm using the private key <code>skR</code> to recover the
     * ephemeral symmetric key (the KEM shared secret) from its encapsulated
     * representation <code>enc</code>."
     */
    byte[] decap(byte[] enc, XECPrivateKey skR);

    /** The length in bytes of a KEM shared secret produced by this KEM. */
    short nSecret();
    /** The length in bytes of an encapsulated key produced by this KEM. */
    short nEnc();
    /** The length in bytes of an encoded public key for this KEM. */
    short nPk();
    /** The length in bytes of an encoded private key for this KEM. */
    short nSk();
    /** Predefined KEM ID, as given in RFC 9180 section 7.1 */
    short kemId();

    /**
     * @return a <code>HKEM(X25519, HKDF-SHA256)</code> instance that generates new ephemeral X25519
     *         key pairs from a secure random source per {@link #encap(XECPublicKey)} invocation.
     */
    static Kem dHKemX25519HkdfSha256() {
        return new DHKemX25519HkdfSha256(KeyUtils::generateX25519KeyPair);
    }

    record UnsafeDeterminsticKeyPairOnlyUsedByTesting(KeyPair keyPair) {}

    /**
     * Returns an unsafe test KEM that returns a single fixed, deterministic key pair.
     *
     * As the name implies, this must only ever be used in the context of testing. If anyone tries
     * to be clever and use this anywhere else, I will find them and bite them in the ankles!
     */
    static Kem dHKemX25519HkdfSha256(UnsafeDeterminsticKeyPairOnlyUsedByTesting testingKP) {
        return new DHKemX25519HkdfSha256(() -> testingKP.keyPair);
    }

}
