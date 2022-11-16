// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * All summary fields which are not attributes
 * must currently be present in the default summary class,
 * since the default summary class also defines the docsum.dat format.
 * This processor adds any missing summaries to the default summary.
 * When that is decoupled from the actual summaries returned, this
 * processor can be removed. Note: the StreamingSummary also takes advantage of
 * the fact that default is the superset.
 *
 * All other summary logic should work unchanged without this processing step
 * except that IndexStructureValidator.validateSummaryFields must be changed to
 * consider all summaries, not just the default, i.e change to
 * if (search.getSummaryField(expr.getFieldName()) == null)
 *
 * This must be done after other summary processors.
 *
 * @author bratseth
 */
public class MakeDefaultSummaryTheSuperSet extends Processor {

    public MakeDefaultSummaryTheSuperSet(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        DocumentSummary defaultSummary= schema.getSummariesInThis().get("default");
        for (SummaryField summaryField : schema.getUniqueNamedSummaryFields().values() ) {
            if (defaultSummary.getSummaryField(summaryField.getName()) != null) continue;
            if (summaryField.getTransform() == SummaryTransform.ATTRIBUTE) continue;
            if (summaryField.getTransform() == SummaryTransform.ATTRIBUTECOMBINER) continue;
            if (summaryField.getTransform() == SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER) continue;

            defaultSummary.add(summaryField.clone());
        }
    }

}
