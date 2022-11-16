// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idatastore.h"
#include <vespa/searchlib/common/i_compactable_lid_space.h>
#include <vespa/searchlib/query/base.h>
#include <future>

namespace document {
    class Document;
    class DocumentTypeRepo;
}

namespace vespalib {
struct CacheStats;
class nbostream;
}

namespace search {

class IDocumentStoreReadVisitor
{
public:
    using DocumentSP = std::shared_ptr<document::Document>;
    virtual ~IDocumentStoreReadVisitor() = default;
    virtual void visit(uint32_t lid, const DocumentSP &doc) = 0;
    virtual void visit(uint32_t lid) = 0;
};

class IDocumentStoreRewriteVisitor
{
public:
    using DocumentSP = std::shared_ptr<document::Document>;
    virtual ~IDocumentStoreRewriteVisitor() = default;
    virtual void visit(uint32_t lid, const DocumentSP &doc) = 0;
};

class IDocumentStoreVisitorProgress
{
public:
    virtual ~IDocumentStoreVisitorProgress() = default;

    virtual void updateProgress(double progress) = 0;
};

class IDocumentVisitor
{
public:
    using DocumentUP = std::unique_ptr<document::Document>;
    virtual ~IDocumentVisitor() = default;
    virtual void visit(uint32_t lid, DocumentUP doc) = 0;
    virtual bool allowVisitCaching() const = 0;
private:
};

/**
 * Simple document store that contains serialized Document instances.
 * updates will be held in memory until flush() is called.
 * Uses a Local ID as key.
 **/
class IDocumentStore : public common::ICompactableLidSpace
{
public:
    /**
     * Convenience typedef for a shared pointer to this class.
     **/
    using SP = std::shared_ptr<IDocumentStore>;
    using LidVector = std::vector<uint32_t>;
    using DocumentUP = std::unique_ptr<document::Document>;

    /**
     * Make a Document from a stored serialized data blob.
     * @param lid The local ID associated with the document.
     * @return NULL if there is no document associated with the lid.
     **/
    virtual DocumentUP read(DocumentIdT lid, const document::DocumentTypeRepo &repo) const = 0;
    virtual void visit(const LidVector & lidVector, const document::DocumentTypeRepo &repo, IDocumentVisitor & visitor) const;

    /**
     * Serialize and store a document.
     * @param doc The document to store
     * @param lid The local ID associated with the document
     **/
    virtual void write(uint64_t syncToken, DocumentIdT lid, const document::Document& doc) = 0;
    virtual void write(uint64_t synkToken, DocumentIdT lid, const vespalib::nbostream & os) = 0;

    /**
     * Mark a document as removed. A later read() will return NULL for the given lid.
     * @param lid The local ID associated with the document
     **/
    virtual void remove(uint64_t syncToken, DocumentIdT lid) = 0;

    /**
     * Flush all in-memory updates to disk.
     **/
    virtual void flush(uint64_t syncToken) = 0;

    virtual uint64_t initFlush(uint64_t synctoken) = 0;

    /**
     * If possible compact the disk.
     **/
    virtual void compactBloat(uint64_t syncToken) = 0;
    virtual void compactSpread(uint64_t syncToken) = 0;

    /**
     * The sync token used for the last successful flush() operation,
     * or 0 if no flush() has been performed yet.
     * @return Last flushed sync token.
     **/
    virtual uint64_t lastSyncToken() const = 0;

    /*
     * The sync token used for last write operation.
     */
    virtual uint64_t tentativeLastSyncToken() const = 0;

    /**
     * The time of the last flush operation,
     * or 0 if no flush has been performed yet.
     * @return Time of last flush.
     **/
    virtual vespalib::system_time getLastFlushTime() const = 0;

    /**
     * Get the number of entries (including removed IDs
     * or gaps in the local ID sequence) in the document store.
     */
    virtual uint32_t getDocIdLimit() const = 0;

    /**
     * Calculate memory used by this instance.  During flush() actual
     * memory usage may be approximately twice the reported amount.
     * @return memory usage (in bytes)
     **/
    virtual size_t memoryUsed() const = 0;

    /**
     * Calculates memory that is used for meta data by this instance. Calling
     * flush() does not free this memory.
     * @return memory usage (in bytes)
     **/
    virtual size_t memoryMeta() const = 0;

    /**
     * Calculates how much disk is used
     * @return disk space used.
     */
    virtual size_t getDiskFootprint() const = 0;
    /**
     * Calculates how much wasted space there is.
     * @return disk bloat.
     */
    virtual size_t getDiskBloat() const = 0;

    /**
     * Calculates the gain from keeping buckets close. It is converted to diskbloat
     * so it can be prioritized accordingly.
     * @return spread as disk bloat.
     */
    virtual size_t getMaxSpreadAsBloat() const = 0;

    /**
     * Returns statistics about the cache.
     */
    virtual vespalib::CacheStats getCacheStats() const = 0;

    /**
     * Returns the base directory from which all structures are stored.
     **/
    virtual const vespalib::string & getBaseDir() const = 0;

    /**
     * Visit all documents found in document store.
     */
    virtual void
    accept(IDocumentStoreReadVisitor &visitor,
           IDocumentStoreVisitorProgress &visitorProgress,
           const document::DocumentTypeRepo &repo) = 0;

    /**
     * Visit all documents found in document store.
     */
    virtual void
    accept(IDocumentStoreRewriteVisitor &visitor,
           IDocumentStoreVisitorProgress &visitorProgress,
           const document::DocumentTypeRepo &repo) = 0;

    /**
     * Return cost of visiting all documents found in document store.
     */
    virtual double getVisitCost() const = 0;

    /*
     * Return brief stats for data store.
     */
    virtual DataStoreStorageStats getStorageStats() const = 0;

    /*
     * Return the memory usage for document store.
     */
    virtual vespalib::MemoryUsage getMemoryUsage() const = 0;

    /*
     * Return detailed stats about underlying files for data store.
     */
    virtual std::vector<DataStoreFileChunkStats> getFileChunkStats() const = 0;
};

} // namespace search

