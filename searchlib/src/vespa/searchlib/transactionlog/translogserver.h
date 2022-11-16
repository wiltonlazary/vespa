// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "domainconfig.h"
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/document/util/queue.h>
#include <vespa/fnet/frt/invokable.h>
#include <shared_mutex>
#include <atomic>

class FRT_Supervisor;
class FNET_Transport;

namespace search::common { class FileHeaderContext; }
namespace search::transactionlog {

class TransLogServerExplorer;
class Domain;

class TransLogServer : private FRT_Invokable, public document::Runnable, public WriterFactory
{
public:
    friend class TransLogServerExplorer;
    using SP = std::shared_ptr<TransLogServer>;
    using DomainSP = std::shared_ptr<Domain>;
    TransLogServer(FNET_Transport & transport, const vespalib::string &name, int listenPort, const vespalib::string &baseDir,
                   const common::FileHeaderContext &fileHeaderContext, const DomainConfig & cfg, size_t maxThreads);
    TransLogServer(FNET_Transport & transport, const vespalib::string &name, int listenPort, const vespalib::string &baseDir,
                   const common::FileHeaderContext &fileHeaderContext, const DomainConfig & cfg);
    TransLogServer(FNET_Transport & transport, const vespalib::string &name, int listenPort, const vespalib::string &baseDir,
                   const common::FileHeaderContext &fileHeaderContext);
    ~TransLogServer() override;
    DomainStats getDomainStats() const;
    std::shared_ptr<Writer> getWriter(const vespalib::string & domainName) const override;
    TransLogServer & setDomainConfig(const DomainConfig & cfg);

private:
    bool onStop() override;
    void run() override;
    void exportRPC(FRT_Supervisor & supervisor);
    void relayToThreadRPC(FRT_RPCRequest *req);

    void createDomain(FRT_RPCRequest *req);
    void deleteDomain(FRT_RPCRequest *req);
    void openDomain(FRT_RPCRequest *req);
    void listDomains(FRT_RPCRequest *req);

    void domainStatus(FRT_RPCRequest *req);
    void domainCommit(FRT_RPCRequest *req);
    void domainSessionRun(FRT_RPCRequest *req);
    void domainPrune(FRT_RPCRequest *req);
    void domainVisit(FRT_RPCRequest *req);
    void domainSessionClose(FRT_RPCRequest *req);
    void domainSync(FRT_RPCRequest *req);

    std::vector<vespalib::string> getDomainNames();
    DomainSP findDomain(vespalib::stringref name) const;
    vespalib::string dir()        const { return _baseDir + "/" + _name; }
    vespalib::string domainList() const { return dir() + "/" + _name + ".domains"; }

    using DomainList = std::map<vespalib::string, DomainSP >;
    using ReadGuard = std::shared_lock<std::shared_mutex>;
    using WriteGuard = std::unique_lock<std::shared_mutex>;

    vespalib::string                    _name;
    vespalib::string                    _baseDir;
    DomainConfig                        _domainConfig;
    vespalib::ThreadStackExecutor       _executor;
    std::unique_ptr<FastOS_ThreadPool>  _threadPool;
    std::unique_ptr<FRT_Supervisor>     _supervisor;
    DomainList                          _domains;
    mutable std::shared_mutex           _domainMutex;;          // Protects _domains
    std::condition_variable             _domainCondition;
    std::mutex                          _fileLock;      // Protects the creating and deleting domains including file system operations.
    document::Queue<FRT_RPCRequest *>   _reqQ;
    const common::FileHeaderContext    &_fileHeaderContext;
    std::atomic<bool>                   _closed;
};

}
