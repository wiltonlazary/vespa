// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mortent
 * @author bjorncs
 */
@SuppressWarnings("unused") //  Injected component
public class CkmsKeyProvider implements KeyProvider {

    private final SecretStore secretStore;
    private final String secretName;
    private final Map<Integer, KeyPair> secrets;

    @Inject
    public CkmsKeyProvider(SecretStore secretStore,
                           Zone zone,
                           AthenzProviderServiceConfig config) {
        this.secretStore = secretStore;
        this.secretName = config.secretName();
        this.secrets = new HashMap<>();
    }

    @Override
    public PrivateKey getPrivateKey(int version) {
        return getKeyPair(version).getPrivate();
    }

    @Override
    public PublicKey getPublicKey(int version) {
        return getKeyPair(version).getPublic();
    }

    @Override
    public KeyPair getKeyPair(int version) {
        synchronized (secrets) {
            KeyPair keyPair = secrets.get(version);
            if (keyPair == null) {
                keyPair = readKeyPair(version);
                secrets.put(version, keyPair);
            }
            return keyPair;
        }
    }

    private KeyPair readKeyPair(int version) {
        PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(secretStore.getSecret(secretName, version));
        PublicKey publicKey = KeyUtils.extractPublicKey(privateKey);
        return new KeyPair(publicKey, privateKey);
    }
}
