// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import java.util.logging.Level;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.http.v2.request.HttpConfigRequests;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.http.HttpConfigRequest;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;

import java.util.Optional;

/**
 * HTTP handler for a getConfig operation
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class HttpGetConfigHandler extends HttpHandler {

    private final TenantRepository tenantRepository;

    @Inject
    public HttpGetConfigHandler(HttpHandler.Context ctx,
                                TenantRepository tenantRepository)
    {
        super(ctx);
        this.tenantRepository = tenantRepository;
    }
    
    @Override
    public HttpResponse handleGET(HttpRequest req) {
        HttpConfigRequest request = HttpConfigRequest.createFromRequestV2(req);       
        RequestHandler requestHandler = HttpConfigRequests.getRequestHandler(tenantRepository, request);
        HttpConfigRequest.validateRequestKey(request.getConfigKey(), requestHandler, request.getApplicationId());
        return HttpConfigResponse.createFromConfig(resolveConfig(request, requestHandler));
    }

    private ConfigResponse resolveConfig(HttpConfigRequest request, RequestHandler requestHandler) {
        log.log(Level.FINE, () -> "nocache=" + request.noCache());
        ConfigResponse config = requestHandler.resolveConfig(request.getApplicationId(), request, Optional.empty());
        if (config == null) HttpConfigRequest.throwModelNotReady();
        return config;
    }
}
