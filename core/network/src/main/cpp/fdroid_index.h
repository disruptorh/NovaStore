// Parser for F-Droid repository indexes (index-v2.json, index-v1.json).
//
// This is a line-by-line port of RepoIndexParser.kt: the same fields are kept,
// the same locale ranking picks the translated text, the same values are
// dropped, and the same URL resolution runs. Any behaviour change would show
// up as a different catalog, so the port is intentionally literal.
//
// The input is untrusted network data. It is therefore scanned iteratively
// with a hard nesting limit, and malformed UTF-8 is replaced with U+FFFD the
// same way java.io.InputStreamReader does. On malformed JSON the parser
// reports the failure instead of guessing; the caller then falls back to the
// Gson parser.

#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace novastore {

struct ParsedApp {
  std::string package_name;
  std::string name;
  std::optional<std::string> summary;
  std::optional<std::string> description;
  std::optional<std::string> developer;
  std::optional<std::string> icon_url;
  std::optional<std::string> license;
  std::vector<std::string> categories;
  std::optional<std::string> website;
  std::optional<std::string> source_code;
  std::optional<std::string> changelog;
  std::optional<int64_t> added;
  std::optional<int64_t> last_updated;
};

struct ParsedVersion {
  std::string package_name;
  int64_t version_code = 0;
  std::optional<std::string> version_name;
  std::string download_url;
  std::optional<std::string> sha256;
  std::optional<int64_t> size;
  std::optional<int32_t> min_sdk;
  std::optional<int32_t> target_sdk;
  std::optional<int64_t> added;
  std::optional<std::string> signer;
  std::vector<std::string> native_code;
};

struct ParsedIndex {
  std::optional<std::string> repo_name;
  std::vector<ParsedApp> apps;
  std::vector<ParsedVersion> versions;
};

// Maximum object/array nesting accepted. A 60 MB index nests 6 levels deep;
// anything past this is treated as malformed rather than followed.
constexpr int kMaxDepth = 200;

bool parse_v2(const uint8_t* data, size_t size, const std::string& base_url,
              const std::vector<std::string>& locales, ParsedIndex* out,
              std::string* error);

bool parse_v1(const uint8_t* data, size_t size, const std::string& base_url,
              const std::vector<std::string>& locales, ParsedIndex* out,
              std::string* error);

}  // namespace novastore
