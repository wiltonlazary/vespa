// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.net.URI;
/**
 * @author bjorncs
 */
public interface AthenzIdentity {
    AthenzDomain getDomain();
    String getName();
    default URI spiffeUri() {
        return URI.create("spiffe://%s/sa/%s".formatted(getDomainName(), getName()));
    }
    default String getFullName() {
        return getDomain().getName() + "." + getName();
    }
    default String getDomainName() { return getDomain().getName(); }
}
