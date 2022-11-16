// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.NumericDataType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Index;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashSet;
import java.util.Set;

/**
 * Replaces the 'index' statement of all numerical fields to 'attribute' because we no longer support numerical indexes.
 *
 * @author baldersheim
 */
public class IntegerIndex2Attribute extends Processor {

    public IntegerIndex2Attribute(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if (field.doesIndexing() && field.getDataType().getPrimitiveType() instanceof NumericDataType) {
                if (field.getIndex(field.getName()) != null
                    && ! (field.getIndex(field.getName()).getType().equals(Index.Type.VESPA))) continue;
                ScriptExpression script = field.getIndexingScript();
                Set<String> attributeNames = new HashSet<>();
                new MyVisitor(attributeNames).visit(script);
                field.setIndexingScript((ScriptExpression)new MyConverter(attributeNames).convert(script));
                warn(schema, field, "Changed to attribute because numerical indexes (field has type " +
                                    field.getDataType().getName() + ") is not currently supported." +
                                    " Index-only settings may fail. Ignore this warning for streaming search.");
            }
        }
    }

    private static class MyVisitor extends ExpressionVisitor {

        final Set<String> attributeNames;

        public MyVisitor(Set<String> attributeNames) {
            this.attributeNames = attributeNames;
        }

        @Override
        protected void doVisit(Expression exp) {
            if (exp instanceof AttributeExpression) {
                attributeNames.add(((AttributeExpression)exp).getFieldName());
            }
        }
    }

    private static class MyConverter extends ExpressionConverter {

        final Set<String> attributeNames;

        public MyConverter(Set<String> attributeNames) {
            this.attributeNames = attributeNames;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            return exp instanceof IndexExpression;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            String indexName = ((IndexExpression)exp).getFieldName();
            if (attributeNames.contains(indexName)) {
                return null;
            }
            return new AttributeExpression(indexName);
        }
    }

}
