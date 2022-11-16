// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::query { class Node; }

namespace search::queryeval {

class Blueprint;
class IRequestContext;
class FieldSpec;
class FieldSpecList;

/**
 * Abstract class extended by components to expose content that can be
 * searched by a query term. A Searchable component supports searching
 * in one or more named fields. The Blueprint created by a Searchable
 * is an intermediate query representation that is later used to
 * create the actual search iterators used to produce matches.
 **/
class Searchable
{
protected:
    /**
     * Create a blueprint searching a single field.
     *
     * @return blueprint
     * @param requestContext that belongs to the query
     * @param field the field to search
     * @param term the query tree term
     **/
    virtual std::unique_ptr<Blueprint> createBlueprint(const IRequestContext & requestContext,
                                                       const FieldSpec &field,
                                                       const search::query::Node &term) = 0;

public:
    typedef std::shared_ptr<Searchable> SP;

    Searchable() = default;

    /**
     * Create a blueprint searching a set of fields. The default
     * implementation of this function will create blueprints for
     * individual fields and combine them with an OR blueprint.
     *
     * @return blueprint
     * @param requestContext that belongs to the query
     * @param fields the set of fields to search
     * @param term the query tree term
     **/
    virtual std::unique_ptr<Blueprint> createBlueprint(const IRequestContext & requestContext,
                                                       const FieldSpecList &fields,
                                                       const search::query::Node &term);
    virtual ~Searchable() = default;
};

}
