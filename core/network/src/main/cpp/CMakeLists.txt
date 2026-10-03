# Build for Android is driven by AGP (core/network externalNativeBuild). The
# same CMakeLists also builds the host test binary, because JVM unit tests
# cannot load the Android library:
#
#   cmake -S core/network/src/main/cpp -B build/native-test
#   cmake --build build/native-test
#   ctest --test-dir build/native-test --output-on-failure

cmake_minimum_required(VERSION 3.22.1)
project(novastore_native CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(ANDROID)
  add_library(novastore_native SHARED
      fdroid_index.cpp
      fdroid_flat_buffer.cpp
      jni_bridge.cpp)

  target_compile_options(novastore_native PRIVATE
      -O2
      -fno-exceptions
      -fno-rtti
      -fvisibility=hidden
      -Wall
      -Wextra)

  # No exceptions and no RTTI means no unwinder; link the static C++ runtime so
  # the APK ships one .so instead of two.
  target_link_options(novastore_native PRIVATE -static-libstdc++)
  target_link_libraries(novastore_native log)
else()
  add_executable(fdroid_index_test
      fdroid_index.cpp
      fdroid_flat_buffer.cpp
      tests/fdroid_index_test.cpp)

  target_include_directories(fdroid_index_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
  target_compile_options(fdroid_index_test PRIVATE -O1 -g -Wall -Wextra)

  enable_testing()
  add_test(NAME fdroid_index_test COMMAND fdroid_index_test)
endif()
