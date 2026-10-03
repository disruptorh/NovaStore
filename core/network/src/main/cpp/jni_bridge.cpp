// JNI entry point: parse an index file and hand one flat buffer back.
//
// Returns null whenever the native parser cannot be trusted with the file
// (unreadable, malformed, or a nesting depth past the limit). The Kotlin side
// then runs the Gson parser, so the native path can never be the only path.

#include <jni.h>

#include <string>
#include <vector>

#include "fdroid_flat_buffer.h"
#include "fdroid_index.h"
#include "mapped_file.h"

namespace {

std::string to_utf8(JNIEnv* env, jstring value) {
  if (value == nullptr) return std::string();
  const char* chars = env->GetStringUTFChars(value, nullptr);
  std::string result = chars == nullptr ? std::string() : std::string(chars);
  if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
  return result;
}

std::vector<std::string> to_string_list(JNIEnv* env, jobjectArray values) {
  std::vector<std::string> result;
  if (values == nullptr) return result;
  const jsize count = env->GetArrayLength(values);
  result.reserve(static_cast<size_t>(count));
  for (jsize i = 0; i < count; ++i) {
    auto element = static_cast<jstring>(env->GetObjectArrayElement(values, i));
    result.push_back(to_utf8(env, element));
    if (element != nullptr) env->DeleteLocalRef(element);
  }
  return result;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_novastore_app_core_network_fdroid_NativeFdroidIndex_nativeParse(
    JNIEnv* env, jobject, jstring path, jstring base_url, jboolean v2_format,
    jobjectArray locales) {
  const std::string file_path = to_utf8(env, path);
  const std::string base = to_utf8(env, base_url);
  const std::vector<std::string> preferred = to_string_list(env, locales);

  novastore::MappedFile file;
  std::string error;
  if (!file.open(file_path.c_str(), &error)) return nullptr;

  novastore::ParsedIndex index;
  const bool parsed =
      v2_format == JNI_TRUE
          ? novastore::parse_v2(file.data(), file.size(), base, preferred, &index, &error)
          : novastore::parse_v1(file.data(), file.size(), base, preferred, &index, &error);
  if (!parsed) return nullptr;

  std::vector<uint8_t> buffer;
  if (!novastore::serialize_index(index, &buffer, &error)) return nullptr;

  auto* result = env->NewByteArray(static_cast<jsize>(buffer.size()));
  if (result == nullptr) return nullptr;
  env->SetByteArrayRegion(result, 0, static_cast<jsize>(buffer.size()),
                          reinterpret_cast<const jbyte*>(buffer.data()));
  return result;
}
