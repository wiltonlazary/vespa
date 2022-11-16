// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::index {

/**
 * Information about average field length for a single index field.
 */
class FieldLengthInfo {
private:
    double   _average_field_length;
    uint32_t _num_samples;

public:
    FieldLengthInfo()
        : FieldLengthInfo(0.0, 0)
    {
    }

    FieldLengthInfo(double average_field_length, uint32_t num_samples)
        : _average_field_length(average_field_length),
          _num_samples(num_samples)
    {
    }

    double get_average_field_length() const { return _average_field_length; }
    uint32_t get_num_samples() const { return _num_samples; }
};

}
