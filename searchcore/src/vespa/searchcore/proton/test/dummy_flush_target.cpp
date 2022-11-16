// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_flush_target.h"

namespace proton::test {

DummyFlushTarget::DummyFlushTarget(const vespalib::string &name) noexcept
    : searchcorespi::IFlushTarget(name)
{}
DummyFlushTarget::DummyFlushTarget(const vespalib::string &name, const Type &type, const Component &component) noexcept
    : searchcorespi::IFlushTarget(name, type, component)
{}
DummyFlushTarget::~DummyFlushTarget() = default;

}
