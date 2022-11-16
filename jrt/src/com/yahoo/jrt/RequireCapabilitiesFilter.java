// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.tls.Capability;
import com.yahoo.security.tls.CapabilitySet;
import com.yahoo.security.tls.MissingCapabilitiesException;

/**
 * @author bjorncs
 */
public class RequireCapabilitiesFilter implements RequestAccessFilter {

    private final CapabilitySet requiredCapabilities;

    public RequireCapabilitiesFilter(CapabilitySet requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities;
    }

    public RequireCapabilitiesFilter(Capability... requiredCapabilities) {
        this(CapabilitySet.from(requiredCapabilities));
    }

    @Override
    public boolean allow(Request r) {
        try {
            r.target().connectionAuthContext()
                    .verifyCapabilities(requiredCapabilities, "RPC", r.methodName(), r.target().peerSpec().toString());
            return true;
        } catch (MissingCapabilitiesException e) {
            return false;
        }
    }

}
