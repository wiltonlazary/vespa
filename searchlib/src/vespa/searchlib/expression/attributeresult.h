// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resultnode.h"
#include <vespa/searchcommon/attribute/iattributevector.h>

namespace search::expression {

class AttributeResult : public ResultNode
{
public:
    typedef std::unique_ptr<AttributeResult> UP;
    DECLARE_RESULTNODE(AttributeResult);
    AttributeResult() : _attribute(nullptr), _docId(0) { }
    AttributeResult(const attribute::IAttributeVector * attribute, DocId docId) :
        _attribute(attribute),
        _docId(docId)
    { }
    void setDocId(DocId docId) { _docId = docId; }
    const search::attribute::IAttributeVector *getAttribute() const { return _attribute; }
    DocId getDocId() const { return _docId; }
private:
    int64_t onGetInteger(size_t index) const override { (void) index; return _attribute->getInt(_docId); }
    double onGetFloat(size_t index)    const override { (void) index; return _attribute->getFloat(_docId); }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override {
        (void) index;
        const char * t = _attribute->getString(_docId, buf.str(), buf.size());
        return ConstBufferRef(t, strlen(t));
    }
    int64_t onGetEnum(size_t index) const override { (void) index; return (static_cast<int64_t>(_attribute->getEnum(_docId))); }
    void set(const search::expression::ResultNode&) override { }
    size_t hash() const override { return _docId; }

    const search::attribute::IAttributeVector *_attribute;
    DocId                                      _docId;
};

}
