// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.ApplicationId;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Generates an endpoint certificate for an application instance.
 *
 * @author andreer
 */
public interface EndpointCertificateProvider  {

    EndpointCertificateMetadata requestCaSignedCertificate(ApplicationId applicationId, List<String> dnsNames, Optional<EndpointCertificateMetadata> currentMetadata);

    List<EndpointCertificateRequestMetadata> listCertificates();

    void deleteCertificate(ApplicationId applicationId, String requestId);

    EndpointCertificateDetails certificateDetails(String requestId);
}
