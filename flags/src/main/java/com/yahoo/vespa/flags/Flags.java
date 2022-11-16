// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.component.Vtag;
import com.yahoo.vespa.defaults.Defaults;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.FetchVector.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.FetchVector.Dimension.NODE_TYPE;
import static com.yahoo.vespa.flags.FetchVector.Dimension.TENANT_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.VESPA_VERSION;
import static com.yahoo.vespa.flags.FetchVector.Dimension.ZONE_ID;

/**
 * Definitions of feature flags.
 *
 * <p>To use feature flags, define the flag in this class as an "unbound" flag, e.g. {@link UnboundBooleanFlag}
 * or {@link UnboundStringFlag}. At the location you want to get the value of the flag, you need the following:</p>
 *
 * <ol>
 *     <li>The unbound flag</li>
 *     <li>A {@link FlagSource}. The flag source is typically available as an injectable component. Binding
 *     an unbound flag to a flag source produces a (bound) flag, e.g. {@link BooleanFlag} and {@link StringFlag}.</li>
 *     <li>If you would like your flag value to be dependent on e.g. the application ID, then 1. you should
 *     declare this in the unbound flag definition in this file (referring to
 *     {@link FetchVector.Dimension#APPLICATION_ID}), and 2. specify the application ID when retrieving the value, e.g.
 *     {@link BooleanFlag#with(FetchVector.Dimension, String)}. See {@link FetchVector} for more info.</li>
 * </ol>
 *
 * <p>Once the code is in place, you can override the flag value. This depends on the flag source, but typically
 * there is a REST API for updating the flags in the config server, which is the root of all flag sources in the zone.</p>
 *
 * @author hakonhall
 */
public class Flags {

    private static volatile TreeMap<FlagId, FlagDefinition> flags = new TreeMap<>();

    public static final UnboundDoubleFlag DEFAULT_TERM_WISE_LIMIT = defineDoubleFlag(
            "default-term-wise-limit", 1.0,
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Default limit for when to apply termwise query evaluation",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag QUERY_DISPATCH_POLICY = defineStringFlag(
            "query-dispatch-policy", "adaptive",
            List.of("baldersheim"), "2022-08-20", "2023-01-01",
            "Select query dispatch policy, valid values are adaptive, round-robin, best-of-random-2," +
                    " latency-amortized-over-requests, latency-amortized-over-time",
            "Takes effect at redeployment (requires restart)",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag FEED_SEQUENCER_TYPE = defineStringFlag(
            "feed-sequencer-type", "THROUGHPUT",
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Selects type of sequenced executor used for feeding in proton, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment (requires restart)",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag KEEP_STORAGE_NODE_UP = defineFeatureFlag(
            "keep-storage-node-up", true,
            List.of("hakonhall"), "2022-07-07", "2022-12-07",
            "Whether to leave the storage node (with wanted state) UP while the node is permanently down.",
            "Takes effect immediately for nodes transitioning to permanently down.",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MAX_UNCOMMITTED_MEMORY = defineIntFlag(
            "max-uncommitted-memory", 130000,
            List.of("geirst, baldersheim"), "2021-10-21", "2023-01-01",
            "Max amount of memory holding updates to an attribute before we do a commit.",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag RESPONSE_SEQUENCER_TYPE = defineStringFlag(
            "response-sequencer-type", "ADAPTIVE",
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Selects type of sequenced executor used for mbus responses, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag RESPONSE_NUM_THREADS = defineIntFlag(
            "response-num-threads", 2,
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Number of threads used for mbus responses, default is 2, negative number = numcores/4",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_COMMUNICATIONMANAGER_THREAD = defineFeatureFlag(
            "skip-communicationmanager-thread", false,
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Should we skip the communicationmanager thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_MBUS_REQUEST_THREAD = defineFeatureFlag(
            "skip-mbus-request-thread", false,
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Should we skip the mbus request thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_MBUS_REPLY_THREAD = defineFeatureFlag(
            "skip-mbus-reply-thread", false,
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Should we skip the mbus reply thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE = defineFeatureFlag(
            "async-message-handling-on-schedule", false,
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "Optionally deliver async messages in own thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag FEED_CONCURRENCY = defineDoubleFlag(
            "feed-concurrency", 0.5,
            List.of("baldersheim"), "2020-12-02", "2023-01-01",
            "How much concurrency should be allowed for feed",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag FEED_NICENESS = defineDoubleFlag(
            "feed-niceness", 0.0,
            List.of("baldersheim"), "2022-06-24", "2023-01-01",
            "How nice feeding shall be",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);


    public static final UnboundIntFlag MBUS_JAVA_NUM_TARGETS = defineIntFlag(
            "mbus-java-num-targets", 1,
            List.of("baldersheim"), "2022-07-05", "2023-01-01",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag MBUS_CPP_NUM_TARGETS = defineIntFlag(
            "mbus-cpp-num-targets", 1,
            List.of("baldersheim"), "2022-07-05", "2023-01-01",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag RPC_NUM_TARGETS = defineIntFlag(
            "rpc-num-targets", 1,
            List.of("baldersheim"), "2022-07-05", "2023-01-01",
            "Number of rpc targets per content node",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag MBUS_JAVA_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-java-events-before-wakeup", 1,
            List.of("baldersheim"), "2022-07-05", "2023-01-01",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag MBUS_CPP_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-cpp-events-before-wakeup", 1,
            List.of("baldersheim"), "2022-07-05", "2023-01-01",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag RPC_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "rpc-events-before-wakeup", 1,
            List.of("baldersheim"), "2022-07-05", "2023-01-01",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MBUS_NUM_NETWORK_THREADS = defineIntFlag(
            "mbus-num-network-threads", 1,
            List.of("baldersheim"), "2022-07-01", "2023-01-01",
            "Number of threads used for mbus network",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SHARED_STRING_REPO_NO_RECLAIM = defineFeatureFlag(
            "shared-string-repo-no-reclaim", false,
            List.of("baldersheim"), "2022-06-14", "2023-01-01",
            "Controls whether we do track usage and reclaim unused enum values in shared string repo",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag CONTAINER_DUMP_HEAP_ON_SHUTDOWN_TIMEOUT = defineFeatureFlag(
            "container-dump-heap-on-shutdown-timeout", false,
            List.of("baldersheim"), "2021-09-25", "2023-01-01",
            "Will trigger a heap dump during if container shutdown times out",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundBooleanFlag LOAD_CODE_AS_HUGEPAGES = defineFeatureFlag(
            "load-code-as-hugepages", false,
            List.of("baldersheim"), "2022-05-13", "2023-01-01",
            "Will try to map the code segment with huge (2M) pages",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag CONTAINER_SHUTDOWN_TIMEOUT = defineDoubleFlag(
            "container-shutdown-timeout", 50.0,
            List.of("baldersheim"), "2021-09-25", "2023-05-01",
            "Timeout for shutdown of a jdisc container",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundListFlag<String> ALLOWED_ATHENZ_PROXY_IDENTITIES = defineListFlag(
            "allowed-athenz-proxy-identities", List.of(), String.class,
            List.of("bjorncs", "tokle"), "2021-02-10", "2023-03-01",
            "Allowed Athenz proxy identities",
            "takes effect at redeployment");

    public static final UnboundIntFlag MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS = defineIntFlag(
            "max-activation-inhibited-out-of-sync-groups", 0,
            List.of("vekterli"), "2021-02-19", "2022-12-01",
            "Allows replicas in up to N content groups to not be activated " +
            "for query visibility if they are out of sync with a majority of other replicas",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag MIN_NODE_RATIO_PER_GROUP = defineDoubleFlag(
            "min-node-ratio-per-group", 0.0,
            List.of("geirst", "vekterli"), "2021-07-16", "2023-02-01",
            "Minimum ratio of nodes that have to be available (i.e. not Down) in any hierarchic content cluster group for the group to be Up",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLED_HORIZON_DASHBOARD = defineFeatureFlag(
            "enabled-horizon-dashboard", false,
            List.of("olaa"), "2021-09-13", "2023-01-01",
            "Enable Horizon dashboard",
            "Takes effect immediately",
            TENANT_ID, CONSOLE_USER_EMAIL
    );

    public static final UnboundBooleanFlag IGNORE_THREAD_STACK_SIZES = defineFeatureFlag(
            "ignore-thread-stack-sizes", false,
            List.of("arnej"), "2021-11-12", "2022-12-01",
            "Whether C++ thread creation should ignore any requested stack size",
            "Triggers restart, takes effect immediately",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_V8_GEO_POSITIONS = defineFeatureFlag(
            "use-v8-geo-positions", true,
            List.of("arnej"), "2021-11-15", "2022-12-31",
            "Use Vespa 8 types and formats for geographical positions",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MAX_COMPACT_BUFFERS = defineIntFlag(
                "max-compact-buffers", 1,
                List.of("baldersheim", "geirst", "toregge"), "2021-12-15", "2023-01-01",
                "Upper limit of buffers to compact in a data store at the same time for each reason (memory usage, address space usage)",
                "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_QRSERVER_SERVICE_NAME = defineFeatureFlag(
            "use-qrserver-service-name", false,
            List.of("arnej"), "2022-01-18", "2022-12-31",
            "Use backwards-compatible 'qrserver' service name for containers with only 'search' API",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag AVOID_RENAMING_SUMMARY_FEATURES = defineFeatureFlag(
            "avoid-renaming-summary-features", true,
            List.of("arnej"), "2022-01-15", "2023-12-31",
            "Tell backend about the original name of summary-features that were wrapped in a rankingExpression feature",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag NOTIFICATION_DISPATCH_FLAG = defineFeatureFlag(
            "dispatch-notifications", false,
            List.of("enygaard"), "2022-05-02", "2022-12-30",
            "Whether we should send notification for a given tenant",
            "Takes effect immediately",
            TENANT_ID);

    public static final UnboundBooleanFlag ENABLE_PROXY_PROTOCOL_MIXED_MODE = defineFeatureFlag(
            "enable-proxy-protocol-mixed-mode", true,
            List.of("tokle"), "2022-05-09", "2022-12-01",
            "Enable or disable proxy protocol mixed mode",
            "Takes effect on redeployment",
            APPLICATION_ID);

    public static final UnboundListFlag<String> FILE_DISTRIBUTION_ACCEPTED_COMPRESSION_TYPES = defineListFlag(
            "file-distribution-accepted-compression-types", List.of("gzip", "lz4"), String.class,
            List.of("hmusum"), "2022-07-05", "2022-12-01",
            "´List of accepted compression types used when asking for a file reference. Valid values: gzip, lz4",
            "Takes effect on restart of service",
            APPLICATION_ID);

    public static final UnboundListFlag<String> FILE_DISTRIBUTION_COMPRESSION_TYPES_TO_SERVE = defineListFlag(
            "file-distribution-compression-types-to-use", List.of("lz4", "gzip"), String.class,
            List.of("hmusum"), "2022-07-05", "2022-12-01",
            "List of compression types to use (in preferred order), matched with accepted compression types when serving file references. Valid values: gzip, lz4",
            "Takes effect on restart of service",
            APPLICATION_ID);

    public static final UnboundBooleanFlag USE_YUM_PROXY_V2 = defineFeatureFlag(
            "use-yumproxy-v2", false,
            List.of("tokle"), "2022-05-05", "2022-12-01",
            "Use yumproxy-v2",
            "Takes effect on host admin restart",
            HOSTNAME);

    public static final UnboundStringFlag LOG_FILE_COMPRESSION_ALGORITHM = defineStringFlag(
            "log-file-compression-algorithm", "",
            List.of("arnej"), "2022-06-14", "2024-12-31",
            "Which algorithm to use for compressing log files. Valid values: empty string (default), gzip, zstd",
            "Takes effect immediately",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SEPARATE_METRIC_CHECK_CONFIG = defineFeatureFlag(
            "separate-metric-check-config", false,
            List.of("olaa"), "2022-07-04", "2023-01-01",
            "Determines whether one metrics config check should be written per Vespa node",
            "Takes effect on next tick",
            HOSTNAME);

    public static final UnboundStringFlag TLS_CAPABILITIES_ENFORCEMENT_MODE = defineStringFlag(
            "tls-capabilities-enforcement-mode", "disable",
            List.of("bjorncs", "vekterli"), "2022-07-21", "2024-01-01",
            "Configure Vespa TLS capability enforcement mode",
            "Takes effect on restart of Docker container",
            APPLICATION_ID,HOSTNAME,NODE_TYPE,TENANT_ID,VESPA_VERSION
    );

    public static final UnboundBooleanFlag CLEANUP_TENANT_ROLES = defineFeatureFlag(
            "cleanup-tenant-roles", false,
            List.of("olaa"), "2022-08-10", "2023-01-01",
            "Determines whether old tenant roles should be deleted",
            "Takes effect next maintenance run"
    );

    public static final UnboundBooleanFlag USE_TWO_PHASE_DOCUMENT_GC = defineFeatureFlag(
            "use-two-phase-document-gc", false,
            List.of("vekterli"), "2022-08-24", "2022-12-01",
            "Use two-phase document GC in content clusters",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag RESTRICT_DATA_PLANE_BINDINGS = defineFeatureFlag(
            "restrict-data-plane-bindings", false,
            List.of("mortent"), "2022-09-08", "2023-02-01",
            "Use restricted data plane bindings",
            "Takes effect at redeployment",
            APPLICATION_ID);

    public static final UnboundStringFlag CSRF_MODE = defineStringFlag(
            "csrf-mode", "disabled",
            List.of("bjorncs", "tokle"), "2022-09-22", "2023-06-01",
            "Set mode for CSRF filter ('disabled', 'log_only', 'enabled')",
            "Takes effect on controller restart/redeployment");

    public static final UnboundBooleanFlag SOFT_REBUILD = defineFeatureFlag(
            "soft-rebuild", true,
            List.of("mpolden"), "2022-09-27", "2022-12-01",
            "Whether soft rebuild can be used to rebuild hosts with remote disk",
            "Takes effect on next run of OsUpgradeActivator"
    );

    public static final UnboundListFlag<String> CSRF_USERS = defineListFlag(
            "csrf-users", List.of(), String.class,
            List.of("bjorncs", "tokle"), "2022-09-22", "2023-06-01",
            "List of users to enable CSRF filter for. Use empty list for everyone.",
            "Takes effect on controller restart/redeployment");

    public static final UnboundBooleanFlag ENABLE_OTELCOL = defineFeatureFlag(
            "enable-otel-collector", false,
            List.of("olaa"), "2022-09-23", "2023-01-01",
            "Whether an OpenTelemetry collector should be enabled",
            "Takes effect at next tick",
            APPLICATION_ID);

    public static final UnboundBooleanFlag CONSOLE_CSRF = defineFeatureFlag(
            "console-csrf", false,
            List.of("bjorncs", "tokle"), "2022-09-26", "2023-06-01",
            "Enable CSRF token in console",
            "Takes effect immediately",
            CONSOLE_USER_EMAIL);

    public static final UnboundBooleanFlag USE_WIREGUARD_ON_CONFIGSERVERS = defineFeatureFlag(
            "use-wireguard-on-configservers", false,
            List.of("andreer", "gjoranv"), "2022-09-28", "2023-04-01",
            "Set up a WireGuard endpoint on config servers",
            "Takes effect on configserver restart",
            HOSTNAME);

    public static final UnboundBooleanFlag USE_WIREGUARD_ON_TENANT_HOSTS = defineFeatureFlag(
            "use-wireguard-on-tenant-hosts", false,
            List.of("andreer", "gjoranv"), "2022-09-28", "2023-04-01",
            "Set up a WireGuard endpoint on tenant hosts",
            "Takes effect on host admin restart",
            HOSTNAME);

    public static final UnboundStringFlag AUTH0_SESSION_LOGOUT = defineStringFlag(
            "auth0-session-logout", "disabled",
            List.of("bjorncs", "tokle"), "2022-10-17", "2023-06-01",
            "Set mode for Auth0 session logout ('disabled', 'log_only', 'enabled')",
            "Takes effect on controller restart/redeployment");

    public static final UnboundBooleanFlag ENABLED_MAIL_VERIFICATION = defineFeatureFlag(
            "enabled-mail-verification", false,
            List.of("olaa"), "2022-10-28", "2023-01-01",
            "Enable mail verification",
            "Takes effect immediately");

    public static final UnboundBooleanFlag REPORT_CORES_VIA_CFG = defineFeatureFlag(
            "report-cores-via-cfg", true,
            List.of("hakonhall"), "2022-11-01", "2022-12-01",
            "If true, report core dumps to the config server instead of directly to the panic app.",
            "Takes effect on the next tick.",
            ZONE_ID, NODE_TYPE, HOSTNAME);

    public static final UnboundStringFlag CORE_ENCRYPTION_PUBLIC_KEY_ID = defineStringFlag(
            "core-encryption-public-key-id", "",
            List.of("vekterli"), "2022-11-03", "2022-12-01",
            "Specifies which public key to use for core dump encryption.",
            "Takes effect on the next tick.",
            ZONE_ID, NODE_TYPE, HOSTNAME);

    public static final UnboundBooleanFlag USE_OLD_JDISC_CONTAINER_STARTUP = defineFeatureFlag(
            "use-old-jdisc-container-startup", true,
            List.of("arnej", "baldersheim"), "2022-11-09", "2023-01-31",
            "If true, use the old vespa-start-container-daemon script.",
            "Takes effect immediately?",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_LOCKS_IN_FILEDISTRIBUTION = defineFeatureFlag(
            "use-locks-in-filedistribution", false,
            List.of("hmusum"), "2022-11-16", "2023-01-31",
            "If true, use locks when writing and deleting file references.",
            "Takes effect immediately",
            ZONE_ID, APPLICATION_ID);

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundBooleanFlag defineFeatureFlag(String flagId, boolean defaultValue, List<String> owners,
                                                       String createdAt, String expiresAt, String description,
                                                       String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundBooleanFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundStringFlag defineStringFlag(String flagId, String defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, FetchVector.Dimension... dimensions) {
        return defineStringFlag(flagId, defaultValue, owners,
                                createdAt, expiresAt, description,
                                modificationEffect, value -> true,
                                dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundStringFlag defineStringFlag(String flagId, String defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, Predicate<String> validator,
                                                     FetchVector.Dimension... dimensions) {
        return define((i, d, v) -> new UnboundStringFlag(i, d, v, validator),
                      flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundIntFlag defineIntFlag(String flagId, int defaultValue, List<String> owners,
                                               String createdAt, String expiresAt, String description,
                                               String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundIntFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundLongFlag defineLongFlag(String flagId, long defaultValue, List<String> owners,
                                                 String createdAt, String expiresAt, String description,
                                                 String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundLongFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundDoubleFlag defineDoubleFlag(String flagId, double defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundDoubleFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static <T> UnboundJacksonFlag<T> defineJacksonFlag(String flagId, T defaultValue, Class<T> jacksonClass, List<String> owners,
                                                              String createdAt, String expiresAt, String description,
                                                              String modificationEffect, FetchVector.Dimension... dimensions) {
        return define((id2, defaultValue2, vector2) -> new UnboundJacksonFlag<>(id2, defaultValue2, vector2, jacksonClass),
                flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static <T> UnboundListFlag<T> defineListFlag(String flagId, List<T> defaultValue, Class<T> elementClass,
                                                        List<String> owners, String createdAt, String expiresAt,
                                                        String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return define((fid, dval, fvec) -> new UnboundListFlag<>(fid, dval, elementClass, fvec),
                flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    @FunctionalInterface
    private interface TypedUnboundFlagFactory<T, U extends UnboundFlag<?, ?, ?>> {
        U create(FlagId id, T defaultValue, FetchVector defaultFetchVector);
    }

    /**
     * Defines a Flag.
     *
     * @param factory            Factory for creating unbound flag of type U
     * @param flagId             The globally unique FlagId.
     * @param defaultValue       The default value if none is present after resolution.
     * @param description        Description of how the flag is used.
     * @param modificationEffect What is required for the flag to take effect? A restart of process? immediately? etc.
     * @param dimensions         What dimensions will be set in the {@link FetchVector} when fetching
     *                           the flag value in
     *                           {@link FlagSource#fetch(FlagId, FetchVector) FlagSource::fetch}.
     *                           For instance, if APPLICATION is one of the dimensions here, you should make sure
     *                           APPLICATION is set to the ApplicationId in the fetch vector when fetching the RawFlag
     *                           from the FlagSource.
     * @param <T>                The boxed type of the flag value, e.g. Boolean for flags guarding features.
     * @param <U>                The type of the unbound flag, e.g. UnboundBooleanFlag.
     * @return An unbound flag with {@link FetchVector.Dimension#HOSTNAME HOSTNAME} and
     *         {@link FetchVector.Dimension#VESPA_VERSION VESPA_VERSION} already set. The ZONE environment
     *         is typically implicit.
     */
    private static <T, U extends UnboundFlag<?, ?, ?>> U define(TypedUnboundFlagFactory<T, U> factory,
                                                                String flagId,
                                                                T defaultValue,
                                                                List<String> owners,
                                                                String createdAt,
                                                                String expiresAt,
                                                                String description,
                                                                String modificationEffect,
                                                                FetchVector.Dimension[] dimensions) {
        FlagId id = new FlagId(flagId);
        FetchVector vector = new FetchVector()
                .with(HOSTNAME, Defaults.getDefaults().vespaHostname())
                // Warning: In unit tests and outside official Vespa releases, the currentVersion is e.g. 7.0.0
                // (determined by the current major version). Consider not setting VESPA_VERSION if minor = micro = 0.
                .with(VESPA_VERSION, Vtag.currentVersion.toFullString());
        U unboundFlag = factory.create(id, defaultValue, vector);
        FlagDefinition definition = new FlagDefinition(
                unboundFlag, owners, parseDate(createdAt), parseDate(expiresAt), description, modificationEffect, dimensions);
        flags.put(id, definition);
        return unboundFlag;
    }

    private static Instant parseDate(String rawDate) {
        return DateTimeFormatter.ISO_DATE.parse(rawDate, LocalDate::from).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static List<FlagDefinition> getAllFlags() {
        return List.copyOf(flags.values());
    }

    public static Optional<FlagDefinition> getFlag(FlagId flagId) {
        return Optional.ofNullable(flags.get(flagId));
    }

    /**
     * Allows the statically defined flags to be controlled in a test.
     *
     * <p>Returns a Replacer instance to be used with e.g. a try-with-resources block. Within the block,
     * the flags starts out as cleared. Flags can be defined, etc. When leaving the block, the flags from
     * before the block is reinserted.
     *
     * <p>NOT thread-safe. Tests using this cannot run in parallel.
     */
    public static Replacer clearFlagsForTesting(FlagId... flagsToKeep) {
        return new Replacer(flagsToKeep);
    }

    public static class Replacer implements AutoCloseable {
        private static volatile boolean flagsCleared = false;

        private final TreeMap<FlagId, FlagDefinition> savedFlags;

        private Replacer(FlagId... flagsToKeep) {
            verifyAndSetFlagsCleared(true);
            this.savedFlags = Flags.flags;
            Flags.flags = new TreeMap<>();
            List.of(flagsToKeep).forEach(id -> Flags.flags.put(id, savedFlags.get(id)));
        }

        @Override
        public void close() {
            verifyAndSetFlagsCleared(false);
            Flags.flags = savedFlags;
        }

        /**
         * Used to implement a simple verification that Replacer is not used by multiple threads.
         * For instance two different tests running in parallel cannot both use Replacer.
         */
        private static void verifyAndSetFlagsCleared(boolean newValue) {
            if (flagsCleared == newValue) {
                throw new IllegalStateException("clearFlagsForTesting called while already cleared - running tests in parallell!?");
            }
            flagsCleared = newValue;
        }
    }
}
