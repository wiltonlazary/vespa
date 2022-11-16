// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isequencedtaskexecutor.h"
#include <atomic>

namespace vespalib {

/**
 * Observer class to observe class to run multiple tasks in parallel,
 * but tasks with same id has to be run in sequence.
 */
class SequencedTaskExecutorObserver : public ISequencedTaskExecutor
{
    ISequencedTaskExecutor &_executor;
    std::atomic<uint32_t> _executeCnt;
    std::atomic<uint32_t> _syncCnt;
    std::vector<uint32_t> _executeHistory;
    std::mutex            _mutex;
public:
    using ISequencedTaskExecutor::getExecutorId;

    SequencedTaskExecutorObserver(ISequencedTaskExecutor &executor);
    ~SequencedTaskExecutorObserver() override;

    ExecutorId getExecutorId(uint64_t componentId) const override;
    void executeTask(ExecutorId id, Executor::Task::UP task) override;
    void executeTasks(TaskList tasks) override;
    void sync_all() override;
    void setTaskLimit(uint32_t taskLimit) override;
    ExecutorStats getStats() override;

    uint32_t getExecuteCnt() const { return _executeCnt; }
    uint32_t getSyncCnt() const { return _syncCnt; }
    std::vector<uint32_t> getExecuteHistory();
};

} // namespace search
