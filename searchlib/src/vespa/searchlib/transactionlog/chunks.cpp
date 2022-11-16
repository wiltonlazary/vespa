// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "chunks.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>
#include <stdexcept>

using std::runtime_error;
using std::make_unique;
using vespalib::make_string_short::fmt;
using vespalib::compression::compress;
using vespalib::compression::decompress;
using vespalib::compression::CompressionConfig;
using vespalib::DataBuffer;
using vespalib::ConstBufferRef;
using vespalib::nbostream;

namespace search::transactionlog {

namespace {
void
verifyCrc(nbostream & is, Encoding::Crc crcType) {
    if (is.size() < sizeof(int32_t) * 2) {
        throw runtime_error(fmt("Not even room for the crc and length. Only %zu bytes left", is.size()));
    }
    size_t start = is.rp();
    is.adjustReadPos(is.size() - sizeof(int32_t));
    int32_t crc(0);
    is >> crc;
    is.rp(start);
    int32_t crcVerify = Encoding::calcCrc(crcType, is.data() + start, is.size() - sizeof(crc));
    if (crc != crcVerify) {
        throw runtime_error(fmt("Got bad crc : crcVerify = %d, expected %d", crcVerify, crc));
    }
}

Encoding::Compression
toCompression(CompressionConfig::Type type) {
    switch (type) {
        case CompressionConfig::ZSTD:
            return Encoding::Compression::zstd;
        case CompressionConfig::LZ4:
            return Encoding::Compression::lz4;
        case CompressionConfig::NONE_MULTI:
        case CompressionConfig::NONE:
            return Encoding::Compression::none_multi;
        default:
            abort();
    }
}

}

Encoding
CCITTCRC32NoneChunk::onEncode(nbostream &os) const {
    size_t start = os.wp();
    assert(getEntries().size() == 1);
    serializeEntries(os);
    os << int32_t(Encoding::calcCrc(Encoding::Crc::ccitt_crc32, os.data()+start, os.size() - start));
    return Encoding(Encoding::Crc::ccitt_crc32, Encoding::Compression::none);
}

void
CCITTCRC32NoneChunk::onDecode(nbostream &is) {
    verifyCrc(is, Encoding::Crc::ccitt_crc32);
    nbostream data(is.peek(), is.size() - sizeof(int32_t));
    deserializeEntries(data);
    is.adjustReadPos(is.size());
}

Encoding
XXH64NoneChunk::onEncode(nbostream &os) const {
    size_t start = os.wp();
    assert(getEntries().size() == 1);
    serializeEntries(os);
    os << int32_t(Encoding::calcCrc(Encoding::Crc::xxh64, os.data()+start, os.size() - start));
    return Encoding(Encoding::Crc::xxh64, Encoding::Compression::none);
}

void
XXH64NoneChunk::onDecode(nbostream &is) {
    verifyCrc(is, Encoding::Crc::xxh64);
    nbostream data(is.peek(), is.size() - sizeof(int32_t));
    deserializeEntries(data);
    is.adjustReadPos(is.size());
}

void
XXH64CompressedChunk::decompress(nbostream & is, uint32_t uncompressedLen) {
    vespalib::DataBuffer uncompressed;
    ConstBufferRef compressed(is.peek(), is.size() - sizeof(int32_t));
    ::decompress(_type, uncompressedLen, compressed, uncompressed, false);
    nbostream data(uncompressed.getData(), uncompressed.getDataLen());
    deserializeEntries(data);
    _backing = std::move(uncompressed).stealBuffer();
    is.adjustReadPos(is.size());
}

XXH64CompressedChunk::XXH64CompressedChunk(CompressionConfig::Type type, uint8_t level)
    : _type(type),
      _level(level),
      _backing()
{ }

XXH64CompressedChunk::~XXH64CompressedChunk() = default;

Encoding
XXH64CompressedChunk::compress(nbostream & os, Encoding::Crc crc) const {
    nbostream org;
    serializeEntries(org);
    DataBuffer compressed;
    CompressionConfig cfg(_type, _level, 80, 200);
    ConstBufferRef uncompressed(org.data(), org.size());
    Encoding::Compression actual = toCompression(::compress(cfg, uncompressed, compressed, false));
    os << uint32_t(uncompressed.size());
    size_t start = os.wp();
    os.write(compressed.getData(), compressed.getDataLen());
    os << int32_t(Encoding::calcCrc(crc, os.data()+start, os.size() - start));
    return Encoding(Encoding::Crc::xxh64, actual);
}

Encoding
XXH64CompressedChunk::onEncode(IChunk::nbostream &os) const {
    return compress(os, Encoding::Crc::xxh64);
}

void
XXH64CompressedChunk::onDecode(IChunk::nbostream &is) {
    uint32_t uncompressedLen;
    is >> uncompressedLen;
    verifyCrc(is, Encoding::Crc::xxh64);
    decompress(is, uncompressedLen);
}

}
