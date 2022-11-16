// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.DocumentOnlySchema;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchConfig.DistributionPolicy;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.content.DispatchSpec;
import com.yahoo.vespa.model.content.DispatchTuning;
import com.yahoo.vespa.model.content.SearchCoverage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author baldersheim
 */
public class IndexedSearchCluster extends SearchCluster
    implements
        DocumentdbInfoConfig.Producer,
        IndexInfoConfig.Producer,
        SchemaInfoConfig.Producer,
        IlscriptsConfig.Producer,
        DispatchConfig.Producer,
        ConfigInstance.Producer {

    private String indexingClusterName = null; // The name of the docproc cluster to run indexing, by config.
    private String indexingChainName = null;

    private DocprocChain indexingChain; // The actual docproc chain indexing for this.

    private Tuning tuning;
    private SearchCoverage searchCoverage;

    // This is the document selector string as derived from the subscription tag.
    private String routingSelector = null;
    private final List<DocumentDatabase> documentDbs = new LinkedList<>();
    private final MultipleDocumentDatabasesConfigProducer documentDbsConfigProducer;

    private int searchableCopies = 1;
    private int redundancy = 1;

    private final DispatchGroup rootDispatch;
    private DispatchSpec dispatchSpec;
    private final List<SearchNode> searchNodes = new ArrayList<>();
    private final DispatchTuning.DispatchPolicy defaultDispatchPolicy;
    private final double dispatchWarmup;
    /**
     * Returns the document selector that is able to resolve what documents are to be routed to this search cluster.
     * This string uses the document selector language as defined in the "document" module.
     *
     * @return the document selector
     */
    public String getRoutingSelector() {
        return routingSelector;
    }

    public IndexedSearchCluster(AbstractConfigProducer<SearchCluster> parent, String clusterName, int index, ModelContext.FeatureFlags featureFlags) {
        super(parent, clusterName, index);
        documentDbsConfigProducer = new MultipleDocumentDatabasesConfigProducer(this, documentDbs);
        rootDispatch =  new DispatchGroup(this);
        defaultDispatchPolicy = DispatchTuning.Builder.toDispatchPolicy(featureFlags.queryDispatchPolicy());
        dispatchWarmup = featureFlags.queryDispatchWarmup();
    }

    @Override
    protected IndexingMode getIndexingMode() { return IndexingMode.REALTIME; }

    public final boolean hasExplicitIndexingCluster() {
        return indexingClusterName != null;
    }

    public final boolean hasExplicitIndexingChain() {
        return indexingChainName != null;
    }

    /**
     * Returns the name of the docproc cluster running indexing for this search cluster. This is derived from the
     * services file on initialization, this can NOT be used at runtime to determine indexing chain. When initialization
     * is done, the {@link #getIndexingServiceName()} method holds the actual indexing docproc chain object.
     *
     * @return the name of the docproc cluster associated with this
     */
    public String getIndexingClusterName() {
        return hasExplicitIndexingCluster() ? indexingClusterName : getClusterName() + ".indexing";
    }

    public String getIndexingChainName() {
        return indexingChainName;
    }

    public void setIndexingChainName(String indexingChainName) {
        this.indexingChainName = indexingChainName;
    }

    /**
     * Sets the name of the docproc cluster running indexing for this search cluster. This is for initial configuration,
     * and will not reflect the actual indexing chain. See {@link #getIndexingClusterName} for more detail.
     *
     * @param name the name of the docproc cluster associated with this
     */
    public void setIndexingClusterName(String name) {
        indexingClusterName = name;
    }

    public String getIndexingServiceName() {
        return indexingChain.getServiceName();
    }

    /**
     * Sets the docproc chain that will be running indexing for this search cluster. This is set by the
     * {@link com.yahoo.vespa.model.content.Content} model during build.
     *
     * @param chain the chain that is to run indexing for this cluster
     * @return this, to allow chaining
     */
    public SearchCluster setIndexingChain(DocprocChain chain) {
        indexingChain = chain;
        return this;
    }

    public DispatchGroup getRootDispatch() { return rootDispatch; }

    public void addSearcher(SearchNode searcher) {
        searchNodes.add(searcher);
        rootDispatch.addSearcher(searcher);
    }

    public List<SearchNode> getSearchNodes() { return Collections.unmodifiableList(searchNodes); }
    public int getSearchNodeCount() { return searchNodes.size(); }
    public SearchNode getSearchNode(int index) { return searchNodes.get(index); }
    public void setTuning(Tuning tuning) {
        this.tuning = tuning;
    }
    public Tuning getTuning() { return tuning; }

    public void fillDocumentDBConfig(String documentType, ProtonConfig.Documentdb.Builder builder) {
        for (DocumentDatabase sdoc : documentDbs) {
            if (sdoc.getName().equals(documentType)) {
                fillDocumentDBConfig(sdoc, builder);
                return;
            }
        }
    }

    private void fillDocumentDBConfig(DocumentDatabase sdoc, ProtonConfig.Documentdb.Builder ddbB) {
        ddbB.inputdoctypename(sdoc.getSchemaName())
            .configid(sdoc.getConfigId());
    }

    public void setRoutingSelector(String selector) {
        this.routingSelector = selector;
        if (this.routingSelector != null) {
            try {
                new DocumentSelectionConverter(this.routingSelector);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid routing selector: " + e.getMessage());
            }
        }
    }
    /**
     * Create default config if not specified by user.
     * Accept empty strings as user config - it means that all feeds/documents are accepted.
     */
    public void defaultDocumentsConfig() {
        if ((routingSelector == null) && !getDocumentNames().isEmpty()) {
            Iterator<String> it = getDocumentNames().iterator();
            routingSelector = it.next();
            StringBuilder sb = new StringBuilder(routingSelector);
            while (it.hasNext()) {
                sb.append(" or ").append(it.next());
            }
            routingSelector = sb.toString();
        }
    }

    @Override
    public void deriveFromSchemas(DeployState deployState) {
        for (SchemaInfo spec : schemas().values()) {
            if (spec.fullSchema() instanceof DocumentOnlySchema) continue;
            DocumentDatabase db = new DocumentDatabase(this, spec.fullSchema().getName(),
                                                       new DerivedConfiguration(spec.fullSchema(), deployState));
            documentDbs.add(db);
        }
    }

    public List<DocumentDatabase> getDocumentDbs() {
        return documentDbs;
    }
    public boolean hasDocumentDB(String name) {
        for (DocumentDatabase db : documentDbs) {
            if (db.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void setSearchCoverage(SearchCoverage searchCoverage) {
        this.searchCoverage = searchCoverage;
    }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        for (DocumentDatabase db : documentDbs) {
            DocumentdbInfoConfig.Documentdb.Builder docDb = new DocumentdbInfoConfig.Documentdb.Builder();
            docDb.name(db.getName());
            builder.documentdb(docDb);
        }
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        documentDbsConfigProducer.getConfig(builder);
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        documentDbsConfigProducer.getConfig(builder);
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        documentDbsConfigProducer.getConfig(builder);
    }

    public void getConfig(AttributesConfig.Builder builder) {
        documentDbsConfigProducer.getConfig(builder);
    }

    public void getConfig(RankProfilesConfig.Builder builder) {
        documentDbsConfigProducer.getConfig(builder);
    }

    boolean useFixedRowInDispatch() {
        for (SearchNode node : getSearchNodes()) {
            if (node.getNodeSpec().groupIndex() > 0) {
                return true;
            }
        }
        return false;
    }

    public int getSearchableCopies() {
        return searchableCopies;
    }

    public void setSearchableCopies(int searchableCopies) {
        this.searchableCopies = searchableCopies;
    }

    public int getRedundancy() {
        return redundancy;
    }

    public void setRedundancy(int redundancy) {
        this.redundancy = redundancy;
    }

    public void setDispatchSpec(DispatchSpec dispatchSpec) {
        if (dispatchSpec.getNumDispatchGroups() != null) {
            this.dispatchSpec = new DispatchSpec.Builder().setGroups
                    (DispatchGroupBuilder.createDispatchGroups(getSearchNodes(),
                                                               dispatchSpec.getNumDispatchGroups())).build();
        } else {
            this.dispatchSpec = dispatchSpec;
        }
    }

    public DispatchSpec getDispatchSpec() {
        return dispatchSpec;
    }

    private static DistributionPolicy.Enum toDistributionPolicy(DispatchTuning.DispatchPolicy tuning) {
        return switch (tuning) {
            case ADAPTIVE: yield DistributionPolicy.ADAPTIVE;
            case ROUNDROBIN: yield DistributionPolicy.ROUNDROBIN;
            case BEST_OF_RANDOM_2: yield DistributionPolicy.BEST_OF_RANDOM_2;
            case LATENCY_AMORTIZED_OVER_REQUESTS: yield DistributionPolicy.LATENCY_AMORTIZED_OVER_REQUESTS;
            case LATENCY_AMORTIZED_OVER_TIME: yield DistributionPolicy.LATENCY_AMORTIZED_OVER_TIME;
        };
    }
    @Override
    public void getConfig(DispatchConfig.Builder builder) {
        for (SearchNode node : getSearchNodes()) {
            DispatchConfig.Node.Builder nodeBuilder = new DispatchConfig.Node.Builder();
            nodeBuilder.key(node.getDistributionKey());
            nodeBuilder.group(node.getNodeSpec().groupIndex());
            nodeBuilder.host(node.getHostName());
            nodeBuilder.port(node.getRpcPort());
            builder.node(nodeBuilder);
        }
        if (tuning.dispatch.getTopkProbability() != null) {
            builder.topKProbability(tuning.dispatch.getTopkProbability());
        }
        if (tuning.dispatch.getMinActiveDocsCoverage() != null)
            builder.minActivedocsPercentage(tuning.dispatch.getMinActiveDocsCoverage());
        if (tuning.dispatch.getDispatchPolicy() != null) {
            builder.distributionPolicy(toDistributionPolicy(tuning.dispatch.getDispatchPolicy()));
        } else {
            builder.distributionPolicy(toDistributionPolicy(defaultDispatchPolicy));
        }
        if (tuning.dispatch.getMaxHitsPerPartition() != null)
            builder.maxHitsPerNode(tuning.dispatch.getMaxHitsPerPartition());

        builder.searchableCopies(rootDispatch.getSearchableCopies());
        builder.redundancy(rootDispatch.getRedundancy());
        if (searchCoverage != null) {
            if (searchCoverage.getMinimum() != null)
                builder.minSearchCoverage(searchCoverage.getMinimum() * 100.0);
            if (searchCoverage.getMinWaitAfterCoverageFactor() != null)
                builder.minWaitAfterCoverageFactor(searchCoverage.getMinWaitAfterCoverageFactor());
            if (searchCoverage.getMaxWaitAfterCoverageFactor() != null)
                builder.maxWaitAfterCoverageFactor(searchCoverage.getMaxWaitAfterCoverageFactor());
        }
        builder.warmuptime(dispatchWarmup);
    }

    @Override
    public int getRowBits() { return 8; }

    @Override
    public String toString() {
        return "Indexing cluster '" + getClusterName() + "'";
    }

    /**
     * Class used to retrieve combined configuration from multiple document databases.
     * It is not a direct {@link com.yahoo.config.ConfigInstance.Producer} of those configs,
     * that is handled (by delegating to this) by the {@link IndexedSearchCluster}
     * which is the parent to this. This avoids building the config multiple times.
     */
    public static class MultipleDocumentDatabasesConfigProducer
            extends AbstractConfigProducer<MultipleDocumentDatabasesConfigProducer>
            implements AttributesConfig.Producer,
                       IndexInfoConfig.Producer,
                       IlscriptsConfig.Producer,
                       SchemaInfoConfig.Producer,
                       RankProfilesConfig.Producer {
        private final List<DocumentDatabase> docDbs;

        private MultipleDocumentDatabasesConfigProducer(AbstractConfigProducer<?> parent, List<DocumentDatabase> docDbs) {
            super(parent, "union");
            this.docDbs = docDbs;
        }

        @Override
        public void getConfig(IndexInfoConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        @Override
        public void getConfig(SchemaInfoConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        @Override
        public void getConfig(IlscriptsConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        @Override
        public void getConfig(RankProfilesConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

    }

}
