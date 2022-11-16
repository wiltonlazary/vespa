// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author bjorncs
 */
class JDiscServerConnector extends ServerConnector {

    public static final String REQUEST_ATTRIBUTE = JDiscServerConnector.class.getName();
    private final Metric.Context metricCtx;
    private final ConnectionStatistics statistics;
    private final ConnectorConfig config;
    private final boolean tcpKeepAlive;
    private final boolean tcpNoDelay;
    private final Metric metric;
    private final String connectorName;
    private final int listenPort;

    JDiscServerConnector(ConnectorConfig config, Metric metric, Server server, JettyConnectionLogger connectionLogger,
                         ConnectionMetricAggregator connectionMetricAggregator, ConnectionFactory... factories) {
        super(server, factories);
        this.config = config;
        this.tcpKeepAlive = config.tcpKeepAliveEnabled();
        this.tcpNoDelay = config.tcpNoDelay();
        this.metric = metric;
        this.connectorName = config.name();
        this.listenPort = config.listenPort();
        this.metricCtx = metric.createContext(createConnectorDimensions(listenPort, connectorName, 0));

        this.statistics = new ConnectionStatistics();
        addBean(statistics);
        ConnectorConfig.Throttling throttlingConfig = config.throttling();
        if (throttlingConfig.enabled()) {
            new ConnectionThrottler(this, throttlingConfig).registerWithConnector();
        }
        addBean(connectionLogger);
        addBean(connectionMetricAggregator);
        setPort(config.listenPort());
        setName(config.name());
        setAcceptQueueSize(config.acceptQueueSize());
        setReuseAddress(config.reuseAddress());
        setIdleTimeout((long) (config.idleTimeout() * 1000));
        addBean(HttpCompliance.RFC7230);
    }

    @Override
    protected void configure(final Socket socket) {
        super.configure(socket);
        try {
            socket.setKeepAlive(tcpKeepAlive);
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (SocketException ignored) {
        }
    }

    public ConnectionStatistics getStatistics() {
        return statistics;
    }

    public Metric.Context getConnectorMetricContext() {
        return metricCtx;
    }

    public Metric.Context createRequestMetricContext(HttpServletRequest request, Map<String, String> extraDimensions) {
        String method = request.getMethod();
        String scheme = request.getScheme();
        boolean clientAuthenticated = request.getAttribute(RequestUtils.SERVLET_REQUEST_X509CERT) != null;
        Map<String, Object> dimensions = createConnectorDimensions(listenPort, connectorName, extraDimensions.size() + 5);
        dimensions.put(MetricDefinitions.METHOD_DIMENSION, method);
        dimensions.put(MetricDefinitions.SCHEME_DIMENSION, scheme);
        dimensions.put(MetricDefinitions.CLIENT_AUTHENTICATED_DIMENSION, Boolean.toString(clientAuthenticated));
        dimensions.put(MetricDefinitions.PROTOCOL_DIMENSION, request.getProtocol());
        String serverName = Optional.ofNullable(request.getServerName()).orElse("unknown");
        dimensions.put(MetricDefinitions.REQUEST_SERVER_NAME_DIMENSION, serverName);
        dimensions.putAll(extraDimensions);
        return metric.createContext(dimensions);
    }

    public static JDiscServerConnector fromRequest(ServletRequest request) {
        return (JDiscServerConnector) request.getAttribute(REQUEST_ATTRIBUTE);
    }

    ConnectorConfig connectorConfig() {
        return config;
    }

    int listenPort() {
        return listenPort;
    }

    private static Map<String, Object> createConnectorDimensions(int listenPort, String connectorName, int reservedSize) {
        Map<String, Object> props = new HashMap<>(reservedSize + 2);
        props.put(MetricDefinitions.NAME_DIMENSION, connectorName);
        props.put(MetricDefinitions.PORT_DIMENSION, listenPort);
        return props;
    }

}
