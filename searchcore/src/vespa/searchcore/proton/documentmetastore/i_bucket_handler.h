// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/searchcore/proton/bucketdb/bucketdeltapair.h>
#include <vector>

namespace proton::bucketdb {
    class SplitBucketSession;
    class JoinBucketsSession;
    class BucketDBOwner;
}

namespace proton::documentmetastore {

/**
 * Interface for handling bucket changes relevant to the document meta store.
 */
struct IBucketHandler
{
    typedef document::BucketId BucketId;

    virtual ~IBucketHandler() = default;

    virtual bucketdb::BucketDBOwner &getBucketDB() const = 0;

    /**
     * Split the source bucket into two target buckets.
     */
    virtual bucketdb::BucketDeltaPair
    handleSplit(const bucketdb::SplitBucketSession &session) = 0;

    /**
     * Join the two source buckets into a target bucket.
     */
    virtual bucketdb::BucketDeltaPair
    handleJoin(const bucketdb::JoinBucketsSession &session) = 0;

    /*
     * Adjust active flag on all lids belonging to given bucket
     */
    virtual void updateActiveLids(const BucketId &bucketId, bool active) = 0;

    /**
     * Sets the bucket state to active / inactive.
     * Documents that are inactive will not be white-listed during search.
     **/
    virtual void setBucketState(const BucketId &bucketId, bool active) = 0;

    /**
     * Sets the bucket state to active, used when adding document db as
     * part of live reconfig.
     **/
    virtual void populateActiveBuckets(BucketId::List buckets) = 0;

};

}
