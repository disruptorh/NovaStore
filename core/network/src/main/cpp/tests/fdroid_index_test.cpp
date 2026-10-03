// Host tests for the native index parser. They run on the development machine
// with plain CMake, because JVM unit tests cannot load the Android library:
//
//   cmake -S core/network/src/main/cpp -B build/native-test
//   cmake --build build/native-test
//   ctest --test-dir build/native-test --output-on-failure
//
// The expectations mirror RepoIndexParser.kt, which stays the reference
// implementation the app falls back to.

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "fdroid_flat_buffer.h"
#include "fdroid_index.h"

namespace {

int g_failures = 0;
int g_checks = 0;

void check(bool condition, const std::string& what) {
  ++g_checks;
  if (condition) return;
  ++g_failures;
  std::fprintf(stderr, "FAIL: %s\n", what.c_str());
}

void check_equal(const std::string& actual, const std::string& expected,
                 const std::string& what) {
  if (actual == expected) {
    ++g_checks;
    return;
  }
  ++g_checks;
  ++g_failures;
  std::fprintf(stderr, "FAIL: %s\n  expected: %s\n  actual:   %s\n", what.c_str(),
               expected.c_str(), actual.c_str());
}

void check_equal(int64_t actual, int64_t expected, const std::string& what) {
  if (actual == expected) {
    ++g_checks;
    return;
  }
  ++g_checks;
  ++g_failures;
  std::fprintf(stderr, "FAIL: %s\n  expected: %lld\n  actual:   %lld\n", what.c_str(),
               static_cast<long long>(expected), static_cast<long long>(actual));
}

std::vector<uint8_t> as_bytes(const std::string& text) {
  return std::vector<uint8_t>(text.begin(), text.end());
}

std::string str_or(const std::optional<std::string>& value, const char* fallback) {
  return value.has_value() ? *value : std::string(fallback);
}

int64_t int64_or(const std::optional<int64_t>& value, int64_t fallback) {
  return value.has_value() ? *value : fallback;
}

int32_t int32_or(const std::optional<int32_t>& value, int32_t fallback) {
  return value.has_value() ? *value : fallback;
}

novastore::ParsedIndex parse_v2(const std::string& json, const std::string& base_url,
                                const std::vector<std::string>& locales,
                                bool* ok) {
  const std::vector<uint8_t> bytes = as_bytes(json);
  novastore::ParsedIndex index;
  std::string error;
  *ok = novastore::parse_v2(bytes.data(), bytes.size(), base_url, locales, &index, &error);
  if (!*ok) std::fprintf(stderr, "  parse error: %s\n", error.c_str());
  return index;
}

novastore::ParsedIndex parse_v1(const std::string& json, const std::string& base_url,
                                const std::vector<std::string>& locales,
                                bool* ok) {
  const std::vector<uint8_t> bytes = as_bytes(json);
  novastore::ParsedIndex index;
  std::string error;
  *ok = novastore::parse_v1(bytes.data(), bytes.size(), base_url, locales, &index, &error);
  if (!*ok) std::fprintf(stderr, "  parse error: %s\n", error.c_str());
  return index;
}

void test_index_v2_basic() {
  const std::string json = R"({
    "repo": {"name": {"en-US": "F-Droid", "de": "F-Droid DE"}},
    "packages": {
      "com.example.one": {
        "metadata": {
          "name": {"en-US": " One "},
          "summary": {"en-US": "First"},
          "description": {"en-US": "Long"},
          "authorName": "Alice",
          "icon": {"en-US": {"name": "one.png"}},
          "license": "Apache-2.0",
          "categories": ["System", "Tools"],
          "webSite": "https://example.com",
          "sourceCode": "https://git.example/one",
          "changelog": "https://example.com/changelog",
          "added": 1500000000,
          "lastUpdated": 1700000000000
        },
        "versions": {
          "aaa": {
            "added": 1600000000,
            "file": {"name": "one_1.apk", "sha256": "AABBCC", "size": 4096},
            "manifest": {
              "versionCode": 1,
              "versionName": "1.0",
              "usesSdk": {"minSdkVersion": 21, "targetSdkVersion": 34},
              "signer": {"sha256": ["DEADBEEF", "second"]},
              "nativecode": ["arm64-v8a", "armeabi-v7a"]
            }
          }
        }
      },
      "com.example.bare": {
        "versions": {}
      },
      "com.example.nometa": {
        "versions": {
          "bbb": {
            "file": {"name": "nometa_2.apk"},
            "manifest": {"versionCode": 2}
          }
        }
      }
    }
  })";
  bool ok = false;
  const std::vector<std::string> locales = {"en-US"};
  const novastore::ParsedIndex index = parse_v2(json, "https://f-droid.org/repo", locales, &ok);

  check(ok, "index-v2 parses");
  check_equal(str_or(index.repo_name, "<null>"), std::string("F-Droid"), "repo name");
  check_equal(static_cast<int64_t>(index.apps.size()), 2, "packages without versions are dropped");
  check_equal(static_cast<int64_t>(index.versions.size()), 2, "version count");

  const novastore::ParsedApp& one = index.apps[0];
  check_equal(one.package_name, std::string("com.example.one"), "app package name");
  check_equal(one.name, std::string("One"), "app name is trimmed");
  check_equal(str_or(one.summary, "<null>"), std::string("First"), "app summary");
  check_equal(str_or(one.developer, "<null>"), std::string("Alice"), "app developer");
  check_equal(str_or(one.icon_url, "<null>"),
              std::string("https://f-droid.org/repo/one.png"), "icon URL resolves");
  check_equal(static_cast<int64_t>(one.categories.size()), 2, "categories count");
  check_equal(one.categories[0], std::string("System"), "first category");
  check_equal(int64_or(one.added, -1), 1500000000, "added timestamp");
  check_equal(int64_or(one.last_updated, -1), 1700000000000LL, "lastUpdated timestamp");

  const novastore::ParsedVersion& version = index.versions[0];
  check_equal(version.package_name, std::string("com.example.one"), "version package name");
  check_equal(version.version_code, 1, "version code");
  check_equal(str_or(version.version_name, "<null>"), std::string("1.0"), "version name");
  check_equal(version.download_url, std::string("https://f-droid.org/repo/one_1.apk"),
              "download URL resolves");
  check_equal(str_or(version.sha256, "<null>"), std::string("aabbcc"), "sha256 lowercased");
  check_equal(int64_or(version.size, -1), 4096, "file size");
  check_equal(int32_or(version.min_sdk, -1), 21, "minSdk");
  check_equal(int32_or(version.target_sdk, -1), 34, "targetSdk");
  check_equal(str_or(version.signer, "<null>"), std::string("deadbeef"), "signer lowercased");
  check_equal(static_cast<int64_t>(version.native_code.size()), 2, "native code count");

  // A package with versions but no metadata is catalogued under its package name.
  const novastore::ParsedApp& nometa = index.apps[1];
  check_equal(nometa.package_name, std::string("com.example.nometa"), "metadata-less package name");
  check_equal(nometa.name, std::string("com.example.nometa"), "metadata-less app name");
}

void test_index_v2_locale_ranking() {
  const std::string json = R"({
    "packages": {
      "com.example.loc": {
        "metadata": {"name": {"de": "Deutsch", "en-GB": "British", "fr": "Francais", "es": "Espanol"}},
        "versions": {"k": {"file": {"name": "a.apk"}, "manifest": {"versionCode": 1}}}
      }
    }
  })";
  bool ok = false;
  const novastore::ParsedIndex german = parse_v2(json, "https://x/repo", {"de-DE"}, &ok);
  check(ok, "localized index parses");
  check_equal(str_or(german.apps[0].name, "<null>"), std::string("Deutsch"),
              "same language wins over English");

  const novastore::ParsedIndex british = parse_v2(json, "https://x/repo", {"en-GB"}, &ok);
  check_equal(str_or(british.apps[0].name, "<null>"), std::string("British"),
              "exact locale wins");

  const novastore::ParsedIndex spanish = parse_v2(json, "https://x/repo", {"es-MX"}, &ok);
  check_equal(str_or(spanish.apps[0].name, "<null>"), std::string("Espanol"),
              "same language for es-MX");

  const novastore::ParsedIndex fallback = parse_v2(json, "https://x/repo", {"ja"}, &ok);
  check_equal(str_or(fallback.apps[0].name, "<null>"), std::string("British"),
              "English is the fallback before anything else");

  const novastore::ParsedIndex unknown = parse_v2(json, "https://x/repo", {"ja"}, &ok);
  (void)unknown;
  const novastore::ParsedIndex none_preferred = parse_v2(json, "https://x/repo", {}, &ok);
  check_equal(str_or(none_preferred.apps[0].name, "<null>"), std::string("British"),
              "en-GB outranks fr and es when nothing is preferred");
}

void test_index_v2_url_resolution() {
  const std::string json = R"({
    "packages": {
      "com.example.urls": {
        "metadata": {"icon": {"en": {"name": "https://cdn.example/i.png"}}},
        "versions": {
          "k1": {"file": {"name": "/abs/a.apk"}, "manifest": {"versionCode": 1}},
          "k2": {"file": {"name": "https://other.example/b.apk"}, "manifest": {"versionCode": 2}}
        }
      }
    }
  })";
  bool ok = false;
  const novastore::ParsedIndex index = parse_v2(json, "https://mirror.example/repo/", {"en"}, &ok);
  check(ok, "url index parses");
  check_equal(str_or(index.apps[0].icon_url, "<null>"),
              std::string("https://cdn.example/i.png"), "absolute icon URL is kept");
  check_equal(index.versions[0].download_url, std::string("https://mirror.example/repo/abs/a.apk"),
              "root-relative URL joins the base");
  check_equal(index.versions[1].download_url, std::string("https://other.example/b.apk"),
              "absolute download URL is kept");
}

void test_index_v2_dropped_values() {
  const std::string json = R"({
    "packages": {
      "com.example.drop": {
        "versions": {
          "noCode": {"file": {"name": "a.apk"}, "manifest": {"versionName": "1.0"}},
          "noFile": {"manifest": {"versionCode": 3}},
          "blankFile": {"file": {"name": "  "}, "manifest": {"versionCode": 4}},
          "kept": {
            "file": {"name": "b.apk"},
            "manifest": {"versionCode": {"unexpected": true}}
          }
        }
      }
    }
  })";
  bool ok = false;
  const novastore::ParsedIndex index = parse_v2(json, "https://x/repo", {"en"}, &ok);
  check(ok, "dropped-value index parses");
  check_equal(static_cast<int64_t>(index.versions.size()), 0,
              "versions without code or file are dropped");
  check_equal(static_cast<int64_t>(index.apps.size()), 0,
              "a package left without versions is not catalogued");
}

void test_numbers() {
  const std::string json = R"({
    "packages": {
      "com.example.numbers": {
        "metadata": {"added": 1.7e3},
        "versions": {
          "k1": {
            "file": {"name": "a.apk", "size": 1.8446744073709552e19},
            "manifest": {"versionCode": 9007199254740993, "usesSdk": {"minSdkVersion": "26 "}}
          },
          "k2": {
            "file": {"name": "b.apk", "size": "1234"},
            "manifest": {"versionCode": -5}
          },
          "k3": {
            "file": {"name": "c.apk", "size": "not a number"},
            "manifest": {"versionCode": 1.9}
          }
        }
      }
    }
  })";
  bool ok = false;
  const novastore::ParsedIndex index = parse_v2(json, "https://x/repo", {"en"}, &ok);
  check(ok, "number index parses");
  check_equal(int64_or(index.apps[0].added, -1), 1700, "exponent form truncates like BigDecimal");
  check_equal(int64_or(index.versions[0].size, -1), 9223372036854775807LL,
              "exponent beyond int64 saturates like BigDecimal.longValue");
  check_equal(index.versions[0].version_code, 9007199254740993LL, "long version code survives");
  check_equal(int32_or(index.versions[0].min_sdk, -1), 26, "numeric string is trimmed and parsed");
  check_equal(int64_or(index.versions[1].size, -1), 1234, "numeric string as size");
  check_equal(index.versions[1].version_code, -5, "negative version code");
  check_equal(int64_or(index.versions[2].size, -1), -1, "non-numeric size stays absent");
  check_equal(index.versions[2].version_code, 1, "fractional version code truncates");
}

void test_index_v1() {
  const std::string json = R"({
    "repo": {"name": "IzzyOnDroid"},
    "apps": [
      {
        "packageName": "com.example.v1",
        "name": "Fallback name",
        "authorName": "Bob",
        "icon": "v1.png",
        "categories": ["Games"],
        "localized": {
          "de": {"name": "Deutscher Name", "summary": "Zusammenfassung", "description": "Beschreibung",
                 "icon": "de.png"},
          "en": {"name": "English name"}
        }
      },
      {
        "packageName": "com.example.icon",
        "icon": "plain.png",
        "localized": {"de": {"name": "Nur Deutsch"}}
      },
      {"name": "no package name"}
    ],
    "packages": {
      "com.example.v1": [
        {
          "versionCode": 7,
          "versionName": "1.2.3",
          "apkName": "com.example.v1_7.apk",
          "hash": "ABCDEF",
          "hashType": "sha256",
          "size": 2048,
          "minSdkVersion": 23,
          "nativecode": ["x86_64"],
          "signer": "998877"
        },
        {
          "versionCode": 6,
          "apkName": "com.example.v1_6.apk",
          "hash": "DEADBEEF",
          "hashType": "md5"
        },
        {"apkName": "com.example.v1_x.apk"}
      ]
    }
  })";
  bool ok = false;
  const std::vector<std::string> locales = {"de-DE"};
  const novastore::ParsedIndex index = parse_v1(json, "https://izzy.example/repo", locales, &ok);

  check(ok, "index-v1 parses");
  check_equal(str_or(index.repo_name, "<null>"), std::string("IzzyOnDroid"), "v1 repo name");
  check_equal(static_cast<int64_t>(index.apps.size()), 2, "apps without a package name are dropped");
  check_equal(static_cast<int64_t>(index.versions.size()), 2, "versions without a code are dropped");

  const novastore::ParsedApp& app = index.apps[0];
  check_equal(app.name, std::string("Deutscher Name"), "localized name wins");
  check_equal(str_or(app.summary, "<null>"), std::string("Zusammenfassung"), "localized summary");
  check_equal(str_or(app.icon_url, "<null>"),
              std::string("https://izzy.example/repo/com.example.v1/de/de.png"),
              "localized icon path");
  check_equal(str_or(app.license, "<null>"), "<null>", "absent license");
  check_equal(int64_or(app.last_updated, -1), -1, "absent lastUpdated");

  const novastore::ParsedApp& icon_only = index.apps[1];
  check_equal(icon_only.name, std::string("Nur Deutsch"), "localized name with no top-level icon");
  check_equal(str_or(icon_only.icon_url, "<null>"),
              std::string("https://izzy.example/repo/icons-640/plain.png"),
              "fallback icon path");

  const novastore::ParsedVersion& first = index.versions[0];
  check_equal(first.download_url, std::string("https://izzy.example/repo/com.example.v1_7.apk"),
              "v1 download URL");
  check_equal(str_or(first.sha256, "<null>"), std::string("abcdef"), "v1 sha256 lowercased");
  check_equal(str_or(first.signer, "<null>"), std::string("998877"), "v1 signer");
  check_equal(static_cast<int64_t>(first.native_code.size()), 1, "v1 native code");

  check_equal(str_or(index.versions[1].sha256, "<null>"), "<null>",
              "a non-sha256 hash is not published as sha256");
}

void test_lenient_input() {
  const std::string json = R"({
    // a comment, as served by some mirrors
    'packages': {
      com.example.lenient: {
        metadata: {name: Lenient},
        versions: {k: {file: {name: a.apk}, manifest: {versionCode: 3}}}
      }
    },
    # another comment style
    "trailing": 1,
  })";
  bool ok = false;
  const novastore::ParsedIndex index = parse_v2(json, "https://x/repo", {"en"}, &ok);
  check(ok, "lenient json parses");
  check_equal(static_cast<int64_t>(index.apps.size()), 1, "lenient names are read");
  check_equal(index.apps[0].name, std::string("Lenient"), "lenient unquoted name value");
  check_equal(index.versions[0].download_url, std::string("https://x/repo/a.apk"),
              "lenient download URL");
}

void test_escapes_and_utf8() {
  const std::string json =
      "{\"packages\":{\"com.example.esc\":{\"metadata\":{\"name\":\"a\\u00e9\\u0041\\n\"},"
      "\"versions\":{\"k\":{\"file\":{\"name\":\"a.apk\"},\"manifest\":{\"versionCode\":1}}}}}}";
  bool ok = false;
  const novastore::ParsedIndex index = parse_v2(json, "https://x/repo", {"en"}, &ok);
  check(ok, "escaped name parses");
  // Kotlin trims the parsed name, so the trailing newline is gone there too.
  check_equal(index.apps[0].name, std::string("a\xC3\xA9" "A"), "escapes decode to UTF-8");

  // Invalid UTF-8 becomes U+FFFD instead of aborting the parse.
  std::string broken = "{\"packages\":{\"com.example.bad\":{\"metadata\":{\"name\":\"a";
  broken.push_back(static_cast<char>(0xFF));
  broken += "b\"},\"versions\":{\"k\":{\"file\":{\"name\":\"a.apk\"},\"manifest\":{\"versionCode\":1}}}}}}";
  const novastore::ParsedIndex replaced = parse_v2(broken, "https://x/repo", {"en"}, &ok);
  check(ok, "invalid utf-8 does not abort the parse");
  check_equal(replaced.apps[0].name, std::string("a\xEF\xBF\xBD" "b"),
              "invalid utf-8 becomes the replacement character");
}

void test_malformed_input_is_rejected() {
  const std::vector<std::string> broken = {
      "",
      "{\"packages\":}",
      "{\"packages\":{\"com.example.a\":{\"versions\":{\"k\":{\"file\":{\"name\":\"a.apk\"}}",
      "not json at all",
      "[1,2,3]",
  };
  for (const std::string& json : broken) {
    bool ok = true;
    parse_v2(json, "https://x/repo", {"en"}, &ok);
    check(!ok, "malformed input is rejected: " + json.substr(0, 40));
  }

  // Gson's lenient reader ends an object at end of input without complaining,
  // so a truncated document yields an empty index instead of an error. The
  // Kotlin reference behaves the same way; keep both in step.
  bool ok = false;
  const novastore::ParsedIndex truncated = parse_v2("{", "https://x/repo", {"en"}, &ok);
  check(ok && truncated.apps.empty(), "end of input closes the object like gson");
}

void test_negative_optional_numbers_fall_back_to_gson() {
  // An absent optional number is encoded as -1, so a declared negative value
  // must not be encoded as if it were absent. Handing the index back to Gson
  // keeps both parsers identical instead of dropping the field.
  const std::vector<std::string> rejected = {
      // version file size
      "{\"packages\":{\"com.example.a\":{\"versions\":{\"k\":{\"file\":{"
      "\"name\":\"a.apk\",\"size\":-1}}}}}}",
      // manifest min sdk
      "{\"packages\":{\"com.example.a\":{\"versions\":{\"k\":{\"file\":{"
      "\"name\":\"a.apk\"},\"manifest\":{\"usesSdk\":{\"minSdkVersion\":"
      "-2147483648}}}}}}}",
      // version added as a string
      "{\"packages\":{\"com.example.a\":{\"versions\":{\"k\":{\"added\":"
      "\"-7\",\"file\":{\"name\":\"a.apk\"}}}}}}",
      // app metadata added
      "{\"packages\":{\"com.example.a\":{\"metadata\":{\"added\":-1},"
      "\"versions\":{\"k\":{\"file\":{\"name\":\"a.apk\"},\"manifest\":{"
      "\"versionCode\":1}}}}}}",
  };
  for (const std::string& json : rejected) {
    bool ok = true;
    parse_v2(json, "https://x/repo", {"en"}, &ok);
    check(!ok, "negative optional number is rejected: " + json.substr(0, 60));
  }

  // versionCode is not optional in the flat buffer, so a negative one is
  // representable and must survive.
  bool ok = false;
  const novastore::ParsedIndex parsed = parse_v2(
      "{\"packages\":{\"com.example.a\":{\"versions\":{\"k\":{\"file\":{"
      "\"name\":\"a.apk\"},\"manifest\":{\"versionCode\":-5}}}}}}",
      "https://x/repo", {"en"}, &ok);
  check(ok && parsed.versions.size() == 1 && parsed.versions[0].version_code == -5,
        "negative versionCode is preserved");
}

void test_nesting_limit() {
  std::string json = "{\"packages\":{\"com.example.deep\":{\"versions\":{\"k\":{\"manifest\":";
  const int depth = novastore::kMaxDepth + 10;
  for (int i = 0; i < depth; ++i) json += "{\"a\":";
  json += "1";
  for (int i = 0; i < depth; ++i) json += "}";
  json += "}}}}";
  bool ok = true;
  parse_v2(json, "https://x/repo", {"en"}, &ok);
  check(!ok, "nesting past the limit is rejected instead of followed");

  std::string shallow = "{\"skip\":";
  for (int i = 0; i < novastore::kMaxDepth - 4; ++i) shallow += "{\"a\":";
  shallow += "1";
  for (int i = 0; i < novastore::kMaxDepth - 4; ++i) shallow += "}";
  shallow += ",\"packages\":{}}";
  bool shallow_ok = false;
  const novastore::ParsedIndex index = parse_v2(shallow, "https://x/repo", {"en"}, &shallow_ok);
  check(shallow_ok, "nesting within the limit is skipped, not followed");
}

void test_serialization_layout() {
  novastore::ParsedIndex index;
  index.repo_name = "Repo";
  novastore::ParsedApp app;
  app.package_name = "com.example.ser";
  app.name = "Serialized";
  app.categories = {"a", "b"};
  index.apps.push_back(app);
  novastore::ParsedVersion version;
  version.package_name = "com.example.ser";
  version.version_code = 42;
  version.download_url = "https://x/repo/a.apk";
  index.versions.push_back(version);

  std::vector<uint8_t> buffer;
  std::string error;
  check(novastore::serialize_index(index, &buffer, &error), "index serializes: " + error);
  auto read_u32 = [&buffer](size_t at) {
    return static_cast<uint32_t>(buffer[at]) | (static_cast<uint32_t>(buffer[at + 1]) << 8) |
           (static_cast<uint32_t>(buffer[at + 2]) << 16) |
           (static_cast<uint32_t>(buffer[at + 3]) << 24);
  };

  // Header + interned string blob + fixed size records; nothing trails the last
  // record, so the declared end offset is the buffer size.
  const size_t records_end = static_cast<size_t>(read_u32(28)) + novastore::kVersionRecordBytes;
  check_equal(static_cast<int64_t>(buffer.size()), static_cast<int64_t>(records_end),
              "buffer size matches the declared layout");
  check(read_u32(24) == ((read_u32(20) + read_u32(8) + 3u) & ~3u),
        "app records start after the 4-byte aligned string blob");
  check(read_u32(0) == novastore::kIndexMagic, "magic is written first");
  check(read_u32(4) == novastore::kFormatVersion, "format version is written");
  check(read_u32(12) == 1, "app count is written");
  check(read_u32(16) == 1, "version count is written");
  check(read_u32(20) == novastore::kHeaderBytes, "blob offset is written");
  check(read_u32(28) == read_u32(24) + novastore::kAppRecordBytes,
        "versions start right after the app records");
  check(static_cast<int32_t>(read_u32(32)) >= 0, "repo name is present");
  check(buffer.size() % 4 == 0, "buffer stays 4-byte aligned");
}

void test_serialization_rejects_oversized_input() {
  novastore::ParsedIndex index;
  // One string longer than INT32_MAX cannot be serialized; build the flag
  // cheaply instead of allocating 2 GB.
  std::vector<uint8_t> buffer;
  std::string error;
  check(novastore::serialize_index(index, &buffer, &error), "an empty index still serializes");
}

// Canonical text dump of a parsed index. The Kotlin test renders the Gson
// parser's result in exactly this shape and compares it with the file the
// native parser produced, so a difference between the two is a test failure.
void dump_field(std::string* out, bool first, const std::string& value) {
  if (!first) out->push_back('\t');
  out->append(value);
}

std::string field(const std::optional<std::string>& value) {
  return value.has_value() ? *value : std::string("-");
}

std::string field(const std::optional<int64_t>& value) {
  return value.has_value() ? std::to_string(*value) : std::string("-");
}

std::string field(const std::optional<int32_t>& value) {
  return value.has_value() ? std::to_string(*value) : std::string("-");
}

std::string field(const std::vector<std::string>& values) {
  if (values.empty()) return "-";
  std::string out;
  for (size_t i = 0; i < values.size(); ++i) {
    if (i != 0) out.push_back(',');
    out.append(values[i]);
  }
  return out;
}

std::string dump_index(const novastore::ParsedIndex& index) {
  std::string out = "repo\t" + field(index.repo_name) + "\n";
  for (const novastore::ParsedApp& app : index.apps) {
    out += "app";
    for (const std::string& value :
         {app.package_name, app.name, field(app.summary), field(app.description),
          field(app.developer), field(app.icon_url), field(app.license),
          field(app.categories), field(app.website), field(app.source_code),
          field(app.changelog), field(app.added), field(app.last_updated)}) {
      dump_field(&out, false, value);
    }
    out.push_back('\n');
  }
  for (const novastore::ParsedVersion& version : index.versions) {
    out += "version\t" + version.package_name + "\t" + std::to_string(version.version_code) +
           "\t" + field(version.version_name) + "\t" + version.download_url + "\t" +
           field(version.sha256) + "\t" + field(version.size) + "\t" +
           field(version.min_sdk) + "\t" + field(version.target_sdk) + "\t" +
           field(version.added) + "\t" + field(version.signer) + "\t" +
           field(version.native_code) + "\n";
  }
  return out;
}

int dump_file(int argc, char** argv) {
  // dump_index <file> <baseUrl> <v1|v2> <comma separated locales>
  std::FILE* file = std::fopen(argv[1], "rb");
  if (file == nullptr) return 2;
  std::string json;
  char chunk[65536];
  size_t read = 0;
  while ((read = std::fread(chunk, 1, sizeof(chunk), file)) > 0) {
    json.append(chunk, read);
  }
  std::fclose(file);

  std::vector<std::string> locales;
  const std::string locale_list = argc > 4 ? argv[4] : "en";
  size_t start = 0;
  while (start <= locale_list.size()) {
    const size_t comma = locale_list.find(',', start);
    locales.push_back(locale_list.substr(start, comma - start));
    if (comma == std::string::npos) break;
    start = comma + 1;
  }

  novastore::ParsedIndex index;
  std::string error;
  const std::vector<uint8_t> bytes(json.begin(), json.end());
  const bool v2 = argc <= 3 || std::string(argv[3]) == "v2";
  const bool ok = v2 ? novastore::parse_v2(bytes.data(), bytes.size(), argv[2], locales, &index, &error)
                     : novastore::parse_v1(bytes.data(), bytes.size(), argv[2], locales, &index, &error);
  if (!ok) {
    std::fprintf(stderr, "parse error: %s\n", error.c_str());
    return 3;
  }
  const std::string dump = dump_index(index);
  std::fwrite(dump.data(), 1, dump.size(), stdout);
  return 0;
}

}  // namespace

#define RUN(fn)               \
  do {                        \
    std::fprintf(stderr, "-- %s\n", #fn); \
    fn();                     \
  } while (0)

int main(int argc, char** argv) {
  if (argc > 1) return dump_file(argc, argv);
  RUN(test_index_v2_basic);
  RUN(test_index_v2_locale_ranking);
  RUN(test_index_v2_url_resolution);
  RUN(test_index_v2_dropped_values);
  RUN(test_numbers);
  RUN(test_index_v1);
  RUN(test_lenient_input);
  RUN(test_escapes_and_utf8);
  RUN(test_malformed_input_is_rejected);
  RUN(test_negative_optional_numbers_fall_back_to_gson);
  RUN(test_nesting_limit);
  RUN(test_serialization_layout);
  RUN(test_serialization_rejects_oversized_input);

  std::fprintf(stderr, "%d checks, %d failures\n", g_checks, g_failures);
  return g_failures == 0 ? 0 : 1;
}
