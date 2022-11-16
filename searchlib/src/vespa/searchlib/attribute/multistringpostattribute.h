// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multistringattribute.h"
#include "postinglistattribute.h"
#include "i_document_weight_attribute.h"

namespace search {

/**
 * Implementation of multi value string attribute that in addition to enum store and
 * multi value mapping uses an underlying posting list to provide faster search.
 * This class is used for both array and weighted set types.
 *
 * B: EnumAttribute<StringAttribute>
 * T: IEnumStore::Index (array) or
 *    multivalue::WeightedValue<IEnumStore::Index> (weighted set)
 */
template <typename B, typename T>
class MultiValueStringPostingAttributeT
    : public MultiValueStringAttributeT<B, T>,
      protected PostingListAttributeSubBase<AttributeWeightPosting,
                                            typename B::LoadedVector,
                                            typename B::LoadedValueType,
                                            typename B::EnumStore>
{
public:
    using EnumStore = typename MultiValueStringAttributeT<B, T>::EnumStore;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;

private:
    struct DocumentWeightAttributeAdapter final : IDocumentWeightAttribute {
        const MultiValueStringPostingAttributeT &self;
        DocumentWeightAttributeAdapter(const MultiValueStringPostingAttributeT &self_in) : self(self_in) {}
        vespalib::datastore::EntryRef get_dictionary_snapshot() const override;
        LookupResult lookup(const LookupKey & key, vespalib::datastore::EntryRef dictionary_snapshot) const override;
        void collect_folded(vespalib::datastore::EntryRef enum_idx, vespalib::datastore::EntryRef dictionary_snapshot, const std::function<void(vespalib::datastore::EntryRef)>& callback) const override;
        void create(vespalib::datastore::EntryRef idx, std::vector<DocumentWeightIterator> &dst) const override;
        DocumentWeightIterator create(vespalib::datastore::EntryRef idx) const override;
        std::unique_ptr<queryeval::SearchIterator> make_bitvector_iterator(vespalib::datastore::EntryRef idx, uint32_t doc_id_limit, fef::TermFieldMatchData &match_data, bool strict) const override;
    };
    DocumentWeightAttributeAdapter _document_weight_attribute_adapter;

    using LoadedVector = typename B::LoadedVector;
    using PostingParent = PostingListAttributeSubBase<AttributeWeightPosting,
                                                      LoadedVector,
                                                      typename B::LoadedValueType,
                                                      typename B::EnumStore>;

    using ComparatorType = typename EnumStore::ComparatorType;
    using DocId = typename MultiValueStringAttributeT<B, T>::DocId;
    using DocIndices = typename MultiValueStringAttributeT<B, T>::DocIndices;
    using Posting = typename PostingParent::Posting;
    using PostingMap = typename PostingParent::PostingMap;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using SelfType = MultiValueStringPostingAttributeT<B, T>;
    using WeightedIndex = typename MultiValueStringAttributeT<B, T>::WeightedIndex;
    using generation_t = typename MultiValueStringAttributeT<B, T>::generation_t;

    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handle_load_posting_lists;
    using PostingParent::handle_load_posting_lists_and_update_enum_store;
    using PostingParent::forwardedOnAddDoc;

    void freezeEnumDictionary() override;
    void mergeMemoryStats(vespalib::MemoryUsage & total) override;
    void applyValueChanges(const DocIndices& docIndices, EnumStoreBatchUpdater& updater) override ;

public:
    using PostingParent::getPostingList;
    using Dictionary = EnumPostingTree;
    using PostingList = typename PostingParent::PostingList;

    MultiValueStringPostingAttributeT(const vespalib::string & name, const AttributeVector::Config & c);
    MultiValueStringPostingAttributeT(const vespalib::string & name);
    ~MultiValueStringPostingAttributeT();

    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    const IDocumentWeightAttribute *asDocumentWeightAttribute() const override;

    bool onAddDoc(DocId doc) override {
        return forwardedOnAddDoc(doc, this->_mvMapping.getNumKeys(), this->_mvMapping.getCapacityKeys());
    }
    
    void load_posting_lists(LoadedVector& loaded) override {
        handle_load_posting_lists(loaded);
    }

    attribute::IPostingListAttributeBase * getIPostingListAttributeBase() override { return this; }

    const attribute::IPostingListAttributeBase * getIPostingListAttributeBase()  const override { return this; }

    void load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader) override {
        handle_load_posting_lists_and_update_enum_store(loader);
    }
};

using ArrayStringPostingAttribute = MultiValueStringPostingAttributeT<EnumAttribute<StringAttribute>, vespalib::datastore::AtomicEntryRef>;
using WeightedSetStringPostingAttribute = MultiValueStringPostingAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef> >;

} // namespace search

