// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"

namespace document {
class DocumentTypeRepo;
class DocumentUpdate;
}

namespace proton {

class UpdateOperation : public DocumentOperation
{
private:
    using DocumentUpdateSP = std::shared_ptr<document::DocumentUpdate>;
    DocumentUpdateSP _upd;
    UpdateOperation(Type type, const document::BucketId &bucketId,
                    Timestamp timestamp, DocumentUpdateSP upd);
    void serializeUpdate(vespalib::nbostream &os) const;
    void deserializeUpdate(vespalib::nbostream && is, const document::DocumentTypeRepo &repo);
public:
    UpdateOperation();
    UpdateOperation(Type type);
    UpdateOperation(const document::BucketId &bucketId,
                    Timestamp timestamp, DocumentUpdateSP upd);
    ~UpdateOperation() override;
    const DocumentUpdateSP &getUpdate() const { return _upd; }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    void verifyUpdate(const document::DocumentTypeRepo &repo);
    vespalib::string toString() const override;
};

} // namespace proton
