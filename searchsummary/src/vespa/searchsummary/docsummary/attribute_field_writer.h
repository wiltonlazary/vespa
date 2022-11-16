// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/memory.h>
#include <memory>

namespace search::attribute { class IAttributeVector; }
namespace vespalib { class Stash; }
namespace vespalib::slime { struct Cursor; }

namespace search::docsummary {

/*
 * This class reads values from a struct field attribute and inserts
 * them into proper position in an array of struct or map of struct.
 * If the value to be inserted is considered to be undefined then
 * the value is not inserted.
 */
class AttributeFieldWriter
{
protected:
    const vespalib::Memory                     _fieldName;
    explicit AttributeFieldWriter(vespalib::Memory fieldName);
public:
    virtual ~AttributeFieldWriter();
    virtual uint32_t fetch(uint32_t docId) = 0;
    virtual void print(uint32_t idx, vespalib::slime::Cursor &cursor) = 0;
    // Create a new attribute field writer which is owned by stash
    static AttributeFieldWriter& create(vespalib::Memory fieldName, const search::attribute::IAttributeVector& attr, vespalib::Stash& stash, bool keep_empty_strings = false);
};

}
