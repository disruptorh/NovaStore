#include "fdroid_flat_buffer.h"

#include <climits>

namespace novastore {
namespace {

struct StringRef {
  int32_t offset = -1;
  int32_t length = 0;
};

struct AppRefs {
  StringRef package_name;
  StringRef name;
  StringRef summary;
  StringRef description;
  StringRef developer;
  StringRef icon_url;
  StringRef license;
  StringRef categories;
  StringRef website;
  StringRef source_code;
  StringRef changelog;
  int64_t added = -1;
  int64_t last_updated = -1;
};

struct VersionRefs {
  StringRef package_name;
  int64_t version_code = 0;
  StringRef version_name;
  StringRef download_url;
  StringRef sha256;
  int64_t size = -1;
  int32_t min_sdk = -1;
  int32_t target_sdk = -1;
  int64_t added = -1;
  StringRef signer;
  StringRef native_code;
};

void put_u32(std::string* out, uint32_t value) {
  out->push_back(static_cast<char>(value & 0xFF));
  out->push_back(static_cast<char>((value >> 8) & 0xFF));
  out->push_back(static_cast<char>((value >> 16) & 0xFF));
  out->push_back(static_cast<char>((value >> 24) & 0xFF));
}

void put_u32(std::vector<uint8_t>* out, uint32_t value) {
  out->push_back(static_cast<uint8_t>(value & 0xFF));
  out->push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
  out->push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
  out->push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
}

void put_i32(std::vector<uint8_t>* out, int32_t value) {
  put_u32(out, static_cast<uint32_t>(value));
}

void put_i64(std::vector<uint8_t>* out, int64_t value) {
  const uint64_t bits = static_cast<uint64_t>(value);
  put_u32(out, static_cast<uint32_t>(bits & 0xFFFFFFFFu));
  put_u32(out, static_cast<uint32_t>((bits >> 32) & 0xFFFFFFFFu));
}

void put_ref(std::vector<uint8_t>* out, const StringRef& ref) {
  put_i32(out, ref.offset);
  put_i32(out, ref.length);
}

StringRef intern(std::string* blob, const std::string& value) {
  StringRef ref;
  ref.offset = static_cast<int32_t>(blob->size());
  ref.length = static_cast<int32_t>(value.size());
  blob->append(value);
  return ref;
}

StringRef intern_optional(std::string* blob, const std::optional<std::string>& value) {
  if (!value.has_value()) return StringRef();
  return intern(blob, *value);
}

StringRef intern_list(std::string* blob, const std::vector<std::string>& items) {
  if (items.empty()) return StringRef();
  StringRef ref;
  ref.offset = static_cast<int32_t>(blob->size());
  put_u32(blob, static_cast<uint32_t>(items.size()));
  for (const std::string& item : items) {
    put_u32(blob, static_cast<uint32_t>(item.size()));
    blob->append(item);
  }
  ref.length = static_cast<int32_t>(blob->size()) - ref.offset;
  return ref;
}

int64_t int64_or(const std::optional<int64_t>& value, int64_t fallback) {
  return value.has_value() ? *value : fallback;
}

int32_t int32_or(const std::optional<int32_t>& value, int32_t fallback) {
  return value.has_value() ? *value : fallback;
}

bool fits_int32(size_t value) { return value <= static_cast<size_t>(INT32_MAX); }

AppRefs intern_app(std::string* blob, const ParsedApp& app) {
  AppRefs refs;
  refs.package_name = intern(blob, app.package_name);
  refs.name = intern(blob, app.name);
  refs.summary = intern_optional(blob, app.summary);
  refs.description = intern_optional(blob, app.description);
  refs.developer = intern_optional(blob, app.developer);
  refs.icon_url = intern_optional(blob, app.icon_url);
  refs.license = intern_optional(blob, app.license);
  refs.categories = intern_list(blob, app.categories);
  refs.website = intern_optional(blob, app.website);
  refs.source_code = intern_optional(blob, app.source_code);
  refs.changelog = intern_optional(blob, app.changelog);
  refs.added = int64_or(app.added, -1);
  refs.last_updated = int64_or(app.last_updated, -1);
  return refs;
}

VersionRefs intern_version(std::string* blob, const ParsedVersion& version) {
  VersionRefs refs;
  refs.package_name = intern(blob, version.package_name);
  refs.version_code = version.version_code;
  refs.version_name = intern_optional(blob, version.version_name);
  refs.download_url = intern(blob, version.download_url);
  refs.sha256 = intern_optional(blob, version.sha256);
  refs.size = int64_or(version.size, -1);
  refs.min_sdk = int32_or(version.min_sdk, -1);
  refs.target_sdk = int32_or(version.target_sdk, -1);
  refs.added = int64_or(version.added, -1);
  refs.signer = intern_optional(blob, version.signer);
  refs.native_code = intern_list(blob, version.native_code);
  return refs;
}

void write_app(std::vector<uint8_t>* out, const AppRefs& refs) {
  put_ref(out, refs.package_name);
  put_ref(out, refs.name);
  put_ref(out, refs.summary);
  put_ref(out, refs.description);
  put_ref(out, refs.developer);
  put_ref(out, refs.icon_url);
  put_ref(out, refs.license);
  put_ref(out, refs.categories);
  put_ref(out, refs.website);
  put_ref(out, refs.source_code);
  put_ref(out, refs.changelog);
  put_i64(out, refs.added);
  put_i64(out, refs.last_updated);
}

void write_version(std::vector<uint8_t>* out, const VersionRefs& refs) {
  put_ref(out, refs.package_name);
  put_i64(out, refs.version_code);
  put_ref(out, refs.version_name);
  put_ref(out, refs.download_url);
  put_ref(out, refs.sha256);
  put_i64(out, refs.size);
  put_i32(out, refs.min_sdk);
  put_i32(out, refs.target_sdk);
  put_i64(out, refs.added);
  put_ref(out, refs.signer);
  put_ref(out, refs.native_code);
}

void align_to_four(std::vector<uint8_t>* out) {
  while (out->size() % 4 != 0) out->push_back(0);
}

}  // namespace

bool serialize_index(const ParsedIndex& index, std::vector<uint8_t>* out,
                     std::string* error) {
  if (!fits_int32(index.apps.size()) || !fits_int32(index.versions.size())) {
    if (error != nullptr) *error = "index has too many records";
    return false;
  }

  std::string blob;
  const StringRef repo_name = intern_optional(&blob, index.repo_name);
  std::vector<AppRefs> apps;
  apps.reserve(index.apps.size());
  for (const ParsedApp& app : index.apps) apps.push_back(intern_app(&blob, app));
  std::vector<VersionRefs> versions;
  versions.reserve(index.versions.size());
  for (const ParsedVersion& version : index.versions) {
    versions.push_back(intern_version(&blob, version));
  }

  if (!fits_int32(blob.size())) {
    if (error != nullptr) *error = "index blob is too large";
    return false;
  }

  const uint32_t apps_offset =
      static_cast<uint32_t>((kHeaderBytes + blob.size() + 3) & ~static_cast<size_t>(3));
  const uint32_t versions_offset =
      apps_offset + static_cast<uint32_t>(index.apps.size() * kAppRecordBytes);
  if (!fits_int32(versions_offset)) {
    if (error != nullptr) *error = "index buffer is too large";
    return false;
  }

  out->clear();
  out->reserve(versions_offset + index.versions.size() * kVersionRecordBytes);
  put_u32(out, kIndexMagic);
  put_u32(out, kFormatVersion);
  put_u32(out, static_cast<uint32_t>(blob.size()));
  put_u32(out, static_cast<uint32_t>(index.apps.size()));
  put_u32(out, static_cast<uint32_t>(index.versions.size()));
  put_u32(out, kHeaderBytes);
  put_u32(out, apps_offset);
  put_u32(out, versions_offset);
  put_i32(out, repo_name.offset);
  put_i32(out, repo_name.length);

  out->insert(out->end(), blob.begin(), blob.end());
  align_to_four(out);
  for (const AppRefs& app : apps) write_app(out, app);
  for (const VersionRefs& version : versions) write_version(out, version);

  if (out->size() != versions_offset + index.versions.size() * kVersionRecordBytes) {
    if (error != nullptr) *error = "record layout mismatch";
    return false;
  }
  return true;
}

}  // namespace novastore
