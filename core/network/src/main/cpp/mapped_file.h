// Read-only memory mapping of the index file. The official F-Droid index is
// ~60 MB: mapping it keeps that out of the heap, so peak native memory stays
// close to the size of the parsed records.

#pragma once

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstdint>
#include <cstring>
#include <string>

namespace novastore {

class MappedFile {
 public:
  MappedFile() = default;
  MappedFile(const MappedFile&) = delete;
  MappedFile& operator=(const MappedFile&) = delete;

  ~MappedFile() {
    if (data_ != nullptr) ::munmap(const_cast<uint8_t*>(data_), size_);
    if (fd_ >= 0) ::close(fd_);
  }

  bool open(const char* path, std::string* error) {
    fd_ = ::open(path, O_RDONLY | O_CLOEXEC);
    if (fd_ < 0) {
      if (error != nullptr) *error = std::string("cannot open ") + path;
      return false;
    }
    struct stat info;
    if (::fstat(fd_, &info) != 0 || info.st_size <= 0) {
      if (error != nullptr) *error = std::string("cannot stat ") + path;
      return false;
    }
    size_ = static_cast<size_t>(info.st_size);
    void* mapped = ::mmap(nullptr, size_, PROT_READ, MAP_PRIVATE, fd_, 0);
    if (mapped == MAP_FAILED) {
      if (error != nullptr) *error = std::string("cannot map ") + path;
      return false;
    }
    data_ = static_cast<const uint8_t*>(mapped);
    return true;
  }

  const uint8_t* data() const { return data_; }
  size_t size() const { return size_; }

 private:
  const uint8_t* data_ = nullptr;
  size_t size_ = 0;
  int fd_ = -1;
};

}  // namespace novastore
