// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::ValueUpdate
 * @ingroup document
 *
 * @brief Superclass for all types of field value update operations.
 *
 * It declares the interface required for all value updates.
 *
 * Furthermore, this class inherits from Printable without implementing its
 * virtual {@link Printable#print} function, so that all subclasses must also
 * implement a human readable output format.
 *
 * This class is a serializable, serializing its content to a buffer. It is
 * however not a deserializable, as it does not serialize the datatype of the
 * content it serializes, such that it needs to get a datatype specified upon
 * deserialization.
 */
#pragma once

#include "updatevisitor.h"
#include <vespa/document/util/identifiableid.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/xmlstream.h>

namespace document {

class DocumentTypeRepo;
class Field;
class FieldValue;
class DataType;

class ValueUpdate
{
protected:
    using nbostream = vespalib::nbostream;
public:
    using XmlOutputStream = vespalib::xml::XmlOutputStream;

    /**
     * Create a value update object from the given stream.
     *
     * @param type A data type that describes the content of the buffer.
     * @param buffer The stream that containes the serialized update.
     */
    static std::unique_ptr<ValueUpdate> createInstance(const DocumentTypeRepo& repo, const DataType& type, nbostream & buffer);

    /** Define all types of value updates. */
    enum ValueUpdateType {
        Add        = IDENTIFIABLE_CLASSID(AddValueUpdate),
        Arithmetic = IDENTIFIABLE_CLASSID(ArithmeticValueUpdate),
        Assign     = IDENTIFIABLE_CLASSID(AssignValueUpdate),
        Clear      = IDENTIFIABLE_CLASSID(ClearValueUpdate),
        Map        = IDENTIFIABLE_CLASSID(MapValueUpdate),
        Remove     = IDENTIFIABLE_CLASSID(RemoveValueUpdate),
        TensorModify = IDENTIFIABLE_CLASSID(TensorModifyUpdate),
        TensorAdd = IDENTIFIABLE_CLASSID(TensorAddUpdate),
        TensorRemove = IDENTIFIABLE_CLASSID(TensorRemoveUpdate)
    };

    virtual ~ValueUpdate() = default;
    virtual bool operator==(const ValueUpdate&) const = 0;
    bool operator != (const ValueUpdate & rhs) const { return ! (*this == rhs); }

    /**
     * Recursively checks the compatibility of this value update as
     * applied to the given document field.
     *
     * @throws IllegalArgumentException Thrown if this value update is not compatible.
     */
    virtual void checkCompatibility(const Field& field) const = 0;

    /**
     * Applies this value update to the given field value.
     *
     * @return True if value is updated, false if value should be removed.
     */
    virtual bool applyTo(FieldValue& value) const = 0;

    /**
     * Deserializes the given stream into an instance of an update object.
     *
     * @param type A data type that describes the content of the stream.
     * @param buffer The stream that contains the serialized update object.
     */
    virtual void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & stream) = 0;

    /** @return The operation type. */
    ValueUpdateType getType() const noexcept { return _type; }
    const char * className() const noexcept;
    /**
     * Visit this fieldvalue for double dispatch.
     */
    virtual void accept(UpdateVisitor &visitor) const = 0;

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const = 0;
    virtual void printXml(XmlOutputStream& out) const = 0;
protected:
    ValueUpdate(ValueUpdateType type) : _type(type) { }
private:
    static std::unique_ptr<ValueUpdate> create(ValueUpdateType type);
    ValueUpdateType _type;
};

std::ostream& operator<<(std::ostream& out, const ValueUpdate & p);

}

