// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::DocumentUpdate
 * @ingroup document
 *
 * @brief Holds a set of operation that may be used to update a document.
 *
 * Stores update values for fields defined in the common
 * VESPA field repository. The "key" for a document is the document id, a
 * string that must conform to the vespa URI schemes.
 *
 * The following update operations are supported: assign, append and remove.
 * Append and remove are only supported for multivalued fields (arrays and
 * weightedsets).
 *
 * Each document update has a document type, which defines what documents this
 * update may be applied to and what fields may be updated in that document
 * update.  The document type for the update is given as a pointer in the
 * document update object's constructor.<br>
 * A DocumentUpdate has a vector of DocumentUpdate::Update objects
 *
 * @see DocumentId
 * @see IdString
 * @see documentmanager.h
 */
#pragma once

#include "fieldupdate.h"
#include "fieldpathupdate.h"
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace document {

class ByteBuffer;
class Document;
class VespaDocumentSerializer;
/**
 * Class containing a document update.  In vespa 5.0, support for field
 * path updates was added, and a new serialization format was
 * introduced while keeping the old one.
 */
class DocumentUpdate
{
public:
    using UP = std::unique_ptr<DocumentUpdate>;
    using SP = std::shared_ptr<DocumentUpdate>;
    using FieldUpdateV = std::vector<FieldUpdate>;
    using FieldPathUpdateV = std::vector<std::unique_ptr<FieldPathUpdate>>;
    using XmlOutputStream = vespalib::xml::XmlOutputStream;

    /**
     * Create new style document update, possibly with field path updates.
     */
    static DocumentUpdate::UP createHEAD(const DocumentTypeRepo & repo, vespalib::nbostream & stream);
    static DocumentUpdate::UP createHEAD(const DocumentTypeRepo & repo, vespalib::nbostream && stream);

    DocumentUpdate();
    /**
     * The document type is not strictly needed, as we know this at applyTo()
     * time, but search does not use applyTo() code to do the update, and don't
     * know the document type of their objects, so this is supplied for
     * convinience. It also makes it possible to check updates for sanity at
     * creation time.
     *
     * @param type The document type that this update is applicable for.
     * @param id The identifier of the document that this update is created for.
     */
    DocumentUpdate(const DocumentTypeRepo & repo, const DataType &type, const DocumentId& id);

    DocumentUpdate(const DocumentUpdate &) = delete;
    DocumentUpdate & operator = (const DocumentUpdate &) = delete;
    DocumentUpdate(DocumentUpdate &&) = delete;
    DocumentUpdate & operator = (DocumentUpdate &&) = delete;
    ~DocumentUpdate();

    bool operator==(const DocumentUpdate&) const;
    bool operator!=(const DocumentUpdate & rhs) const { return ! (*this == rhs); }
	
    const DocumentId& getId() const { return _documentId; }

    /**
     * Applies this update object to the given {@link Document} object.
     *
     * @param doc The document to apply this update to. Must be of the same
     * type as this.
     */
    void applyTo(Document& doc) const;

    DocumentUpdate& addUpdate(FieldUpdate && update);
    DocumentUpdate& addFieldPathUpdate(std::unique_ptr<FieldPathUpdate> update);

    /** @return The list of updates. */
    const FieldUpdateV & getUpdates() const;

    /** @return The list of fieldpath updates. */
    const FieldPathUpdateV & getFieldPathUpdates() const;

    void eagerDeserialize() const;

    /** @return The type of document this update is for. */
    const DocumentType& getType() const;

    void serializeHEAD(vespalib::nbostream &stream) const;

    /**
     * Sets whether this update should create the document it updates if that document does not exist.
     * In this case an empty document is created before the update is applied.
     */
    void setCreateIfNonExistent(bool value);

    /**
     * Gets whether this update should create the document it updates if that document does not exist.
     */
    bool getCreateIfNonExistent() const;

    int serializeFlags(int size_) const;

    // Only used for debugging
    void print(std::ostream& out, bool verbose, const std::string& indent) const;
    void printXml(XmlOutputStream&) const;
    std::string toXml(const std::string& indent) const;
private:
    DocumentId              _documentId; // The ID of the document to update.
    const DataType         *_type; // The type of document this update is for.
    const DocumentTypeRepo *_repo;
    vespalib::nbostream     _backing;
    FieldUpdateV            _updates; // The list of field updates.
    FieldPathUpdateV        _fieldPathUpdates;
    bool                    _createIfNonExistent;
    bool                    _needHardReserialize;

    int deserializeFlags(int sizeAndFlags);
    void initHEAD(const DocumentTypeRepo & repo, vespalib::nbostream & stream);
    void initHEAD(const DocumentTypeRepo & repo, vespalib::nbostream && stream);
    void deserializeBody(const DocumentTypeRepo &repo, vespalib::nbostream &stream);
    void lazyDeserialize(const DocumentTypeRepo & repo, vespalib::nbostream & stream);
    void ensureDeserialized() const;
    void serializeHeader();
    void reserialize();
    friend VespaDocumentSerializer;
};

std::ostream &operator<<(std::ostream &out, const DocumentUpdate &update);

}
