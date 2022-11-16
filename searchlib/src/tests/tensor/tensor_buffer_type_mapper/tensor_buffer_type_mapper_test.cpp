// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/tensor_buffer_type_mapper.h>
#include <vespa/searchlib/tensor/tensor_buffer_operations.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::tensor::TensorBufferOperations;
using search::tensor::TensorBufferTypeMapper;
using vespalib::eval::ValueType;

const vespalib::string tensor_type_sparse_spec("tensor(x{})");
const vespalib::string tensor_type_2d_spec("tensor(x{},y{})");
const vespalib::string tensor_type_2d_mixed_spec("tensor(x{},y[2])");
const vespalib::string float_tensor_type_spec("tensor<float>(y{})");
const vespalib::string tensor_type_dense_spec("tensor(x[2])");

struct TestParam
{
    vespalib::string    _name;
    std::vector<size_t> _array_sizes;
    vespalib::string    _tensor_type_spec;
    TestParam(vespalib::string name, std::vector<size_t> array_sizes, const vespalib::string& tensor_type_spec)
        : _name(std::move(name)),
          _array_sizes(std::move(array_sizes)),
          _tensor_type_spec(tensor_type_spec)
    {
    }
    TestParam(const TestParam&);
    ~TestParam();
};

TestParam::TestParam(const TestParam&) = default;

TestParam::~TestParam() = default;

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param._name;
    return os;
}

class TensorBufferTypeMapperTest : public testing::TestWithParam<TestParam>
{
protected:
    ValueType         _tensor_type;
    TensorBufferOperations _ops;
    TensorBufferTypeMapper _mapper;
    TensorBufferTypeMapperTest();
    ~TensorBufferTypeMapperTest() override;
    std::vector<size_t> get_array_sizes();
    void select_type_ids();
};

TensorBufferTypeMapperTest::TensorBufferTypeMapperTest()
    : testing::TestWithParam<TestParam>(),
      _tensor_type(ValueType::from_spec(GetParam()._tensor_type_spec)),
      _ops(_tensor_type),
      _mapper(GetParam()._array_sizes.size(), &_ops)
{
}

TensorBufferTypeMapperTest::~TensorBufferTypeMapperTest() = default;

std::vector<size_t>
TensorBufferTypeMapperTest::get_array_sizes()
{
    uint32_t max_small_subspaces_type_id = GetParam()._array_sizes.size();
    std::vector<size_t> array_sizes;
    for (uint32_t type_id = 1; type_id <= max_small_subspaces_type_id; ++type_id) {
        auto num_subspaces = type_id - 1;
        array_sizes.emplace_back(_mapper.get_array_size(type_id));
        EXPECT_EQ(_ops.get_array_size(num_subspaces), array_sizes.back());
    }
    return array_sizes;
}

void
TensorBufferTypeMapperTest::select_type_ids()
{
    auto& array_sizes = GetParam()._array_sizes;
    uint32_t type_id = 0;
    for (auto array_size : array_sizes) {
        ++type_id;
        EXPECT_EQ(type_id, _mapper.get_type_id(array_size));
        EXPECT_EQ(type_id, _mapper.get_type_id(array_size - 1));
        if (array_size == array_sizes.back()) {
            // Fallback to indirect storage, using type id 0
            EXPECT_EQ(0u, _mapper.get_type_id(array_size + 1));
        } else {
            EXPECT_EQ(type_id + 1, _mapper.get_type_id(array_size + 1));
        }
    }
}

/*
 * For "dense" case, array size for type id 1 is irrelevant, since
 * type ids 0 and 1 are not used when storing dense tensors in
 * TensorBufferStore.
 */

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(TensorBufferTypeMapperMultiTest,
                                     TensorBufferTypeMapperTest,
                                     testing::Values(TestParam("1d", {8, 16, 32, 40, 64}, tensor_type_sparse_spec),
                                                     TestParam("1dfloat", {4, 12, 20, 28, 36}, float_tensor_type_spec),
                                                     TestParam("2d", {8, 24, 40, 56, 80}, tensor_type_2d_spec),
                                                     TestParam("2dmixed", {8, 24, 48, 64, 96}, tensor_type_2d_mixed_spec),
                                                     TestParam("dense", {8, 24}, tensor_type_dense_spec)),
                                     testing::PrintToStringParamName());

TEST_P(TensorBufferTypeMapperTest, array_sizes_are_calculated)
{
    EXPECT_EQ(GetParam()._array_sizes, get_array_sizes());
}

TEST_P(TensorBufferTypeMapperTest, type_ids_are_selected)
{
    select_type_ids();
}

GTEST_MAIN_RUN_ALL_TESTS()
