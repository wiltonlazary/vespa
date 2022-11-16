// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store_type_mapper.h"
#include "array_store_config.h"
#include "buffer_type.h"
#include "bufferstate.h"
#include "datastore.h"
#include "entryref.h"
#include "atomic_entry_ref.h"
#include "i_compaction_context.h"
#include "i_compactable.h"
#include "large_array_buffer_type.h"
#include "small_array_buffer_type.h"
#include <vespa/vespalib/util/array.h>

namespace vespalib::datastore {

/**
 * Datastore for storing arrays of type EntryT that is accessed via a 32-bit EntryRef.
 *
 * The default EntryRef type uses 19 bits for offset (524288 values) and 13 bits for buffer id (8192 buffers).
 *
 * Buffer type ids [1,maxSmallArrayTypeId] are used to allocate small arrays in datastore buffers.
 * The default type mapper uses a 1-to-1 mapping between type id and array size.
 * Buffer type id 0 is used to heap allocate large arrays as vespalib::Array instances.
 *
 * The max value of maxSmallArrayTypeId is (2^bufferBits - 1).
 */
template <typename EntryT, typename RefT = EntryRefT<19>, typename TypeMapperT = ArrayStoreTypeMapper<EntryT> >
class ArrayStore : public ICompactable
{
public:
    using AllocSpec = ArrayStoreConfig::AllocSpec;
    using ArrayRef = vespalib::ArrayRef<EntryT>;
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;
    using DataStoreType  = DataStoreT<RefT>;
    using LargeArray = vespalib::Array<EntryT>;
    using LargeBufferType = typename TypeMapperT::LargeBufferType;
    using SmallBufferType = typename TypeMapperT::SmallBufferType;
    using TypeMapper = TypeMapperT;
private:
    uint32_t _largeArrayTypeId;
    uint32_t _maxSmallArrayTypeId;
    size_t _maxSmallArraySize;
    DataStoreType _store;
    TypeMapper _mapper;
    std::vector<SmallBufferType> _smallArrayTypes;
    LargeBufferType _largeArrayType;
    using generation_t = vespalib::GenerationHandler::generation_t;

    void initArrayTypes(const ArrayStoreConfig &cfg, std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    EntryRef addSmallArray(const ConstArrayRef &array);
    EntryRef allocate_small_array(size_t array_size);
    EntryRef addLargeArray(const ConstArrayRef &array);
    EntryRef allocate_large_array(size_t array_size);
    ConstArrayRef getSmallArray(RefT ref, size_t arraySize) const {
        const EntryT *buf = _store.template getEntryArray<EntryT>(ref, arraySize);
        return ConstArrayRef(buf, arraySize);
    }
    ConstArrayRef getLargeArray(RefT ref) const {
        const LargeArray *buf = _store.template getEntry<LargeArray>(ref);
        return ConstArrayRef(&(*buf)[0], buf->size());
    }

public:
    ArrayStore(const ArrayStoreConfig &cfg, std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    ArrayStore(const ArrayStoreConfig &cfg, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, TypeMapper&& mapper);
    ~ArrayStore() override;
    EntryRef add(const ConstArrayRef &array);
    ConstArrayRef get(EntryRef ref) const {
        if (!ref.valid()) [[unlikely]] {
            return ConstArrayRef();
        }
        RefT internalRef(ref);
        uint32_t typeId = _store.getTypeId(internalRef.bufferId());
        if (typeId != _largeArrayTypeId) [[likely]] {
            size_t arraySize = _mapper.get_array_size(typeId);
            return getSmallArray(internalRef, arraySize);
        } else {
            return getLargeArray(internalRef);
        }
    }

    /**
     * Allocate an array of the given size without any instantiation of EntryT elements.
     *
     * Use get_writable() to get a reference to the array for writing.
     *
     * NOTE: In most cases add() should be used instead.
     *       This function is however relevant when serializing objects into char buffers
     *       when e.g. using an ArrayStore<char> for memory management.
     */
    EntryRef allocate(size_t array_size);

    /**
     * Returns a writeable reference to the given array.
     *
     * NOTE: Use with care if reader threads are accessing arrays at the same time.
     *       If so, replacing an element in the array should be an atomic operation.
     */
    ArrayRef get_writable(EntryRef ref) {
        return vespalib::unconstify(get(ref));
    }

    void remove(EntryRef ref);
    EntryRef move_on_compact(EntryRef ref) override;
    ICompactionContext::UP compactWorst(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy);
    // Use this if references to array store is not an array of AtomicEntryRef
    std::unique_ptr<CompactingBuffers> start_compact_worst_buffers(CompactionSpec compaction_spec, const CompactionStrategy &compaction_strategy);

    vespalib::MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }

    /**
     * Returns the address space usage by this store as the ratio between active buffers
     * and the total number available buffers.
     */
    vespalib::AddressSpace addressSpaceUsage() const;

    // Pass on hold list management to underlying store
    void assign_generation(generation_t current_gen) { _store.assign_generation(current_gen); }
    void reclaim_memory(generation_t oldest_used_gen) { _store.reclaim_memory(oldest_used_gen); }
    vespalib::GenerationHolder &getGenerationHolder() { return _store.getGenerationHolder(); }
    void setInitializing(bool initializing) { _store.setInitializing(initializing); }

    // need object location before construction
    static vespalib::GenerationHolder &getGenerationHolderLocation(ArrayStore &self) {
        return DataStoreBase::getGenerationHolderLocation(self._store);
    }
    // need object location before construction
    static DataStoreBase& get_data_store_base(ArrayStore &self) { return self._store; }

    // Should only be used for unit testing
    const BufferState &bufferState(EntryRef ref) const;

    bool has_free_lists_enabled() const { return _store.has_free_lists_enabled(); }
    bool has_held_buffers() const noexcept { return _store.has_held_buffers(); }

    static ArrayStoreConfig optimizedConfigForHugePage(uint32_t maxSmallArrayTypeId,
                                                       size_t hugePageSize,
                                                       size_t smallPageSize,
                                                       size_t minNumArraysForNewBuffer,
                                                       float allocGrowFactor);

    static ArrayStoreConfig optimizedConfigForHugePage(uint32_t maxSmallArrayTypeId,
                                                       const TypeMapper& mapper,
                                                       size_t hugePageSize,
                                                       size_t smallPageSize,
                                                       size_t minNumArraysForNewBuffer,
                                                       float allocGrowFactor);
};

extern template class BufferType<vespalib::Array<uint8_t>>;
extern template class BufferType<vespalib::Array<uint32_t>>;
extern template class BufferType<vespalib::Array<int32_t>>;
extern template class BufferType<vespalib::Array<std::string>>;
extern template class BufferType<vespalib::Array<AtomicEntryRef>>;

}
