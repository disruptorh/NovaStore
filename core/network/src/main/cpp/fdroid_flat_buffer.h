// Wire format between the native index parser and the Kotlin side.
//
// One call returns one buffer, so a 40 000 version index crosses the JNI
// boundary once instead of once per record. Layout, little endian:
//
//   header (40 bytes, see kHeaderBytes)
//     u32 magic            'N' 'S' 'I' '1'
//     u32 format_version
//     u32 blob_bytes       size of the UTF-8 blob
//     u32 app_count
//     u32 version_count
//     u32 blob_offset      byte offset of the blob
//     u32 apps_offset      byte offset of the app record array
//     u32 versions_offset  byte offset of the version record array
//     i32 repo_name_offset relative to the blob, -1 when absent
//     i32 repo_name_length
//   blob                    every string, UTF-8, no terminators
//   app records             kAppRecordBytes each
//   version records         kVersionRecordBytes each
//
// A string reference is (i32 offset relative to the blob, i32 length), with an
// offset of -1 meaning absent. A string list reference is (i32 offset, i32
// total byte length) pointing at u32 count followed by count × (u32 byte
// length, bytes). An empty list is encoded as offset -1. Optional numbers are
// -1 when the index does not declare them; every such value is non-negative in
// a real repository index.

#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include "fdroid_index.h"

namespace novastore {

constexpr uint32_t kIndexMagic = 0x3149534E;  // 'N','S','I','1'
constexpr uint32_t kFormatVersion = 1;
constexpr uint32_t kHeaderBytes = 40;
constexpr uint32_t kAppRecordBytes = 104;
constexpr uint32_t kVersionRecordBytes = 80;

// Serializes [index] into [out]. Returns false only when the index does not
// fit in a 32-bit address space, which no real repository index reaches.
bool serialize_index(const ParsedIndex& index, std::vector<uint8_t>* out,
                     std::string* error);

}  // namespace novastore
