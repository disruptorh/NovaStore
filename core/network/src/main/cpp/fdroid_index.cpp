#include "fdroid_index.h"

#include <climits>
#include <cstring>

namespace novastore {
namespace {

bool is_space(uint8_t c) {
  return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\v' ||
         (c >= 0x1C && c <= 0x1F);
}

bool is_hex(uint8_t c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

int hex_value(uint8_t c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return c - 'A' + 10;
}

bool is_digit(uint8_t c) { return c >= '0' && c <= '9'; }

char lower(char c) { return (c >= 'A' && c <= 'Z') ? static_cast<char>(c + 32) : c; }

bool equals_ignore_case(const std::string& a, const std::string& b) {
  if (a.size() != b.size()) return false;
  for (size_t i = 0; i < a.size(); ++i) {
    if (lower(a[i]) != lower(b[i])) return false;
  }
  return true;
}

bool starts_with(const std::string& s, const char* prefix) {
  const size_t n = std::strlen(prefix);
  return s.size() >= n && std::memcmp(s.data(), prefix, n) == 0;
}

bool starts_with_ignore_case(const std::string& s, const char* prefix) {
  const size_t n = std::strlen(prefix);
  if (s.size() < n) return false;
  for (size_t i = 0; i < n; ++i) {
    if (lower(s[i]) != lower(prefix[i])) return false;
  }
  return true;
}

std::string ascii_lowercase(const std::string& s) {
  std::string out(s);
  for (char& c : out) c = lower(c);
  return out;
}

std::string trim(const std::string& s) {
  size_t begin = 0;
  size_t end = s.size();
  while (begin < end && is_space(static_cast<uint8_t>(s[begin]))) ++begin;
  while (end > begin && is_space(static_cast<uint8_t>(s[end - 1]))) --end;
  return s.substr(begin, end - begin);
}

bool is_blank(const std::string& s) {
  for (char c : s) {
    if (!is_space(static_cast<uint8_t>(c))) return false;
  }
  return true;
}

std::string replace_underscores(const std::string& s) {
  std::string out(s);
  for (char& c : out) {
    if (c == '_') c = '-';
  }
  return out;
}

std::string before_dash(const std::string& s) {
  const size_t at = s.find('-');
  return at == std::string::npos ? s : s.substr(0, at);
}

std::string trim_trailing_slashes(const std::string& s) {
  size_t end = s.size();
  while (end > 0 && s[end - 1] == '/') --end;
  return s.substr(0, end);
}

// Long.parseLong semantics: optional sign, then decimal digits only.
bool parse_int64(const std::string& text, int64_t* out) {
  if (text.empty()) return false;
  size_t i = 0;
  bool negative = false;
  if (text[0] == '+' || text[0] == '-') {
    negative = text[0] == '-';
    i = 1;
  }
  if (i >= text.size()) return false;
  uint64_t value = 0;
  for (; i < text.size(); ++i) {
    const char c = text[i];
    if (!is_digit(static_cast<uint8_t>(c))) return false;
    const uint64_t digit = static_cast<uint64_t>(c - '0');
    if (value > (UINT64_MAX - digit) / 10) return false;
    value = value * 10 + digit;
  }
  const uint64_t limit = negative ? 9223372036854775808ULL : 9223372036854775807ULL;
  if (value > limit) return false;
  *out = negative ? -static_cast<int64_t>(value) : static_cast<int64_t>(value);
  return true;
}

// java.math.BigDecimal(text).longValue(): sign, digits, optional fraction and
// exponent, then truncation toward zero and saturation instead of a throw.
bool decimal_to_int64(const std::string& text, int64_t* out) {
  size_t i = 0;
  bool negative = false;
  if (i < text.size() && (text[i] == '+' || text[i] == '-')) {
    negative = text[i] == '-';
    ++i;
  }
  std::string integer_digits;
  std::string fraction_digits;
  bool seen_dot = false;
  size_t digit_count = 0;
  for (; i < text.size(); ++i) {
    const char c = text[i];
    if (is_digit(static_cast<uint8_t>(c))) {
      ++digit_count;
      (seen_dot ? fraction_digits : integer_digits).push_back(c);
      continue;
    }
    if (c == '.' && !seen_dot) {
      seen_dot = true;
      continue;
    }
    break;
  }
  if (digit_count == 0) return false;

  long long exponent = 0;
  if (i < text.size() && (text[i] == 'e' || text[i] == 'E')) {
    ++i;
    bool exponent_negative = false;
    if (i < text.size() && (text[i] == '+' || text[i] == '-')) {
      exponent_negative = text[i] == '-';
      ++i;
    }
    if (i >= text.size() || !is_digit(static_cast<uint8_t>(text[i]))) return false;
    for (; i < text.size() && is_digit(static_cast<uint8_t>(text[i])); ++i) {
      if (exponent < 100000) exponent = exponent * 10 + (text[i] - '0');
    }
    if (exponent_negative) exponent = -exponent;
  }
  if (i != text.size()) return false;

  // Truncation toward zero keeps the digits left of the decimal point, after
  // the exponent has been applied.
  const std::string all = integer_digits + fraction_digits;
  long long point = static_cast<long long>(integer_digits.size()) + exponent;
  if (point < 0) point = 0;
  if (point > 64) point = 64;
  std::string kept;
  kept.reserve(static_cast<size_t>(point));
  for (long long k = 0; k < point; ++k) {
    kept.push_back(static_cast<size_t>(k) < all.size() ? all[static_cast<size_t>(k)] : '0');
  }

  uint64_t magnitude = 0;
  for (const char c : kept) {
    const uint64_t digit = static_cast<uint64_t>(c - '0');
    if (magnitude > (UINT64_MAX - digit) / 10) {
      *out = negative ? INT64_MIN : INT64_MAX;
      return true;
    }
    magnitude = magnitude * 10 + digit;
  }
  const uint64_t limit = negative ? 9223372036854775808ULL : 9223372036854775807ULL;
  if (magnitude > limit) {
    *out = negative ? INT64_MIN : INT64_MAX;
    return true;
  }
  *out = negative ? -static_cast<int64_t>(magnitude) : static_cast<int64_t>(magnitude);
  return true;
}

class Scanner {
 public:
  enum class Token {
    kEnd,
    kObjectBegin,
    kObjectEnd,
    kArrayBegin,
    kArrayEnd,
    kString,
    kNumber,
    kBoolean,
    kNull,
    kLiteral,
  };

  Scanner(const uint8_t* data, size_t size) : cur_(data), end_(data + size) {}

  bool fail(const char* message) {
    if (error_.empty()) error_ = message;
    return false;
  }

  bool ok() const { return error_.empty(); }
  const std::string& error() const { return error_; }

  void skip_blanks() {
    for (;;) {
      while (cur_ < end_ && is_space(*cur_)) ++cur_;
      if (cur_ + 1 < end_ && cur_[0] == '/' && cur_[1] == '/') {
        cur_ += 2;
        while (cur_ < end_ && *cur_ != '\n') ++cur_;
        continue;
      }
      if (cur_ < end_ && *cur_ == '#') {
        while (cur_ < end_ && *cur_ != '\n') ++cur_;
        continue;
      }
      if (cur_ + 1 < end_ && cur_[0] == '/' && cur_[1] == '*') {
        cur_ += 2;
        while (cur_ + 1 < end_ && !(cur_[0] == '*' && cur_[1] == '/')) ++cur_;
        if (cur_ + 1 >= end_) {
          cur_ = end_;
          fail("unterminated comment");
          return;
        }
        cur_ += 2;
        continue;
      }
      break;
    }
  }

  Token peek() {
    skip_blanks();
    if (cur_ >= end_) return Token::kEnd;
    switch (*cur_) {
      case '{': return Token::kObjectBegin;
      case '}': return Token::kObjectEnd;
      case '[': return Token::kArrayBegin;
      case ']': return Token::kArrayEnd;
      case '"':
      case '\'': return Token::kString;
      case 't':
      case 'f': return Token::kBoolean;
      case 'n': return Token::kNull;
      default:
        if (*cur_ == '-' || is_digit(*cur_)) return Token::kNumber;
        return Token::kLiteral;
    }
  }

  bool begin_object() {
    skip_blanks();
    if (cur_ >= end_ || *cur_ != '{') return fail("expected '{'");
    ++cur_;
    return true;
  }

  bool begin_array() {
    skip_blanks();
    if (cur_ >= end_ || *cur_ != '[') return fail("expected '['");
    ++cur_;
    return true;
  }

  // Reads the next "name": value pair. Returns false when the object ended
  // (the '}' is consumed) or when the input failed; ok() tells them apart.
  // Only the document's own object may end at end of input; a nested one that
  // stops early is malformed input, not a short document.
  bool next_member(std::string* name, bool allow_end_of_input = false) {
    skip_blanks();
    if (cur_ < end_ && (*cur_ == ',' || *cur_ == ';')) {
      ++cur_;
      skip_blanks();
    }
    if (cur_ < end_ && *cur_ == '}') {
      ++cur_;
      return false;
    }
    if (cur_ >= end_) {
      if (allow_end_of_input) return false;
      return fail("unterminated object");
    }
    if (!read_name(name)) return false;
    skip_blanks();
    if (cur_ < end_ && (*cur_ == ':' || *cur_ == '=')) {
      ++cur_;
      if (cur_ < end_ && *cur_ == '>') ++cur_;
      return true;
    }
    return fail("expected ':' after member name");
  }

  // Same contract as next_member, for arrays.
  bool next_element() {
    skip_blanks();
    if (cur_ < end_ && (*cur_ == ',' || *cur_ == ';')) {
      ++cur_;
      skip_blanks();
    }
    if (cur_ < end_ && *cur_ == ']') {
      ++cur_;
      return false;
    }
    if (cur_ >= end_) return fail("unterminated array");
    return true;
  }

  bool read_string(std::string* out) {
    skip_blanks();
    if (cur_ >= end_) return fail("expected a string");
    const uint8_t quote = *cur_;
    if (quote != '"' && quote != '\'') return fail("expected a string");
    ++cur_;
    out->clear();
    const uint8_t* chunk = cur_;
    for (;;) {
      if (cur_ >= end_) return fail("unterminated string");
      const uint8_t c = *cur_;
      if (c == quote) {
        if (!append_utf8(out, chunk, cur_)) return false;
        ++cur_;
        return true;
      }
      if (c != '\\') {
        ++cur_;
        continue;
      }
      if (!append_utf8(out, chunk, cur_)) return false;
      ++cur_;
      if (cur_ >= end_) return fail("unterminated escape");
      const uint8_t escaped = *cur_++;
      switch (escaped) {
        case '"': out->push_back('"'); break;
        case '\'': out->push_back('\''); break;
        case '\\': out->push_back('\\'); break;
        case '/': out->push_back('/'); break;
        case 'b': out->push_back('\b'); break;
        case 'f': out->push_back('\f'); break;
        case 'n': out->push_back('\n'); break;
        case 'r': out->push_back('\r'); break;
        case 't': out->push_back('\t'); break;
        case 'u': {
          if (!append_unicode_escape(out)) return false;
          break;
        }
        default:
          return fail("invalid escape sequence");
      }
      chunk = cur_;
    }
  }

  // Reads a bare token (number, true/false/null or a lenient unquoted value).
  bool read_bare(std::string* out) {
    skip_blanks();
    const uint8_t* start = cur_;
    while (cur_ < end_ && !is_space(*cur_) && *cur_ != ',' && *cur_ != '}' && *cur_ != ']' &&
           *cur_ != ':' && *cur_ != '/' && *cur_ != '#') {
      ++cur_;
    }
    return append_utf8(out, start, cur_);
  }

  bool skip_value(int depth) {
    if (depth > kMaxDepth) return fail("nesting too deep");
    switch (peek()) {
      case Token::kObjectBegin: {
        ++cur_;
        std::string name;
        while (next_member(&name)) {
          if (!skip_value(depth + 1)) return false;
        }
        return ok();
      }
      case Token::kArrayBegin: {
        ++cur_;
        while (next_element()) {
          if (!skip_value(depth + 1)) return false;
        }
        return ok();
      }
      case Token::kString: {
        std::string sink;
        return read_string(&sink);
      }
      case Token::kEnd:
      case Token::kObjectEnd:
      case Token::kArrayEnd:
        return fail("unexpected end of input");
      default: {
        std::string sink;
        return read_bare(&sink);
      }
    }
  }

 private:
  bool read_name(std::string* out) {
    skip_blanks();
    if (cur_ < end_ && (*cur_ == '"' || *cur_ == '\'')) return read_string(out);
    const uint8_t* start = cur_;
    while (cur_ < end_ && *cur_ != ':' && *cur_ != '=' && !is_space(*cur_)) ++cur_;
    if (cur_ == start) return fail("expected a member name");
    // The caller reuses one buffer across members, so replace, never append.
    out->clear();
    return append_utf8(out, start, cur_);
  }

  bool read_hex4(uint32_t* out) {
    if (cur_ + 4 > end_) return fail("truncated unicode escape");
    uint32_t value = 0;
    for (int i = 0; i < 4; ++i) {
      if (!is_hex(cur_[i])) return fail("invalid unicode escape");
      value = (value << 4) | static_cast<uint32_t>(hex_value(cur_[i]));
    }
    cur_ += 4;
    *out = value;
    return true;
  }

  bool append_unicode_escape(std::string* out) {
    uint32_t code = 0;
    if (!read_hex4(&code)) return false;
    if (code >= 0xD800 && code <= 0xDBFF) {
      if (cur_ + 1 < end_ && cur_[0] == '\\' && cur_[1] == 'u') {
        cur_ += 2;
        uint32_t low = 0;
        if (!read_hex4(&low)) return false;
        if (low >= 0xDC00 && low <= 0xDFFF) {
          append_code_point(out, 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00));
          return true;
        }
        // Not a pair: both halves become replacement characters.
        out->append("\xEF\xBF\xBD");
        append_code_point(out, low);
        return true;
      }
      out->append("\xEF\xBF\xBD");
      return true;
    }
    if (code >= 0xDC00 && code <= 0xDFFF) {
      out->append("\xEF\xBF\xBD");
      return true;
    }
    append_code_point(out, code);
    return true;
  }

  static void append_code_point(std::string* out, uint32_t code) {
    if (code < 0x80) {
      out->push_back(static_cast<char>(code));
    } else if (code < 0x800) {
      out->push_back(static_cast<char>(0xC0 | (code >> 6)));
      out->push_back(static_cast<char>(0x80 | (code & 0x3F)));
    } else if (code < 0x10000) {
      out->push_back(static_cast<char>(0xE0 | (code >> 12)));
      out->push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
      out->push_back(static_cast<char>(0x80 | (code & 0x3F)));
    } else {
      out->push_back(static_cast<char>(0xF0 | (code >> 18)));
      out->push_back(static_cast<char>(0x80 | ((code >> 12) & 0x3F)));
      out->push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
      out->push_back(static_cast<char>(0x80 | (code & 0x3F)));
    }
  }

  static void append_replacement(std::string* out) { out->append("\xEF\xBF\xBD"); }

  // Copies a raw byte run as UTF-8, replacing malformed sequences with
  // U+FFFD exactly like java.io.InputStreamReader does.
  bool append_utf8(std::string* out, const uint8_t* begin, const uint8_t* end) {
    const uint8_t* p = begin;
    while (p < end) {
      const uint8_t c = *p;
      if (c < 0x80) {
        out->push_back(static_cast<char>(c));
        ++p;
        continue;
      }
      size_t length = 0;
      uint32_t code = 0;
      if (c >= 0xC2 && c <= 0xDF) {
        length = 2;
        code = c & 0x1Fu;
      } else if (c >= 0xE0 && c <= 0xEF) {
        length = 3;
        code = c & 0x0Fu;
      } else if (c >= 0xF0 && c <= 0xF4) {
        length = 4;
        code = c & 0x07u;
      } else {
        append_replacement(out);
        ++p;
        continue;
      }
      if (static_cast<size_t>(end - p) < length) {
        append_replacement(out);
        ++p;
        continue;
      }
      bool valid = true;
      for (size_t i = 1; i < length; ++i) {
        if ((p[i] & 0xC0) != 0x80) {
          valid = false;
          break;
        }
        code = (code << 6) | (p[i] & 0x3Fu);
      }
      const bool overlong = (length == 2 && code < 0x80) || (length == 3 && code < 0x800) ||
                            (length == 4 && code < 0x10000);
      if (valid && !overlong && code <= 0x10FFFF) {
        out->append(reinterpret_cast<const char*>(p), length);
        p += length;
        continue;
      }
      append_replacement(out);
      ++p;
    }
    return true;
  }

  const uint8_t* cur_;
  const uint8_t* end_;
  std::string error_;
};

// The parser proper. One instance handles one index document.
class Parser {
 public:
  Parser(Scanner& scanner, const std::string& base_url,
         const std::vector<std::string>& locales)
      : s_(scanner), base_(trim_trailing_slashes(base_url)), locales_(locales) {}

  bool parse_v2(ParsedIndex* out) {
    if (!s_.begin_object()) return false;
    std::string name;
    while (s_.next_member(&name, true)) {
      if (name == "repo") {
        if (!read_v2_repo_name(out)) return false;
      } else if (name == "packages") {
        if (!s_.begin_object()) return false;
        std::string package_name;
        while (s_.next_member(&package_name)) {
          if (!read_v2_package(package_name, out)) return false;
        }
        if (!s_.ok()) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    return s_.ok();
  }

  bool parse_v1(ParsedIndex* out) {
    if (!s_.begin_object()) return false;
    std::string name;
    while (s_.next_member(&name, true)) {
      if (name == "repo") {
        if (!read_v1_repo_name(out)) return false;
      } else if (name == "apps") {
        if (!s_.begin_array()) return false;
        while (s_.next_element()) {
          ParsedApp app;
          bool keep = false;
          if (!read_v1_app(&app, &keep)) return false;
          if (keep) out->apps.push_back(std::move(app));
        }
        if (!s_.ok()) return false;
      } else if (name == "packages") {
        if (!s_.begin_object()) return false;
        std::string package_name;
        while (s_.next_member(&package_name)) {
          if (!s_.begin_array()) return false;
          while (s_.next_element()) {
            ParsedVersion version;
            bool keep = false;
            if (!read_v1_version(package_name, &version, &keep)) return false;
            if (keep) out->versions.push_back(std::move(version));
          }
          if (!s_.ok()) return false;
        }
        if (!s_.ok()) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    return s_.ok();
  }

 private:
  // ---------------------------------------------------------------- helpers

  std::string resolve(const std::string& path) const {
    if (starts_with(path, "http://") || starts_with(path, "https://")) return path;
    if (!path.empty() && path[0] == '/') return base_ + path;
    return base_ + "/" + path;
  }

  // Lower is better: exact preferred locale, then same language, then English,
  // then anything. Mirrors RepoIndexParser.rankOf, including its first match
  // wins behaviour.
  int rank_of(const std::string& locale) const {
    const std::string normalized = replace_underscores(locale);
    for (size_t i = 0; i < locales_.size(); ++i) {
      if (equals_ignore_case(normalized, locales_[i])) return static_cast<int>(i) * 2;
      if (equals_ignore_case(before_dash(normalized), before_dash(locales_[i]))) {
        return static_cast<int>(i) * 2 + 1;
      }
    }
    if (equals_ignore_case(normalized, "en-US")) return 1000;
    if (starts_with_ignore_case(normalized, "en")) return 1001;
    return 2000;
  }

  // readStringOrNull: STRING and NUMBER keep their literal text, BOOLEAN
  // becomes "true"/"false", anything else is skipped and stays absent.
  bool read_string_or_null(std::optional<std::string>* out) {
    switch (s_.peek()) {
      case Scanner::Token::kString: {
        std::string value;
        if (!s_.read_string(&value)) return false;
        *out = std::move(value);
        return true;
      }
      // NUMBER, BOOLEAN and unquoted literals all go through nextString().
      case Scanner::Token::kNumber:
      case Scanner::Token::kBoolean:
      case Scanner::Token::kLiteral: {
        std::string value;
        if (!s_.read_bare(&value)) return false;
        *out = std::move(value);
        return true;
      }
      default:
        out->reset();
        return s_.skip_value(0);
    }
  }

  // readLongOrNull: NUMBER goes through BigDecimal truncation, STRING through
  // Long.parseLong on the trimmed text.
  bool read_long_or_null(std::optional<int64_t>* out) {
    return read_long_value(out, /*allow_negative=*/false);
  }

  // versionCode is mandatory and is stored verbatim, so it keeps whatever sign
  // the index declares.
  bool read_required_long(std::optional<int64_t>* out) {
    return read_long_value(out, /*allow_negative=*/true);
  }

  // The flat buffer marks an absent optional number with -1, so a declared
  // negative value could not be told apart from a missing one. Rejecting the
  // index hands it to the Gson reference parser, which keeps both results
  // identical instead of dropping the field.
  bool read_long_value(std::optional<int64_t>* out, bool allow_negative) {
    switch (s_.peek()) {
      case Scanner::Token::kNumber: {
        std::string text;
        if (!s_.read_bare(&text)) return false;
        int64_t value = 0;
        if (decimal_to_int64(text, &value)) {
          if (value < 0 && !allow_negative) return false;
          *out = value;
        }
        return true;
      }
      case Scanner::Token::kString:
      case Scanner::Token::kLiteral: {
        std::string text;
        if (s_.peek() == Scanner::Token::kString) {
          if (!s_.read_string(&text)) return false;
        } else if (!s_.read_bare(&text)) {
          return false;
        }
        int64_t value = 0;
        if (parse_int64(trim(text), &value)) {
          if (value < 0 && !allow_negative) return false;
          *out = value;
        }
        return true;
      }
      default:
        out->reset();
        return s_.skip_value(0);
    }
  }

  bool read_int_or_null(std::optional<int32_t>* out) {
    std::optional<int64_t> wide;
    if (!read_long_or_null(&wide)) return false;
    if (wide.has_value()) *out = static_cast<int32_t>(*wide);
    return true;
  }

  bool read_string_array(std::vector<std::string>* out) {
    if (s_.peek() != Scanner::Token::kArrayBegin) {
      out->clear();
      return s_.skip_value(0);
    }
    if (!s_.begin_array()) return false;
    out->clear();
    while (s_.next_element()) {
      std::optional<std::string> value;
      if (!read_string_or_null(&value)) return false;
      if (value.has_value()) out->push_back(std::move(*value));
    }
    return s_.ok();
  }

  // readLocalizedText: keeps the best ranked locale of a {locale: text} map.
  bool read_localized_text(std::optional<std::string>* out) {
    const Scanner::Token token = s_.peek();
    if (token == Scanner::Token::kString) return read_string_or_null(out);
    if (token == Scanner::Token::kLiteral) {
      std::string value;
      if (!s_.read_bare(&value)) return false;
      *out = std::move(value);
      return true;
    }
    if (token != Scanner::Token::kObjectBegin) {
      out->reset();
      return s_.skip_value(0);
    }
    if (!s_.begin_object()) return false;
    std::optional<std::string> best;
    int best_rank = INT_MAX;
    std::string locale;
    while (s_.next_member(&locale)) {
      if (s_.peek() == Scanner::Token::kString) {
        std::string value;
        if (!s_.read_string(&value)) return false;
        const int rank = rank_of(locale);
        if (!best.has_value() || rank < best_rank) {
          best = std::move(value);
          best_rank = rank;
        }
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    if (!s_.ok()) return false;
    *out = std::move(best);
    return true;
  }

  // readLocalizedFileName: best ranked {locale: {name: …}} entry.
  bool read_localized_file_name(std::optional<std::string>* out) {
    if (s_.peek() != Scanner::Token::kObjectBegin) {
      out->reset();
      return s_.skip_value(0);
    }
    if (!s_.begin_object()) return false;
    std::optional<std::string> best;
    int best_rank = INT_MAX;
    std::string locale;
    while (s_.next_member(&locale)) {
      std::optional<std::string> name;
      if (s_.peek() == Scanner::Token::kObjectBegin) {
        if (!s_.begin_object()) return false;
        std::string key;
        while (s_.next_member(&key)) {
          if (key == "name") {
            if (!read_string_or_null(&name)) return false;
          } else if (!s_.skip_value(0)) {
            return false;
          }
        }
        if (!s_.ok()) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
      const int rank = rank_of(locale);
      if (name.has_value() && (!best.has_value() || rank < best_rank)) {
        best = std::move(name);
        best_rank = rank;
      }
    }
    if (!s_.ok()) return false;
    *out = std::move(best);
    return true;
  }

  // ------------------------------------------------------------------- v2

  bool read_v2_repo_name(ParsedIndex* out) {
    if (!s_.begin_object()) return false;
    std::string key;
    while (s_.next_member(&key)) {
      if (key == "name") {
        if (!read_localized_text(&out->repo_name)) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    return s_.ok();
  }

  bool read_v2_package(const std::string& package_name, ParsedIndex* out) {
    ParsedApp app;
    bool has_metadata = false;
    std::vector<ParsedVersion> package_versions;
    if (!s_.begin_object()) return false;
    std::string key;
    while (s_.next_member(&key)) {
      if (key == "metadata") {
        if (!read_v2_metadata(package_name, &app)) return false;
        has_metadata = true;
      } else if (key == "versions") {
        if (!s_.begin_object()) return false;
        std::string version_key;  // keyed by APK sha256, not by version code
        while (s_.next_member(&version_key)) {
          ParsedVersion version;
          bool keep = false;
          if (!read_v2_version(package_name, &version, &keep)) return false;
          if (keep) package_versions.push_back(std::move(version));
        }
        if (!s_.ok()) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    if (!s_.ok()) return false;
    // A package without a single downloadable version is not installable,
    // so it never reaches the catalog.
    if (package_versions.empty()) return true;
    if (!has_metadata) {
      app = ParsedApp();
      app.package_name = package_name;
      app.name = package_name;
    }
    out->apps.push_back(std::move(app));
    for (ParsedVersion& version : package_versions) out->versions.push_back(std::move(version));
    return true;
  }

  bool read_v2_metadata(const std::string& package_name, ParsedApp* app) {
    std::optional<std::string> name;
    std::optional<std::string> summary;
    std::optional<std::string> description;
    std::optional<std::string> author;
    std::optional<std::string> icon;
    std::optional<std::string> license;
    std::vector<std::string> categories;
    std::optional<std::string> website;
    std::optional<std::string> source_code;
    std::optional<std::string> changelog;
    std::optional<int64_t> added;
    std::optional<int64_t> last_updated;
    if (!s_.begin_object()) return false;
    std::string key;
    while (s_.next_member(&key)) {
      if (key == "name") {
        if (!read_localized_text(&name)) return false;
      } else if (key == "summary") {
        if (!read_localized_text(&summary)) return false;
      } else if (key == "description") {
        if (!read_localized_text(&description)) return false;
      } else if (key == "authorName") {
        if (!read_string_or_null(&author)) return false;
      } else if (key == "icon") {
        if (!read_localized_file_name(&icon)) return false;
      } else if (key == "license") {
        if (!read_string_or_null(&license)) return false;
      } else if (key == "categories") {
        if (!read_string_array(&categories)) return false;
      } else if (key == "webSite") {
        if (!read_string_or_null(&website)) return false;
      } else if (key == "sourceCode") {
        if (!read_string_or_null(&source_code)) return false;
      } else if (key == "changelog") {
        if (!read_string_or_null(&changelog)) return false;
      } else if (key == "added") {
        if (!read_long_or_null(&added)) return false;
      } else if (key == "lastUpdated") {
        if (!read_long_or_null(&last_updated)) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    if (!s_.ok()) return false;

    app->package_name = package_name;
    if (name.has_value()) {
      const std::string trimmed = trim(*name);
      app->name = is_blank(trimmed) ? package_name : trimmed;
    } else {
      app->name = package_name;
    }
    if (summary.has_value()) app->summary = trim(*summary);
    if (description.has_value()) app->description = trim(*description);
    app->developer = std::move(author);
    if (icon.has_value()) app->icon_url = resolve(*icon);
    app->license = std::move(license);
    app->categories = std::move(categories);
    app->website = std::move(website);
    app->source_code = std::move(source_code);
    app->changelog = std::move(changelog);
    app->added = added;
    app->last_updated = last_updated;
    return true;
  }

  bool read_v2_version(const std::string& package_name, ParsedVersion* version, bool* keep) {
    std::optional<std::string> file_name;
    std::optional<std::string> sha256;
    std::optional<int64_t> size;
    std::optional<int64_t> added;
    std::optional<int64_t> version_code;
    std::optional<std::string> version_name;
    std::optional<int32_t> min_sdk;
    std::optional<int32_t> target_sdk;
    std::optional<std::string> signer;
    std::vector<std::string> native_code;
    if (!s_.begin_object()) return false;
    std::string key;
    while (s_.next_member(&key)) {
      if (key == "added") {
        if (!read_long_or_null(&added)) return false;
      } else if (key == "file") {
        if (!s_.begin_object()) return false;
        std::string file_key;
        while (s_.next_member(&file_key)) {
          if (file_key == "name") {
            if (!read_string_or_null(&file_name)) return false;
          } else if (file_key == "sha256") {
            if (!read_string_or_null(&sha256)) return false;
          } else if (file_key == "size") {
            if (!read_long_or_null(&size)) return false;
          } else if (!s_.skip_value(0)) {
            return false;
          }
        }
        if (!s_.ok()) return false;
      } else if (key == "manifest") {
        if (!s_.begin_object()) return false;
        std::string manifest_key;
        while (s_.next_member(&manifest_key)) {
          if (manifest_key == "versionCode") {
            if (!read_required_long(&version_code)) return false;
          } else if (manifest_key == "versionName") {
            if (!read_string_or_null(&version_name)) return false;
          } else if (manifest_key == "usesSdk") {
            if (!s_.begin_object()) return false;
            std::string sdk_key;
            while (s_.next_member(&sdk_key)) {
              if (sdk_key == "minSdkVersion") {
                if (!read_int_or_null(&min_sdk)) return false;
              } else if (sdk_key == "targetSdkVersion") {
                if (!read_int_or_null(&target_sdk)) return false;
              } else if (!s_.skip_value(0)) {
                return false;
              }
            }
            if (!s_.ok()) return false;
          } else if (manifest_key == "signer") {
            if (!s_.begin_object()) return false;
            std::string signer_key;
            while (s_.next_member(&signer_key)) {
              if (signer_key == "sha256") {
                std::vector<std::string> digests;
                if (!read_string_array(&digests)) return false;
                signer = digests.empty() ? std::optional<std::string>() : digests.front();
              } else if (!s_.skip_value(0)) {
                return false;
              }
            }
            if (!s_.ok()) return false;
          } else if (manifest_key == "nativecode") {
            if (!read_string_array(&native_code)) return false;
          } else if (!s_.skip_value(0)) {
            return false;
          }
        }
        if (!s_.ok()) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    if (!s_.ok()) return false;

    *keep = version_code.has_value() && file_name.has_value() && !is_blank(*file_name);
    if (!*keep) return true;

    version->package_name = package_name;
    version->version_code = *version_code;
    version->version_name = std::move(version_name);
    version->download_url = resolve(trim(*file_name));
    if (sha256.has_value()) version->sha256 = ascii_lowercase(*sha256);
    version->size = size;
    version->min_sdk = min_sdk;
    version->target_sdk = target_sdk;
    version->added = added;
    if (signer.has_value()) version->signer = ascii_lowercase(*signer);
    version->native_code = std::move(native_code);
    return true;
  }

  // ------------------------------------------------------------------- v1

  bool read_v1_repo_name(ParsedIndex* out) {
    if (!s_.begin_object()) return false;
    std::string key;
    while (s_.next_member(&key)) {
      if (key == "name") {
        if (!read_string_or_null(&out->repo_name)) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    return s_.ok();
  }

  struct LocalizedV1 {
    std::optional<std::string> name;
    std::optional<std::string> summary;
    std::optional<std::string> description;
    std::optional<std::string> icon;
    std::optional<std::string> icon_locale;
  };

  bool read_v1_app(ParsedApp* app, bool* keep) {
    std::optional<std::string> package_name;
    std::optional<std::string> name;
    std::optional<std::string> summary;
    std::optional<std::string> description;
    std::optional<std::string> author;
    std::optional<std::string> icon;
    std::optional<std::string> license;
    std::vector<std::string> categories;
    std::optional<std::string> website;
    std::optional<std::string> source_code;
    std::optional<std::string> changelog;
    std::optional<int64_t> added;
    std::optional<int64_t> last_updated;
    std::optional<LocalizedV1> localized;
    if (!s_.begin_object()) return false;
    std::string key;
    while (s_.next_member(&key)) {
      if (key == "packageName") {
        if (!read_string_or_null(&package_name)) return false;
      } else if (key == "name") {
        if (!read_string_or_null(&name)) return false;
      } else if (key == "summary") {
        if (!read_string_or_null(&summary)) return false;
      } else if (key == "description") {
        if (!read_string_or_null(&description)) return false;
      } else if (key == "authorName") {
        if (!read_string_or_null(&author)) return false;
      } else if (key == "icon") {
        if (!read_string_or_null(&icon)) return false;
      } else if (key == "license") {
        if (!read_string_or_null(&license)) return false;
      } else if (key == "categories") {
        if (!read_string_array(&categories)) return false;
      } else if (key == "webSite") {
        if (!read_string_or_null(&website)) return false;
      } else if (key == "sourceCode") {
        if (!read_string_or_null(&source_code)) return false;
      } else if (key == "changelog") {
        if (!read_string_or_null(&changelog)) return false;
      } else if (key == "added") {
        if (!read_long_or_null(&added)) return false;
      } else if (key == "lastUpdated") {
        if (!read_long_or_null(&last_updated)) return false;
      } else if (key == "localized") {
        LocalizedV1 value;
        if (!read_v1_localized(&value)) return false;
        localized = std::move(value);
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    if (!s_.ok()) return false;
    *keep = package_name.has_value();
    if (!*keep) return true;

    const std::string pkg = *package_name;
    std::optional<std::string> icon_url;
    if (localized.has_value() && localized->icon.has_value() &&
        localized->icon_locale.has_value()) {
      icon_url = resolve("/" + pkg + "/" + *localized->icon_locale + "/" + *localized->icon);
    } else if (icon.has_value()) {
      icon_url = resolve("/icons-640/" + *icon);
    }

    std::optional<std::string> effective_name =
        (localized.has_value() && localized->name.has_value()) ? localized->name : name;
    app->package_name = pkg;
    if (effective_name.has_value()) {
      const std::string trimmed = trim(*effective_name);
      app->name = is_blank(trimmed) ? pkg : trimmed;
    } else {
      app->name = pkg;
    }
    if (localized.has_value() && localized->summary.has_value()) {
      app->summary = trim(*localized->summary);
    } else if (summary.has_value()) {
      app->summary = trim(*summary);
    }
    if (localized.has_value() && localized->description.has_value()) {
      app->description = trim(*localized->description);
    } else if (description.has_value()) {
      app->description = trim(*description);
    }
    app->developer = std::move(author);
    app->icon_url = std::move(icon_url);
    app->license = std::move(license);
    app->categories = std::move(categories);
    app->website = std::move(website);
    app->source_code = std::move(source_code);
    app->changelog = std::move(changelog);
    app->added = added;
    app->last_updated = last_updated;
    return true;
  }

  // The best ranked locale wins even when it declares no text at all, which
  // is what RepoIndexParser.readV1Localized does.
  bool read_v1_localized(LocalizedV1* out) {
    struct Fields {
      std::optional<std::string> name;
      std::optional<std::string> summary;
      std::optional<std::string> description;
      std::optional<std::string> icon;
    };
    Fields best;
    bool has_best = false;
    int best_rank = INT_MAX;
    std::string best_locale;
    if (!s_.begin_object()) return false;
    std::string locale;
    while (s_.next_member(&locale)) {
      Fields fields;
      if (!s_.begin_object()) return false;
      std::string key;
      while (s_.next_member(&key)) {
        const bool tracked = key == "name" || key == "summary" || key == "description" || key == "icon";
        if (tracked && s_.peek() == Scanner::Token::kString) {
          std::optional<std::string> value;
          if (!read_string_or_null(&value)) return false;
          if (key == "name") fields.name = std::move(value);
          else if (key == "summary") fields.summary = std::move(value);
          else if (key == "description") fields.description = std::move(value);
          else fields.icon = std::move(value);
        } else if (!s_.skip_value(0)) {
          return false;
        }
      }
      if (!s_.ok()) return false;
      const int rank = rank_of(locale);
      if (!has_best || rank < best_rank) {
        best = std::move(fields);
        best_locale = locale;
        best_rank = rank;
        has_best = true;
      }
    }
    if (!s_.ok()) return false;
    if (!has_best) return true;
    out->name = std::move(best.name);
    out->summary = std::move(best.summary);
    out->description = std::move(best.description);
    out->icon = std::move(best.icon);
    out->icon_locale = best_locale;
    return true;
  }

  bool read_v1_version(const std::string& package_name, ParsedVersion* version, bool* keep) {
    std::optional<int64_t> version_code;
    std::optional<std::string> version_name;
    std::optional<std::string> apk_name;
    std::optional<std::string> hash;
    std::optional<std::string> hash_type;
    std::optional<int64_t> size;
    std::optional<int32_t> min_sdk;
    std::optional<int32_t> target_sdk;
    std::optional<int64_t> added;
    std::optional<std::string> signer;
    std::vector<std::string> native_code;
    if (!s_.begin_object()) return false;
    std::string key;
    while (s_.next_member(&key)) {
      if (key == "versionCode") {
        if (!read_required_long(&version_code)) return false;
      } else if (key == "versionName") {
        if (!read_string_or_null(&version_name)) return false;
      } else if (key == "apkName") {
        if (!read_string_or_null(&apk_name)) return false;
      } else if (key == "hash") {
        if (!read_string_or_null(&hash)) return false;
      } else if (key == "hashType") {
        if (!read_string_or_null(&hash_type)) return false;
      } else if (key == "size") {
        if (!read_long_or_null(&size)) return false;
      } else if (key == "minSdkVersion") {
        if (!read_int_or_null(&min_sdk)) return false;
      } else if (key == "targetSdkVersion") {
        if (!read_int_or_null(&target_sdk)) return false;
      } else if (key == "added") {
        if (!read_long_or_null(&added)) return false;
      } else if (key == "signer") {
        if (!read_string_or_null(&signer)) return false;
      } else if (key == "nativecode") {
        if (!read_string_array(&native_code)) return false;
      } else if (!s_.skip_value(0)) {
        return false;
      }
    }
    if (!s_.ok()) return false;

    *keep = version_code.has_value() && apk_name.has_value() && !is_blank(*apk_name);
    if (!*keep) return true;

    version->package_name = package_name;
    version->version_code = *version_code;
    version->version_name = std::move(version_name);
    version->download_url = resolve("/" + trim(*apk_name));
    const bool hash_is_sha256 =
        !hash_type.has_value() || equals_ignore_case(*hash_type, "sha256");
    if (hash.has_value() && hash_is_sha256) version->sha256 = ascii_lowercase(*hash);
    version->size = size;
    version->min_sdk = min_sdk;
    version->target_sdk = target_sdk;
    version->added = added;
    if (signer.has_value()) version->signer = ascii_lowercase(*signer);
    version->native_code = std::move(native_code);
    return true;
  }

  Scanner& s_;
  std::string base_;
  const std::vector<std::string>& locales_;
};

}  // namespace

bool parse_v2(const uint8_t* data, size_t size, const std::string& base_url,
              const std::vector<std::string>& locales, ParsedIndex* out,
              std::string* error) {
  Scanner scanner(data, size);
  Parser parser(scanner, base_url, locales);
  *out = ParsedIndex();
  if (parser.parse_v2(out)) return true;
  if (error != nullptr) *error = scanner.error();
  return false;
}

bool parse_v1(const uint8_t* data, size_t size, const std::string& base_url,
              const std::vector<std::string>& locales, ParsedIndex* out,
              std::string* error) {
  Scanner scanner(data, size);
  Parser parser(scanner, base_url, locales);
  *out = ParsedIndex();
  if (parser.parse_v1(out)) return true;
  if (error != nullptr) *error = scanner.error();
  return false;
}

}  // namespace novastore
