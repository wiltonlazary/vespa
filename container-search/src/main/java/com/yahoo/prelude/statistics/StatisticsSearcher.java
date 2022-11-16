// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.statistics;

import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.MetricSettings;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.logging.Level;

import static com.yahoo.container.protect.Error.BACKEND_COMMUNICATION_ERROR;
import static com.yahoo.container.protect.Error.EMPTY_DOCUMENTS;
import static com.yahoo.container.protect.Error.ERROR_IN_PLUGIN;
import static com.yahoo.container.protect.Error.ILLEGAL_QUERY;
import static com.yahoo.container.protect.Error.INTERNAL_SERVER_ERROR;
import static com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER;
import static com.yahoo.container.protect.Error.INVALID_QUERY_TRANSFORMATION;
import static com.yahoo.container.protect.Error.NO_BACKENDS_IN_SERVICE;
import static com.yahoo.container.protect.Error.RESULT_HAS_ERRORS;
import static com.yahoo.container.protect.Error.SERVER_IS_MISCONFIGURED;
import static com.yahoo.container.protect.Error.TIMEOUT;
import static com.yahoo.container.protect.Error.UNSPECIFIED;


/**
 * <p>A searcher to gather statistics such as queries completed and query latency.  There
 * may be more than 1 StatisticsSearcher in the Searcher chain, each identified by a
 * Searcher ID.  The statistics accumulated by all StatisticsSearchers are stored
 * in the singleton StatisticsManager object. </p>
 * <p>
 * TODO: Fix events to handle more than one of these searchers properly.
 *
 * @author Gene Meyers
 * @author Steinar Knutsen
 * @author bergum
 */
@Before(PhaseNames.RAW_QUERY)
public class StatisticsSearcher extends Searcher {

    private static final CompoundName IGNORE_QUERY = new CompoundName("metrics.ignore");
    private static final String MAX_QUERY_LATENCY_METRIC = "max_query_latency";
    private static final String EMPTY_RESULTS_METRIC = "empty_results";
    private static final String HITS_PER_QUERY_METRIC = "hits_per_query";
    private static final String TOTALHITS_PER_QUERY_METRIC = "totalhits_per_query";
    private static final String FAILED_QUERIES_METRIC = "failed_queries";
    private static final String MEAN_QUERY_LATENCY_METRIC = "mean_query_latency";
    private static final String QUERY_LATENCY_METRIC = "query_latency";
    private static final String QUERY_HIT_OFFSET_METRIC = "query_hit_offset";
    private static final String QUERIES_METRIC = "queries";
    private static final String PEAK_QPS_METRIC = "peak_qps";
    private static final String DOCS_COVERED_METRIC = "documents_covered";
    private static final String DOCS_TOTAL_METRIC = "documents_total";
    private static final String DOCS_TARGET_TOTAL_METRIC = "documents_target_total";
    private static final String DEGRADED_QUERIES_METRIC = "degraded_queries";
    private static final String RELEVANCE_AT_1_METRIC = "relevance.at_1";
    private static final String RELEVANCE_AT_3_METRIC = "relevance.at_3";
    private static final String RELEVANCE_AT_10_METRIC = "relevance.at_10";
    private static final String QUERY_ITEM_COUNT = "query_item_count";

    @SuppressWarnings("unused") // all the work is done by the callback
    private final PeakQpsReporter peakQpsReporter;

    // Naming of enums are reflected directly in metric dimensions and should not be changed as they are public API
    private enum DegradedReason { match_phase, adaptive_timeout, timeout, non_ideal_state }

    private final Metric metric;
    private final Map<String, Metric.Context> chainContexts = new CopyOnWriteHashMap<>();
    private final Map<String, Metric.Context> statePageOnlyContexts = new CopyOnWriteHashMap<>();
    private final Map<String, Map<DegradedReason, Metric.Context>> degradedReasonContexts = new CopyOnWriteHashMap<>();
    private final Map<String, Map<String, Metric.Context>> relevanceContexts = new CopyOnWriteHashMap<>();
    private final java.util.Timer scheduler = new java.util.Timer(true);

    private class PeakQpsReporter extends java.util.TimerTask {
        private long prevMaxQPSTime = System.currentTimeMillis();
        private long queriesForQPS = 0;
        private Metric.Context metricContext = null;
        public void setContext(Metric.Context metricContext) {
            if (this.metricContext == null) {
                synchronized(this) {
                    this.metricContext = metricContext;
                }
            }
        }
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (metricContext == null) return;
                flushPeakQps(now);
            }
        }
        private void flushPeakQps(long now) {
            double ms = (double) (now - prevMaxQPSTime);
            final double value = ((double)queriesForQPS) / (ms / 1000.0);
            metric.set(PEAK_QPS_METRIC, value, metricContext);
            prevMaxQPSTime = now;
            queriesForQPS = 0;
        }
        void countQuery() {
            synchronized (this) {
                ++queriesForQPS;
            }
        }
    }

    public StatisticsSearcher(Metric metric, MetricReceiver metricReceiver) {
        this.peakQpsReporter = new PeakQpsReporter();
        this.metric = metric;

        metricReceiver.declareGauge(QUERY_LATENCY_METRIC, Optional.empty(), new MetricSettings.Builder().histogram(true).build());
        metricReceiver.declareGauge(HITS_PER_QUERY_METRIC, Optional.empty(), new MetricSettings.Builder().histogram(true).build());
        metricReceiver.declareGauge(TOTALHITS_PER_QUERY_METRIC, Optional.empty(), new MetricSettings.Builder().histogram(true).build());

        scheduler.schedule(peakQpsReporter, 1000, 1000);
    }

    @Override
    public void deconstruct() {
        scheduler.cancel();
    }

    private void qps(Metric.Context metricContext) {
        peakQpsReporter.setContext(metricContext);
        peakQpsReporter.countQuery();
    }

    private Metric.Context getChainMetricContext(String chainName) {
        Metric.Context context = chainContexts.get(chainName);
        if (context == null) {
            Map<String, String> dimensions = new HashMap<>();
            dimensions.put("chain", chainName);
            context = this.metric.createContext(dimensions);
            chainContexts.put(chainName, context);
        }
        return context;
    }

    private Metric.Context getDegradedMetricContext(String chainName, Coverage coverage) {
        Map<DegradedReason, Metric.Context> reasons = degradedReasonContexts.get(chainName);
        if (reasons == null) {
            reasons = new HashMap<>(4);
            for (DegradedReason reason : DegradedReason.values() ) {
                Map<String, String> dimensions = new HashMap<>();
                dimensions.put("chain", chainName);
                dimensions.put("reason", reason.toString());
                Metric.Context context = this.metric.createContext(dimensions);
                reasons.put(reason, context);
            }
            degradedReasonContexts.put(chainName, reasons);
        }
        return reasons.get(getMostImportantDegradeReason(coverage));
    }

    private DegradedReason getMostImportantDegradeReason(Coverage coverage) {
        if (coverage.isDegradedByMatchPhase()) {
            return DegradedReason.match_phase;
        }
        if (coverage.isDegradedByTimeout()) {
            return DegradedReason.timeout;
        }
        if (coverage.isDegradedByAdapativeTimeout()) {
            return DegradedReason.adaptive_timeout;
        }
        return DegradedReason.non_ideal_state;
    }

    private Metric.Context createRelevanceMetricContext(String chainName, String rankProfile) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("chain", chainName);
        dimensions.put("rankProfile", rankProfile);
        return metric.createContext(dimensions);
    }

    private Metric.Context getRelevanceMetricContext(Execution execution, Query query) {
        String chain = execution.chain().getId().stringValue();
        String rankProfile = query.getRanking().getProfile();
        Map<String, Metric.Context> chainContext = relevanceContexts.get(chain);  // CopyOnWriteHashMap - don't use computeIfAbsent
        if (chainContext == null) {
            chainContext = new CopyOnWriteHashMap<>();
            relevanceContexts.put(chain, chainContext);
        }
        Metric.Context metricContext = chainContext.get(rankProfile);
        if (metricContext == null) {
            metricContext = createRelevanceMetricContext(chain, rankProfile);
            chainContext.put(rankProfile, metricContext);
        }
        return metricContext;
    }

    /**
     * Generate statistics for the query passing through this Searcher
     * 1) Add 1 to total query count
     * 2) Add response time to total response time (time from entry to return)
     * 3) .....
     */
    @Override
    public Result search(Query query, Execution execution) {
        if (query.properties().getBoolean(IGNORE_QUERY,false)) {
            return execution.search(query);
        }

        Metric.Context metricContext = getChainMetricContext(execution.chain().getId().stringValue());

        incrQueryCount(metricContext);
        logQuery(query);
        long start_ns = getStartNanoTime(query);
        qps(metricContext);
        Result result;
        //handle exceptions thrown below in searchers
        try {
            result = execution.search(query); // Pass on down the chain
        } catch (Exception e) {
            incrErrorCount(null, metricContext);
            throw e;
        }

        long end_ns = System.nanoTime(); // End time, in nanoseconds
        long latency_ns = end_ns - start_ns;
        if (latency_ns >= 0) {
            addLatency(latency_ns, metricContext);
        } else {
            getLogger().log(Level.WARNING,
                            "Apparently negative latency measure, start: " + start_ns
                            + ", end: " + end_ns + ", for query: " + query);
        }
        if (result.hits().getError() != null) {
            incrErrorCount(result, metricContext);
            incrementStatePageOnlyErrors(result);
        }
        Coverage queryCoverage = result.getCoverage(false);
        if (queryCoverage != null) {
            if (queryCoverage.isDegraded()) {
                Metric.Context degradedContext = getDegradedMetricContext(execution.chain().getId().stringValue(), queryCoverage);
                metric.add(DEGRADED_QUERIES_METRIC, 1, degradedContext);
            }
            metric.add(DOCS_COVERED_METRIC, queryCoverage.getDocs(), metricContext);
            metric.add(DOCS_TOTAL_METRIC, queryCoverage.getActive(), metricContext);
            metric.add(DOCS_TARGET_TOTAL_METRIC, queryCoverage.getTargetActive(), metricContext);
        }
        int hitCount = result.getConcreteHitCount();
        metric.set(HITS_PER_QUERY_METRIC, (double) hitCount, metricContext);

        long totalHitCount = result.getTotalHitCount();
        metric.set(TOTALHITS_PER_QUERY_METRIC, (double) totalHitCount, metricContext);
        metric.set(QUERY_HIT_OFFSET_METRIC, (double) (query.getHits() + query.getOffset()), metricContext);
        if (hitCount == 0) {
            metric.add(EMPTY_RESULTS_METRIC, 1, metricContext);
        }

        addRelevanceMetrics(query, execution, result);

        addItemCountMetric(query, metricContext);

        return result;
    }


    private void logQuery(com.yahoo.search.Query query) {
        // Don't parse the query if it's not necessary for the logging Query.toString triggers parsing
        if (getLogger().isLoggable(Level.FINER)) {
            getLogger().finer("Query: " + query.toString());
        }
    }

    private void addLatency(long latency_ns, Metric.Context metricContext) {
        double latency = 0.000001 * latency_ns;
        metric.set(QUERY_LATENCY_METRIC, latency, metricContext);
        metric.set(MEAN_QUERY_LATENCY_METRIC, latency, metricContext);
        metric.set(MAX_QUERY_LATENCY_METRIC, latency, metricContext);
    }

    private void incrQueryCount(Metric.Context metricContext) {
        metric.add(QUERIES_METRIC, 1, metricContext);
    }

    private void incrErrorCount(Result result, Metric.Context metricContext) {
        metric.add(FAILED_QUERIES_METRIC, 1, metricContext);

        if (result == null) // the chain threw an exception
            metric.add("error.unhandled_exception", 1, metricContext);
    }

    /**
     * Creates error metric for StateHandler only. These metrics are only exposed on /state/v1/metrics page
     * and not forwarded to the log file.
     *
     * @param result The result to check for errors
     */
    private void incrementStatePageOnlyErrors(Result result) {
        if (result == null) return;

        ErrorHit error = result.hits().getErrorHit();
        if (error == null) return;

        for (ErrorMessage m : error.errors()) {
            int code = m.getCode();
            Metric.Context c = getDimensions(m.getSource());
            if (code == TIMEOUT.code) {
                metric.add("error.timeout", 1, c);
            } else if (code == NO_BACKENDS_IN_SERVICE.code) {
                metric.add("error.backends_oos", 1, c);
            } else if (code == ERROR_IN_PLUGIN.code) {
                metric.add("error.plugin_failure", 1, c);
            } else if (code == BACKEND_COMMUNICATION_ERROR.code) {
                metric.add("error.backend_communication_error", 1, c);
            } else if (code == EMPTY_DOCUMENTS.code) {
                metric.add("error.empty_document_summaries", 1, c);
            } else if (code == ILLEGAL_QUERY.code) {
                metric.add("error.illegal_query", 1, c);
            } else if (code == INVALID_QUERY_PARAMETER.code) {
                metric.add("error.invalid_query_parameter", 1, c);
            } else if (code == INTERNAL_SERVER_ERROR.code) {
                metric.add("error.internal_server_error", 1, c);
            } else if (code == SERVER_IS_MISCONFIGURED.code) {
                metric.add("error.misconfigured_server", 1, c);
            } else if (code == INVALID_QUERY_TRANSFORMATION.code) {
                metric.add("error.invalid_query_transformation", 1, c);
            } else if (code == RESULT_HAS_ERRORS.code) {
                metric.add("error.result_with_errors", 1, c);
            } else if (code == UNSPECIFIED.code) {
                metric.add("error.unspecified", 1, c);
            }
        }
    }

    private Metric.Context getDimensions(String source) {
        Metric.Context context = statePageOnlyContexts.get(source == null ? "" : source);
        if (context == null) {
            Map<String, String> dims = new HashMap<>();
            if (source != null) {
                dims.put("source", source);
            }
            context = this.metric.createContext(dims);
            statePageOnlyContexts.put(source == null ? "" : source, context);
        }
        // TODO add other relevant metric dimensions
        // Would be nice to have chain as a dimension as
        // we can separate errors from different chains
        return context;
    }

    /**
     * Effectively flattens the hits, and measures relevance @ 1, 3, and 10
     */
    private void addRelevanceMetrics(Query query, Execution execution, Result result) {
        Queue<Double> topScores = findTopRelevanceScores(10, result.hits());
        if (topScores.isEmpty()) {
            return;
        }
        Metric.Context metricContext = getRelevanceMetricContext(execution, query);
        setRelevanceMetric(10, RELEVANCE_AT_10_METRIC, topScores, metricContext);  // min-queue: lowest values are polled first
        setRelevanceMetric(3,  RELEVANCE_AT_3_METRIC,  topScores, metricContext);
        setRelevanceMetric(1,  RELEVANCE_AT_1_METRIC,  topScores, metricContext);
    }

    private static Queue<Double> findTopRelevanceScores(int n, HitGroup hits) {
        PriorityQueue<Double> heap = new PriorityQueue<>(n);
        for (var iterator = hits.unorderedDeepIterator(); iterator.hasNext(); ) {
            Hit hit = iterator.next();
            if (hit instanceof ErrorHit || hit.getRelevance() == null) {
                continue;
            }
            double score = hit.getRelevance().getScore();
            if (Double.isNaN(score)) {
                continue;
            }
            if (heap.size() < n) {
                heap.add(score);
            } else if (score > heap.peek()) {
                heap.remove();
                heap.add(score);
            }
        }
        return heap;
    }

    private void setRelevanceMetric(int pos, String name, Queue<Double> minQueue, Metric.Context context) {
        while (minQueue.size() > pos) {
            minQueue.remove();
        }
        if (minQueue.size() == pos) {
            metric.set(name, minQueue.poll(), context);
        }
    }

    private void addItemCountMetric(Query query, Metric.Context context) {
        metric.set(QUERY_ITEM_COUNT, query.getModel().getQueryTree().treeSize(), context);
    }

    /**
     * Returns the relative start time from request was received by jdisc
     */
    private static long getStartNanoTime(Query query) {
        return Optional.ofNullable(query.getHttpRequest())
                .flatMap(httpRequest -> Optional.ofNullable(httpRequest.getJDiscRequest()))
                .map(HttpRequest::relativeCreatedAtNanoTime)
                .orElseGet(System::nanoTime);
    }

}

