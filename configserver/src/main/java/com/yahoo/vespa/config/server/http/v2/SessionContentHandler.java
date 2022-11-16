// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import ai.vespa.http.HttpURL.Path;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.ContentHandler;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.http.v2.request.SessionContentRequestV2;

/**
 * A handler that will return content or content status for files or directories
 * in the session's application package
 *
 * @author Ulf Lilleengen
 */
public class SessionContentHandler extends SessionHandler {

    private final ContentHandler contentHandler = new ContentHandler();

    @Inject
    public SessionContentHandler(Context ctx, ApplicationRepository applicationRepository) {
        super(ctx, applicationRepository);
    }

    @Override
    public HttpResponse handleGET(HttpRequest request) {
        return contentHandler.get(getContentRequest(request));
    }

    @Override
    public HttpResponse handlePUT(HttpRequest request) {
        return contentHandler.put(getContentRequest(request));
    }

    @Override
    public HttpResponse handleDELETE(HttpRequest request) {
        return contentHandler.delete(getContentRequest(request));
    }

    private void validateRequest(TenantName tenantName) {
        Utils.checkThatTenantExists(applicationRepository.tenantRepository(), tenantName);
    }

    private SessionContentRequestV2 getContentRequest(HttpRequest request) {
        TenantName tenantName = Utils.getTenantNameFromSessionRequest(request);
        validateRequest(tenantName);
        long sessionId = getSessionIdV2(request);
        Path contentPath = SessionContentRequestV2.getContentPath(request);
        ApplicationFile applicationFile =
                applicationRepository.getApplicationFileFromSession(tenantName,
                                                                    sessionId,
                                                                    contentPath,
                                                                    ContentRequest.getApplicationFileMode(request.getMethod()));
        return new SessionContentRequestV2(request, sessionId, tenantName, contentPath, applicationFile);
    }

}
