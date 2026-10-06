#!/usr/bin/env bash
# Run all JVM unit tests for the release variant.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew testReleaseUnitTest