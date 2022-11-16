// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "constant_value.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib::eval {

struct ValueBuilderFactory;

/**
 * A ConstantValueFactory that will load constant tensor values from
 * file. The file is expected to be in json format with the same
 * structure used when feeding.
 **/
class ConstantTensorLoader : public ConstantValueFactory
{
private:
    const ValueBuilderFactory &_factory;
public:
    ConstantTensorLoader(const ValueBuilderFactory &factory) : _factory(factory) {}
    ~ConstantTensorLoader();
    ConstantValue::UP create(const vespalib::string &path, const vespalib::string &type) const override;
};

}
