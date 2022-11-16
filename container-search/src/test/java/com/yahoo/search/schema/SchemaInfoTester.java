// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class SchemaInfoTester {

    private final SchemaInfo schemaInfo;

    SchemaInfoTester() {
        this.schemaInfo = createSchemaInfo();
    }

    SchemaInfo schemaInfo() { return schemaInfo; }

    Query query(String sources, String restrict) {
        Map<String, String> params = new HashMap<>();
        if ( ! sources.isEmpty())
            params.put("sources", sources);
        if ( ! restrict.isEmpty())
            params.put("restrict", restrict);
        return new Query.Builder().setSchemaInfo(schemaInfo)
                                  .setRequestMap(params)
                                  .build();
    }

    void assertInput(TensorType expectedType, String sources, String restrict, String rankProfile, String feature) {
        assertEquals(expectedType,
                     schemaInfo.newSession(query(sources, restrict)).rankProfileInput(feature, rankProfile));
    }

    void assertInputConflict(TensorType expectedType, String sources, String restrict, String rankProfile, String feature) {
        try {
            assertInput(expectedType, sources, restrict, rankProfile, feature);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Conflicting input type declarations for '" + feature + "'",
                         e.getMessage().split(":")[0]);
        }
    }

    static SchemaInfo createSchemaInfo() {
        List<Schema> schemas = new ArrayList<>();
        RankProfile common = new RankProfile.Builder("commonProfile")
                .addInput("query(myTensor1)", TensorType.fromSpec("tensor(a{},b{})"))
                .addInput("query(myTensor2)", TensorType.fromSpec("tensor(x[2],y[2])"))
                .addInput("query(myTensor3)", TensorType.fromSpec("tensor(x[2],y[2])"))
                .addInput("query(myTensor4)", TensorType.fromSpec("tensor<float>(x[5])"))
                .build();
        schemas.add(new Schema.Builder("a")
                            .add(common)
                            .add(new RankProfile.Builder("inconsistent")
                                         .addInput("query(myTensor1)", TensorType.fromSpec("tensor(a{},b{})"))
                                         .build())
                            .add(new DocumentSummary.Builder("testSummary")
                                         .add(new DocumentSummary.Field("field1", "string"))
                                         .add(new DocumentSummary.Field("field2", "integer"))
                                         .setDynamic(true)
                                         .build())
                            .build());
        schemas.add(new Schema.Builder("b")
                            .add(common)
                            .add(new RankProfile.Builder("inconsistent")
                                         .addInput("query(myTensor1)", TensorType.fromSpec("tensor(x[10])"))
                                         .build())
                            .add(new RankProfile.Builder("bOnly")
                                         .addInput("query(myTensor1)", TensorType.fromSpec("tensor(a{},b{})"))
                                         .build())
                            .build());
        Map<String, List<String>> clusters = new HashMap<>();
        clusters.put("ab", List.of("a", "b"));
        clusters.put("a", List.of("a"));
        return new SchemaInfo(schemas, clusters);
    }

    /** Creates the same schema info as createSchemaInfo from config objects. */
    static SchemaInfo createSchemaInfoFromConfig() {
        var indexInfoConfig = new IndexInfoConfig.Builder();

        var rankProfileCommon = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileCommon.name("commonProfile");
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(a{},b{})"));
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor2)").type("tensor(x[2],y[2])"));
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor3)").type("tensor(x[2],y[2])"));
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor4)").type("tensor<float>(x[5])"));

        var schemaInfoInfoConfig = new SchemaInfoConfig.Builder();

        // ----- Schema A
        var schemaA = new SchemaInfoConfig.Schema.Builder();
        schemaA.name("a");

        schemaA.rankprofile(rankProfileCommon);
        var rankProfileInconsistentA = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileInconsistentA.name("inconsistent");
        rankProfileInconsistentA.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(a{},b{})"));
        schemaA.rankprofile(rankProfileInconsistentA);

        var summaryClass = new SchemaInfoConfig.Schema.Summaryclass.Builder();
        summaryClass.name("testSummary");
        var field1 = new SchemaInfoConfig.Schema.Summaryclass.Fields.Builder();
        field1.name("field1").type("string").dynamic(true);
        summaryClass.fields(field1);
        var field2 = new SchemaInfoConfig.Schema.Summaryclass.Fields.Builder();
        field2.name("field2").type("integer");
        summaryClass.fields(field2);
        schemaA.summaryclass(summaryClass);

        schemaInfoInfoConfig.schema(schemaA);

        // ----- Schema B
        var schemaB = new SchemaInfoConfig.Schema.Builder();
        schemaB.name("b");

        schemaB.rankprofile(rankProfileCommon);
        var rankProfileInconsistentB = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileInconsistentB.name("inconsistent");
        rankProfileInconsistentB.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(x[10])"));
        schemaB.rankprofile(rankProfileInconsistentB);
        var rankProfileBOnly = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileBOnly.name("bOnly");
        rankProfileBOnly.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(a{},b{})"));
        schemaB.rankprofile(rankProfileBOnly);

        schemaInfoInfoConfig.schema(schemaB);

        // ----- Info about which schemas are in which clusters
        var qrSearchersConfig = new QrSearchersConfig.Builder();
        var clusterAB = new QrSearchersConfig.Searchcluster.Builder();
        clusterAB.name("ab");
        clusterAB.searchdef("a").searchdef("b");
        qrSearchersConfig.searchcluster(clusterAB);
        var clusterA = new QrSearchersConfig.Searchcluster.Builder();
        clusterA.name("a");
        clusterA.searchdef("a");
        qrSearchersConfig.searchcluster(clusterA);

        return new SchemaInfo(indexInfoConfig.build(), schemaInfoInfoConfig.build(), qrSearchersConfig.build());
    }

}
