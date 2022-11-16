// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::StateManager
 * @ingroup storageserver
 *
 * @brief Keeps and updates node and system states.
 *
 * This component implements the NodeStateUpdater interface to handle states
 * for all components. See that interface for documentation.
 *
 * In addition, this manager is a storage link such that it can handle the
 * various commands for setting and retrieving states.
 */
#pragma once

#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <deque>
#include <map>
#include <unordered_set>
#include <list>
#include <atomic>

namespace metrics {
    class MetricManager;
}

namespace storage {

namespace lib { class ClusterStateBundle; }

class StateManager : public NodeStateUpdater,
                     public StorageLink,
                     public framework::HtmlStatusReporter,
                     private framework::Runnable,
                     private vespalib::JsonStreamTypes
{
    using ClusterStateBundle = lib::ClusterStateBundle;
    using TimeStateCmdPair   = std::pair<framework::MilliSecTime, api::GetNodeStateCommand::SP>;
    using TimeSysStatePair   = std::pair<framework::MilliSecTime, std::shared_ptr<const ClusterStateBundle>>;

    StorageComponent                          _component;
    metrics::MetricManager&                   _metricManager;
    mutable std::mutex                        _stateLock;
    std::condition_variable                   _stateCond;
    std::mutex                                _listenerLock;
    std::shared_ptr<lib::NodeState>           _nodeState;
    std::shared_ptr<lib::NodeState>           _nextNodeState;
    std::shared_ptr<const ClusterStateBundle> _systemState;
    std::shared_ptr<const ClusterStateBundle> _nextSystemState;
    uint32_t                                  _reported_host_info_cluster_state_version;
    std::list<StateListener*>                 _stateListeners;
    std::list<TimeStateCmdPair>               _queuedStateRequests;
    mutable std::mutex                        _threadLock;
    std::condition_variable                   _threadCond;
    std::deque<TimeSysStatePair>              _systemStateHistory;
    uint32_t                                  _systemStateHistorySize;
    std::unique_ptr<HostInfo>                 _hostInfo;
    framework::Thread::UP                     _thread;
    // Controllers that have observed a GetNodeState response sent _after_
    // immediately_send_get_node_state_replies() has been invoked.
    std::unordered_set<uint16_t>              _controllers_observed_explicit_node_state;
    bool                                      _noThreadTestMode;
    bool                                      _grabbedExternalLock;
    std::atomic<bool>                         _notifyingListeners;
    std::atomic<bool>                         _requested_almost_immediate_node_state_replies;

public:
    explicit StateManager(StorageComponentRegister&, metrics::MetricManager&,
                          std::unique_ptr<HostInfo>, bool testMode = false);
    ~StateManager() override;

    void onOpen() override;
    void onClose() override;

    void tick();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    lib::NodeState::CSP getReportedNodeState() const override;
    lib::NodeState::CSP getCurrentNodeState() const override;
    std::shared_ptr<const ClusterStateBundle> getClusterStateBundle() const override;

    void addStateListener(StateListener&) override;
    void removeStateListener(StateListener&) override;

    Lock::SP grabStateChangeLock() override;
    void setReportedNodeState(const lib::NodeState& state) override;
    void setClusterStateBundle(const ClusterStateBundle& c);
    HostInfo& getHostInfo() { return *_hostInfo; }

    void immediately_send_get_node_state_replies() override;
    void request_almost_immediate_node_state_replies() override;

private:
    struct ExternalStateLock;
    friend struct ExternalStateLock;
    friend struct StateManagerTest;

    void notifyStateListeners();
    bool sendGetNodeStateReplies(
            framework::MilliSecTime olderThanTime = framework::MilliSecTime(0),
            uint16_t index = 0xffff);
    void mark_controller_as_having_observed_explicit_node_state(const std::unique_lock<std::mutex> &, uint16_t controller_index);

    lib::Node thisNode() const;

    /**
     * Overwrite the current cluster state with the one that is currently
     * pending.
     *
     * Appends the pending cluster state to a circular buffer of historic
     * states.
     *
     * Preconditions:
     *   - _stateLock is held
     *   - _systemState.get() != nullptr
     *   - _nextSystemState.get() != nullptr
     * Postconditions:
     *   - _systemState = old(_nextSystemState)
     *   - _nextSystemState.get() == nullptr
     */
    void enableNextClusterState();

    /**
     * Log this node's state transition as given by the cluster state iff the
     * state differs between currentState and newState.
     */
    void logNodeClusterStateTransition(
            const ClusterStateBundle& currentState,
            const ClusterStateBundle& newState) const;

    bool onGetNodeState(const std::shared_ptr<api::GetNodeStateCommand>&) override;
    bool onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>&) override;
    bool onActivateClusterStateVersion(const std::shared_ptr<api::ActivateClusterStateVersionCommand>&) override;

    /**
     * _stateLock MUST NOT be held while calling.
     */
    std::string getNodeInfo() const;

    void run(framework::ThreadHandle&) override;

    void clear_controllers_observed_explicit_node_state_vector();
};

} // storage
