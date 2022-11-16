// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <mutex>

namespace storage {

namespace lib {
class ClusterState;
class Distribution;
}

/**
 * Class representing a bucket space (with associated bucket database) on a content node.
 */
class ContentBucketSpace {
private:
    document::BucketSpace _bucketSpace;
    StorBucketDatabase _bucketDatabase;
    mutable std::mutex _lock;
    std::shared_ptr<const lib::ClusterState> _clusterState;
    std::shared_ptr<const lib::Distribution> _distribution;
    bool _nodeUpInLastNodeStateSeenByProvider;
    bool _nodeMaintenanceInLastNodeStateSeenByProvider;

public:
    using UP = std::unique_ptr<ContentBucketSpace>;
    explicit ContentBucketSpace(document::BucketSpace bucketSpace, const ContentBucketDbOptions& db_opts);

    document::BucketSpace bucketSpace() const noexcept { return _bucketSpace; }
    StorBucketDatabase &bucketDatabase() { return _bucketDatabase; }
    void setClusterState(std::shared_ptr<const lib::ClusterState> clusterState);
    std::shared_ptr<const lib::ClusterState> getClusterState() const;
    void setDistribution(std::shared_ptr<const lib::Distribution> distribution);
    std::shared_ptr<const lib::Distribution> getDistribution() const;
    bool getNodeUpInLastNodeStateSeenByProvider() const;
    void setNodeUpInLastNodeStateSeenByProvider(bool nodeUpInLastNodeStateSeenByProvider);
    bool getNodeMaintenanceInLastNodeStateSeenByProvider() const;
    void setNodeMaintenanceInLastNodeStateSeenByProvider(bool nodeMaintenanceInLastNodeStateSeenByProvider);
};

}
