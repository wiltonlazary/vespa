// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/labels.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace search::fef;
using namespace search::fef::test;

namespace search::tensor { class DenseTensorAttribute; }
namespace vespalib::eval { class TensorSpec; }

namespace search::features::test {

struct BlueprintFactoryFixture {
    BlueprintFactory factory;
    BlueprintFactoryFixture() : factory()
    {
        setup_search_features(factory);
    }
};

struct IndexEnvironmentFixture {
    IndexEnvironment indexEnv;
    IndexEnvironmentFixture() : indexEnv()
    {
        IndexEnvironmentBuilder builder(indexEnv);
        builder.addField(FieldType::ATTRIBUTE, FieldInfo::CollectionType::SINGLE, FieldInfo::DataType::INT64, "foo");
        builder.addField(FieldType::ATTRIBUTE, FieldInfo::CollectionType::SINGLE, FieldInfo::DataType::TENSOR, "bar");
    }
};

struct FeatureDumpFixture : public IDumpFeatureVisitor {
    virtual void visitDumpFeature(const vespalib::string &) override {
        TEST_ERROR("no features should be dumped");
    }
    FeatureDumpFixture() : IDumpFeatureVisitor() {}
    ~FeatureDumpFixture() override;
};

/**
 * Fixture used by unit tests for distance and closeness rank features.
 */
struct DistanceClosenessFixture : BlueprintFactoryFixture, IndexEnvironmentFixture {
    QueryEnvironment         queryEnv;
    RankSetup                rankSetup;
    MatchDataLayout          mdl;
    MatchData::UP            match_data;
    RankProgram::UP          rankProgram;
    std::vector<TermFieldHandle> fooHandles;
    std::vector<TermFieldHandle> barHandles;
    std::shared_ptr<search::tensor::DenseTensorAttribute> tensor_attr;
    uint32_t docid_limit;
    DistanceClosenessFixture(size_t fooCnt, size_t barCnt,
                             const Labels &labels, const vespalib::string &featureName,
                             const vespalib::string& query_tensor = "");
    ~DistanceClosenessFixture();
    void set_attribute_tensor(uint32_t docid, const vespalib::eval::TensorSpec& spec);
    void set_query_tensor(const vespalib::string& query_tensor_name,
                          const vespalib::string& tensor_type,
                          const vespalib::eval::TensorSpec& spec);
    feature_t getScore(uint32_t docId) {
        return Utils::getScoreFeature(*rankProgram, docId);
    }
    void setScore(TermFieldHandle handle, uint32_t docId, feature_t score) {
        match_data->resolveTermField(handle)->setRawScore(docId, score);
    }
    void setFooScore(uint32_t i, uint32_t docId, feature_t distance) {
        ASSERT_LESS(i, fooHandles.size());
        setScore(fooHandles[i], docId, 1.0/(1.0+distance));
    }
    void setBarScore(uint32_t i, uint32_t docId, feature_t distance) {
        ASSERT_LESS(i, barHandles.size());
        setScore(barHandles[i], docId, 1.0/(1.0+distance));
    }
};

}
