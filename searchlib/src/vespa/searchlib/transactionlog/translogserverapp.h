// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "translogserver.h"
#include <vespa/searchlib/config/config-translogserver.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/vespalib/util/ptrholder.h>

namespace config {
    class ConfigFetcher;
    class ConfigUri;
}
namespace search::common { class FileHeaderContext; }

namespace search::transactionlog {

class TransLogServerApp : public config::IFetcherCallback<searchlib::TranslogserverConfig>
{
private:
    mutable std::mutex                                   _lock;
    TransLogServer::SP                                   _tls;
    vespalib::PtrHolder<searchlib::TranslogserverConfig> _tlsConfig;
    std::unique_ptr<config::ConfigFetcher>               _tlsConfigFetcher;
    const common::FileHeaderContext                    & _fileHeaderContext;

    void configure(std::unique_ptr<searchlib::TranslogserverConfig> cfg) override ;

public:
    TransLogServerApp(const config::ConfigUri & tlsConfigUri,
                      const common::FileHeaderContext &fileHeaderContext);
    ~TransLogServerApp() override;

    TransLogServer::SP getTransLogServer() const;

    void start(FNET_Transport & transport, uint32_t num_cores);
};

}
