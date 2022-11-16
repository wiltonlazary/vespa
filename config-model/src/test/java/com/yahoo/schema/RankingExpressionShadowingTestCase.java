// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.collections.Pair;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.RawRankProfile;
import com.yahoo.schema.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author lesters
 */
public class RankingExpressionShadowingTestCase extends AbstractSchemaTestCase {

    @Test
    void testBasicFunctionShadowing() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        function sin(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: sin(2)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile test = rankProfileRegistry.get(s, "test").compile(new QueryProfileRegistry(), new ImportedMlModels());
        List<Pair<String, String>> testRankProperties = createRawRankProfile(test, new QueryProfileRegistry(), s).configProperties();
        assertEquals("(rankingExpression(sin@).rankingScript, 2 * 2)",
                censorBindingHash(testRankProperties.get(0).toString()));
        assertEquals("(rankingExpression(sin).rankingScript, x * x)",
                testRankProperties.get(1).toString());
        assertEquals("(vespa.rank.firstphase, rankingExpression(sin@))",
                censorBindingHash(testRankProperties.get(2).toString()));
    }


    @Test
    void testMultiLevelFunctionShadowing() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        function tan(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        function cos(x) {\n" +
                        "            expression: tan(x)\n" +
                        "        }\n" +
                        "        function sin(x) {\n" +
                        "            expression: cos(x)\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: sin(2)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile test = rankProfileRegistry.get(s, "test").compile(new QueryProfileRegistry(), new ImportedMlModels());
        List<Pair<String, String>> testRankProperties = createRawRankProfile(test, new QueryProfileRegistry(), s).configProperties();
        assertEquals("(rankingExpression(tan@).rankingScript, 2 * 2)",
                censorBindingHash(testRankProperties.get(0).toString()));
        assertEquals("(rankingExpression(cos@).rankingScript, rankingExpression(tan@))",
                censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("(rankingExpression(sin@).rankingScript, rankingExpression(cos@))",
                censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("(rankingExpression(tan).rankingScript, x * x)",
                testRankProperties.get(3).toString());
        assertEquals("(rankingExpression(tan@).rankingScript, x * x)",
                censorBindingHash(testRankProperties.get(4).toString()));
        assertEquals("(rankingExpression(cos).rankingScript, rankingExpression(tan@))",
                censorBindingHash(testRankProperties.get(5).toString()));
        assertEquals("(rankingExpression(cos@).rankingScript, rankingExpression(tan@))",
                censorBindingHash(testRankProperties.get(6).toString()));
        assertEquals("(rankingExpression(sin).rankingScript, rankingExpression(cos@))",
                censorBindingHash(testRankProperties.get(7).toString()));
        assertEquals("(vespa.rank.firstphase, rankingExpression(sin@))",
                censorBindingHash(testRankProperties.get(8).toString()));
    }

    @Test
    void testFunctionShadowingArguments() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        function sin(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: cos(sin(2*2)) + sin(cos(1+4))\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile test = rankProfileRegistry.get(s, "test").compile(new QueryProfileRegistry(), new ImportedMlModels());
        List<Pair<String, String>> testRankProperties = createRawRankProfile(test, new QueryProfileRegistry(), s).configProperties();
        assertEquals("(rankingExpression(sin@).rankingScript, 4.0 * 4.0)",
                censorBindingHash(testRankProperties.get(0).toString()));
        assertEquals("(rankingExpression(sin@).rankingScript, cos(5.0) * cos(5.0))",
                censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("(rankingExpression(sin).rankingScript, x * x)",
                testRankProperties.get(2).toString());
        assertEquals("(vespa.rank.firstphase, rankingExpression(firstphase))",
                censorBindingHash(testRankProperties.get(3).toString()));
        assertEquals("(rankingExpression(firstphase).rankingScript, cos(rankingExpression(sin@)) + rankingExpression(sin@))",
                censorBindingHash(testRankProperties.get(4).toString()));
    }

    @Test
    void testNeuralNetworkSetup() throws ParseException {
        // Note: the type assigned to query profile and constant tensors here is not the correct type
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        QueryProfileRegistry queryProfiles = queryProfileWith("query(q)", "tensor(input[1])");
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry, queryProfiles);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        function relu(x) {\n" + // relu is a built in function, redefined here
                        "            expression: max(1.0, x)\n" +
                        "        }\n" +
                        "        function hidden_layer() {\n" +
                        "            expression: relu(sum(query(q) * constant(W_hidden), input) + constant(b_input))\n" +
                        "        }\n" +
                        "        function final_layer() {\n" +
                        "            expression: sigmoid(sum(hidden_layer * constant(W_final), hidden) + constant(b_final))\n" +
                        "        }\n" +
                        "        second-phase {\n" +
                        "            expression: sum(final_layer)\n" +
                        "        }\n" +
                        "    }\n" +
                        "    constant W_hidden {\n" +
                        "        type: tensor(hidden[1])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "    constant b_input {\n" +
                        "        type: tensor(hidden[1])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "    constant W_final {\n" +
                        "        type: tensor(final[1])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "    constant b_final {\n" +
                        "        type: tensor(final[1])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile test = rankProfileRegistry.get(s, "test").compile(queryProfiles, new ImportedMlModels());
        List<Pair<String, String>> testRankProperties = createRawRankProfile(test, queryProfiles, s).configProperties();
        assertEquals("(rankingExpression(autogenerated_ranking_feature@).rankingScript, reduce(query(q) * constant(W_hidden), sum, input) + constant(b_input))",
                censorBindingHash(testRankProperties.get(0).toString()));
        assertEquals("(rankingExpression(relu@).rankingScript, max(1.0,rankingExpression(autogenerated_ranking_feature@)))",
                censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("(rankingExpression(hidden_layer).rankingScript, rankingExpression(relu@))",
                censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("(rankingExpression(final_layer).rankingScript, sigmoid(reduce(rankingExpression(hidden_layer) * constant(W_final), sum, hidden) + constant(b_final)))",
                testRankProperties.get(4).toString());
        assertEquals("(rankingExpression(relu).rankingScript, max(1.0,x))",
                testRankProperties.get(6).toString());
        assertEquals("(vespa.rank.secondphase, rankingExpression(secondphase))",
                testRankProperties.get(7).toString());
        assertEquals("(rankingExpression(secondphase).rankingScript, reduce(rankingExpression(final_layer), sum))",
                testRankProperties.get(8).toString());
    }

    private static RawRankProfile createRawRankProfile(RankProfile profile, QueryProfileRegistry queryProfiles, Schema schema) {
        return new RawRankProfile(profile,
                new LargeRankingExpressions(new MockFileRegistry()),
                queryProfiles,
                new ImportedMlModels(),
                new AttributeFields(schema),
                new TestProperties());
    }

    private QueryProfileRegistry queryProfileWith(String field, String type) {
        QueryProfileType queryProfileType = new QueryProfileType("root");
        queryProfileType.addField(new FieldDescription(field, type));
        QueryProfileRegistry queryProfileRegistry = new QueryProfileRegistry();
        queryProfileRegistry.getTypeRegistry().register(queryProfileType);
        QueryProfile profile = new QueryProfile("default");
        profile.setType(queryProfileType);
        queryProfileRegistry.register(profile);
        return queryProfileRegistry;
    }

    private String censorBindingHash(String s) {
        StringBuilder b = new StringBuilder();
        boolean areInHash = false;
        for (int i = 0; i < s.length() ; i++) {
            char current = s.charAt(i);
            if ( ! Character.isLetterOrDigit(current)) // end of hash
                areInHash = false;
            if ( ! areInHash)
                b.append(current);
            if (current == '@') // start of hash
                areInHash = true;
        }
        return b.toString();
    }

}
