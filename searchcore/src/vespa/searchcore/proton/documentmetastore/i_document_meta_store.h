// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "gid_to_lid_map_key.h"
#include "lid_gid_key_comparator.h"
#include "i_simple_document_meta_store.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/searchlib/common/commit_param.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>

namespace proton::documentmetastore { class OperationListener; }

namespace proton {

/**
 * Interface used to manage the documents that are contained
 * in a document sub database with related meta data.
 *
 * A document meta store will have storage of <lid, meta data> pairs
 * (local document id, meta data (including global document id)) and
 * mapping from lid -> meta data and gid -> lid.
 **/
struct IDocumentMetaStore : public search::IDocumentMetaStore,
                            public ISimpleDocumentMetaStore
{
    using search::IDocumentMetaStore::DocId;
    using search::IDocumentMetaStore::GlobalId;
    using search::IDocumentMetaStore::BucketId;
    using search::IDocumentMetaStore::Timestamp;
    using CommitParam = search::CommitParam;
    using SerialNum = search::SerialNum;

    // Typedef for the tree used to map from gid -> lid
    // Lids are stored as keys in the tree, sorted by their gid counterpart.
    // The LidGidKeyComparator class maps from lids -> metadata by using the metadata store.
    // TODO(geirst): move this typedef and iterator functions away from this interface.
    using TreeType = vespalib::btree::BTree<documentmetastore::GidToLidMapKey,
                                            vespalib::btree::BTreeNoLeafData,
                                            vespalib::btree::NoAggregated,
                                            const documentmetastore::LidGidKeyComparator &>;
    using Iterator = TreeType::Iterator;
    using SP = std::shared_ptr<IDocumentMetaStore>;

    virtual ~IDocumentMetaStore() = default;

    /**
     * Constructs a new underlying free list for lids.
     * This should be done after a load() and calls to put() and remove().
     **/
    virtual void constructFreeList() = 0;

    virtual Iterator begin() const = 0;
    virtual Iterator lowerBound(const BucketId &bucketId) const = 0;
    virtual Iterator upperBound(const BucketId &bucketId) const = 0;
    virtual Iterator lowerBound(const GlobalId &gid) const = 0;
    virtual Iterator upperBound(const GlobalId &gid) const = 0;
    virtual void getLids(const BucketId &bucketId, std::vector<DocId> &lids) = 0;

    /*
     * Called by document db executor to hold unblocking of shrinking of lid
     * space after all outstanding holdLid() operations at the time of
     * compactLidSpace() call have been completed.
     */
    virtual void holdUnblockShrinkLidSpace() = 0;

    // Functions that are also defined search::AttributeVector
    virtual void commit(const CommitParam & param) = 0;
    virtual void reclaim_unused_memory() = 0;
    virtual bool canShrinkLidSpace() const = 0;
    virtual SerialNum getLastSerialNum() const = 0;

    /*
     * Adjust committedDocIdLimit downwards and prepare for shrinking
     * of lid space.
     *
     * NOTE: Must call unblockShrinkLidSpace() before lid space can
     * be shrunk.
     */
    virtual void compactLidSpace(DocId wantedLidLimit) = 0;

    virtual void set_operation_listener(std::shared_ptr<documentmetastore::OperationListener> op_listener) = 0;

};

} // namespace proton

