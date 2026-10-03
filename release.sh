#!/usr/bin/env bash
#
# Cut a full release from the current commit, end to end:
#   1. requires a clean working tree;
#   2. refuses to run if there are no code changes since the last release tag;
#   3. bumps versionCode (+1) and versionName (patch +1) in app/build.gradle.kts;
#   4. rewrites the vX.Y.Z tag example in README.md;
#   5. builds the signed release APK (app/build/outputs/apk/release/NovaStore-vX.Y.Z.apk);
#   6. commits, creates an annotated vX.Y.Z tag and pushes both to origin;
#   7. publishes the GitHub Release with the APK and SHA256SUMS.txt.
#
# Usage:
#   ./release.sh
#
# Override the toolchain by exporting JAVA_HOME / ANDROID_HOME beforehand.
set -euo pipefail

cd "$(dirname "$0")"

GRADLE="app/build.gradle.kts"
README="README.md"
APK_DIR="app/build/outputs/apk/release"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
ORIGIN_URL="$(git remote get-url origin 2>/dev/null || true)"
REPO="$(printf '%s' "$ORIGIN_URL" | sed -E 's#(git@|https://)github\.com[:/]##; s#\.git$##')"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-/home/reimen/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

die() { echo "error: $*" >&2; exit 1; }

[ -f "$GRADLE" ] || die "$GRADLE not found (run from the repo root)"
[ "$BRANCH" != "HEAD" ] || die "detached HEAD; check out a branch first"
[ -x "$JAVA_HOME/bin/java" ] || die "JDK not found at JAVA_HOME=$JAVA_HOME"
command -v gh >/dev/null || die "GitHub CLI (gh) not found"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated; run 'gh auth login'"
[ -n "$REPO" ] || die "could not determine GitHub repo from origin remote ($ORIGIN_URL)"

# 1. Clean tree.
[ -z "$(git status --porcelain)" ] || die "working tree is not clean; commit or stash your changes first"

# 2. Read the current version.
OLD_CODE="$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE" | head -1 || true)"
OLD_NAME="$(grep -oP 'versionName = "\K[^"]+' "$GRADLE" | head -1 || true)"
[ -n "$OLD_CODE" ] && [ -n "$OLD_NAME" ] || die "could not read versionCode/versionName from $GRADLE"

# 3. Refuse to release when only docs changed since the last tag.
LAST_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
if [ -n "$LAST_TAG" ]; then
    if git diff --quiet "$LAST_TAG"..HEAD -- \
        app core data domain feature \
        build.gradle.kts settings.gradle.kts gradle.properties gradle; then
        die "no code changes since $LAST_TAG - nothing to release"
    fi
fi

# 4. Compute the next version.
NEW_CODE=$((OLD_CODE + 1))
NEW_NAME="$(printf '%s' "$OLD_NAME" | awk -F. '{ printf "%d.%d.%d", $1, $2, $3 + 1 }')"
NEW_TAG="v$NEW_NAME"
[ -n "$NEW_NAME" ] || die "could not bump versionName '$OLD_NAME' (expected X.Y.Z)"
git rev-parse -q --verify "refs/tags/$NEW_TAG" >/dev/null && die "tag $NEW_TAG already exists"

echo "Release $OLD_NAME (build $OLD_CODE) -> $NEW_NAME (build $NEW_CODE)"

restore() { git checkout -- "$GRADLE" "$README" 2>/dev/null || true; }

# 5. Bump the version files, then build the signed APK (restore on failure).
sed -i "s/versionCode = $OLD_CODE/versionCode = $NEW_CODE/" "$GRADLE"
sed -i "s/versionName = \"$OLD_NAME\"/versionName = \"$NEW_NAME\"/" "$GRADLE"
sed -i "s/v$OLD_NAME/v$NEW_NAME/g" "$README"

echo "Building signed release APK..."
./gradlew :app:assembleRelease --console=plain || { restore; die "build failed"; }

APK="$(ls "$APK_DIR"/*.apk 2>/dev/null | head -1 || true)"
[ -n "$APK" ] || { restore; die "release APK not found in $APK_DIR"; }

OUT="$(mktemp -d)"
OUT_APK="$OUT/NovaStore-v$NEW_NAME.apk"
cp "$APK" "$OUT_APK"
( cd "$OUT" && sha256sum "$(basename "$OUT_APK")" > SHA256SUMS.txt )

# 6. Commit, tag and push.
git add "$GRADLE" "$README"
git commit -m "chore(release): $NEW_TAG (build $NEW_CODE)"
git tag -a "$NEW_TAG" -m "Nova Store $NEW_NAME (build $NEW_CODE)"
git push origin "$BRANCH"
git push origin "$NEW_TAG"

# 7. Publish the GitHub Release (always target the origin repo, never upstream).
if gh release view "$NEW_TAG" --repo "$REPO" >/dev/null 2>&1; then
    gh release upload "$NEW_TAG" "$OUT_APK" "$OUT/SHA256SUMS.txt" --repo "$REPO" --clobber
else
    gh release create "$NEW_TAG" "$OUT_APK" "$OUT/SHA256SUMS.txt" --repo "$REPO" \
        --title "Nova Store $NEW_NAME" --generate-notes
fi
rm -rf "$OUT"

echo "Done: $NEW_TAG released ($NEW_NAME, build $NEW_CODE)."
