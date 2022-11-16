// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/properties.h>
#include "indexenvironment.h"

namespace streaming {

/**
 * Implementation of the feature execution framework
 * query environment API for the search visitor.
 **/
class QueryEnvironment : public search::fef::IQueryEnvironment
{
private:
    const IndexEnvironment                     &_indexEnv;
    const search::fef::Properties              &_properties;
    search::attribute::IAttributeContext::UP    _attrCtx;
    std::vector<const search::fef::ITermData *> _queryTerms;
    std::vector<search::common::GeoLocationSpec> _locations;

public:
    typedef std::unique_ptr<QueryEnvironment> UP;

    QueryEnvironment(const vespalib::string & location,
                     const IndexEnvironment & indexEnv,
                     const search::fef::Properties & properties,
                     const search::IAttributeManager * attrMgr = nullptr);
    ~QueryEnvironment();

    void addGeoLocation(const vespalib::string &field, const vespalib::string &location);

    // inherit documentation
    virtual const search::fef::Properties & getProperties() const override { return _properties; }

    // inherit documentation
    virtual uint32_t getNumTerms() const override { return _queryTerms.size(); }

    // inherit documentation
    virtual const search::fef::ITermData *getTerm(uint32_t idx) const override {
        if (idx >= _queryTerms.size()) {
            return nullptr;
        }
        return _queryTerms[idx];
    }

    // inherit documentation
    GeoLocationSpecPtrs getAllLocations() const override;

    // inherit documentation
    virtual const search::attribute::IAttributeContext & getAttributeContext() const override { return *_attrCtx; }

    double get_average_field_length(const vespalib::string &) const override { return 1.0; }

    // inherit documentation
    virtual const search::fef::IIndexEnvironment & getIndexEnvironment() const override { return _indexEnv; }

    void addTerm(const search::fef::ITermData *term) { _queryTerms.push_back(term); }
};

} // namespace streaming

