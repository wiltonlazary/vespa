// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::AssignValueUpdate
 * @ingroup document
 *
 * @brief Represents an update that specifies an assignment of a value to a
 *        field, completely overwriting the previous value.
 *
 * Note that for multi-value types, the entire collection is overwritten with a
 * new collection.
 */
#pragma once

#include "valueupdate.h"
#include <vespa/document/fieldvalue/fieldvalue.h>

namespace document {

class AssignValueUpdate final : public ValueUpdate {
    std::unique_ptr<FieldValue> _value;

    ACCEPT_UPDATE_VISITOR;
public:
    AssignValueUpdate();
    explicit AssignValueUpdate(std::unique_ptr<FieldValue> value);
    AssignValueUpdate(const AssignValueUpdate& value) = delete;
    AssignValueUpdate & operator=(const AssignValueUpdate& value) = delete;
    ~AssignValueUpdate() override;

    bool operator==(const ValueUpdate& other) const override;

    bool hasValue() const { return bool(_value); }
    const FieldValue& getValue() const { return *_value; }

    AssignValueUpdate& setValue(const FieldValue* value) {
        _value.reset(value ? value->clone() : 0);
        return *this;
    }

    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & buffer) override;
};

}
