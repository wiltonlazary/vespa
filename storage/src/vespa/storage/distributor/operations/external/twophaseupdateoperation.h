// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "newest_replica.h"
#include <vespa/storage/distributor/persistencemessagetracker.h>
#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/document/update/documentupdate.h>
#include <set>
#include <optional>

namespace document {
class Document;
}

namespace storage {

namespace api {
class UpdateCommand;
class UpdateReply;
class CreateBucketReply;
class ReturnCode;
}

class UpdateMetricSet;

namespace distributor {

class DistributorBucketSpace;
class GetOperation;

/*
 * General functional outline:
 *
 * if bucket is consistent and all copies are in sync
 *   send updates directly to nodes
 * else
 *   start safe (slow) path
 *
 * Slow path:
 *
 * send Get for document to update to inconsistent copies
 * if get reply has document
 *   apply updates and send new put
 * else if create-if-non-existing set on update
 *   create new blank document
 *   apply updates and send new put
 * else
 *   reply with not found
 *
 * Note that the above case also implicitly handles the case in which a
 * bucket does not exist.
 *
*/


class TwoPhaseUpdateOperation : public SequencedOperation
{
public:
    TwoPhaseUpdateOperation(const DistributorNodeContext& node_ctx,
                            DistributorStripeOperationContext& op_ctx,
                            const DocumentSelectionParser& parser,
                            DistributorBucketSpace &bucketSpace,
                            std::shared_ptr<api::UpdateCommand> msg,
                            DistributorMetricSet& metrics,
                            SequencingHandle sequencingHandle = SequencingHandle());
    ~TwoPhaseUpdateOperation() override;

    void onStart(DistributorStripeMessageSender& sender) override;

    const char* getName() const override { return "twophaseupdate"; }

    std::string getStatus() const override { return ""; }

    void onReceive(DistributorStripeMessageSender&,
                   const std::shared_ptr<api::StorageReply>&) override;

    void onClose(DistributorStripeMessageSender& sender) override;

private:
    enum class SendState {
        NONE_SENT,
        UPDATES_SENT,
        METADATA_GETS_SENT,
        SINGLE_GET_SENT,
        FULL_GETS_SENT,
        PUTS_SENT,
    };

    enum class Mode {
        FAST_PATH,
        SLOW_PATH
    };

    void transitionTo(SendState newState);
    static const char* stateToString(SendState);

    void sendReply(DistributorStripeMessageSender&,
                   std::shared_ptr<api::UpdateReply>);
    void sendReplyWithResult(DistributorStripeMessageSender&, const api::ReturnCode&);
    void ensureUpdateReplyCreated();

    std::vector<BucketDatabase::Entry> get_bucket_database_entries() const;
    bool isFastPathPossible(const std::vector<BucketDatabase::Entry>& entries) const;
    void startFastPathUpdate(DistributorStripeMessageSender& sender, std::vector<BucketDatabase::Entry> entries);
    void startSafePathUpdate(DistributorStripeMessageSender&);
    bool lostBucketOwnershipBetweenPhases() const;
    void sendLostOwnershipTransientErrorReply(DistributorStripeMessageSender&);
    void send_feed_blocked_error_reply(DistributorStripeMessageSender& sender);
    void schedulePutsWithUpdatedDocument(
            std::shared_ptr<document::Document>,
            api::Timestamp,
            DistributorStripeMessageSender&);
    void applyUpdateToDocument(document::Document&) const;
    std::shared_ptr<document::Document> createBlankDocument() const;
    void setUpdatedForTimestamp(api::Timestamp);
    void handleFastPathReceive(DistributorStripeMessageSender&,
                               const std::shared_ptr<api::StorageReply>&);
    void handleSafePathReceive(DistributorStripeMessageSender&,
                               const std::shared_ptr<api::StorageReply>&);
    std::shared_ptr<GetOperation> create_initial_safe_path_get_operation();
    void handle_safe_path_received_metadata_get(DistributorStripeMessageSender&,
                                                api::GetReply&,
                                                const std::optional<NewestReplica>&,
                                                bool any_replicas_failed);
    void handle_safe_path_received_single_full_get(DistributorStripeMessageSender&, api::GetReply&);
    void handleSafePathReceivedGet(DistributorStripeMessageSender&, api::GetReply&);
    void handleSafePathReceivedPut(DistributorStripeMessageSender&, const api::PutReply&);
    bool shouldCreateIfNonExistent() const;
    bool processAndMatchTasCondition(
            DistributorStripeMessageSender& sender,
            const document::Document& candidateDoc);
    bool satisfiesUpdateTimestampConstraint(api::Timestamp) const;
    void addTraceFromReply(api::StorageReply& reply);
    bool hasTasCondition() const noexcept;
    void replyWithTasFailure(DistributorStripeMessageSender& sender,
                             vespalib::stringref message);
    bool may_restart_with_fast_path(const api::GetReply& reply);
    bool replica_set_unchanged_after_get_operation() const;
    void restart_with_fast_path_due_to_consistent_get_timestamps(DistributorStripeMessageSender& sender);
    // Precondition: reply has not yet been sent.
    vespalib::string update_doc_id() const;

    UpdateMetricSet& _updateMetric;
    PersistenceOperationMetricSet& _putMetric;
    PersistenceOperationMetricSet& _getMetric;
    PersistenceOperationMetricSet& _metadata_get_metrics;
    std::shared_ptr<api::UpdateCommand> _updateCmd;
    std::shared_ptr<api::UpdateReply> _updateReply;
    const DistributorNodeContext& _node_ctx;
    DistributorStripeOperationContext& _op_ctx;
    const DocumentSelectionParser& _parser;
    DistributorBucketSpace &_bucketSpace;
    SentMessageMap _sentMessageMap;
    SendState _sendState;
    Mode _mode;
    mbus::Trace _trace;
    document::BucketId _updateDocBucketId;
    std::vector<std::pair<document::BucketId, uint16_t>> _replicas_at_get_send_time;
    std::optional<framework::MilliSecTimer> _single_get_latency_timer;
    uint16_t _fast_path_repair_source_node;
    bool _use_initial_cheap_metadata_fetch_phase;
    bool _replySent;
};

}

}


