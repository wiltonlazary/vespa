// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/slobrok/cfg.h>
#include <string>

namespace slobrok {

class ConfigShim
{
private:
    uint32_t            _port;
    bool                _enableStateServer;
    std::string         _configId;
    ConfiguratorFactory _factory;

public:
    ConfigShim(uint32_t port);
    ConfigShim(uint32_t port, const std::string& cfgId);
    ConfigShim(uint32_t port, const std::string& cfgId, std::shared_ptr<config::IConfigContext> cfgCtx);
    ~ConfigShim();

    ConfigShim & enableStateServer(bool v) { _enableStateServer = v; return *this; }
    bool enableStateServer() const { return _enableStateServer; }
    uint32_t portNumber() const { return _port; }
    std::string configId() const { return _configId; }
    const char *id() const { return _configId.c_str(); }
    const ConfiguratorFactory & factory() const { return _factory; }
};

} // namespace slobrok
