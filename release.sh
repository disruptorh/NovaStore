#!/usr/bin/env bash
#
# Cut a release from the current commit.
#
# Does, in order:
#   1. requires a clean working tree;
#   2. refuses to run if there are no code changes since the last release tag;
#   3. bumps versionCode (+1) and versionName (patch +1) in app/build.gradle.kts;
#   4. rewrites the vX.Y.Z tag example in README.md;
#   5. commits, creates an annotated vX.Y.Z tag and pushes both to origin.
#
# GitHub Actions (.github/workflows/build.yml) then builds the APK and publishes
# the GitHub Release for that tag.
#
# Usage:
#   ./release.sh
#
set -euo pipefail

cd "$(dirname "$0")"

GRADLE="app/build.gradle.kts"
README="README.md"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

die() { echo "error: $*" >&2; exit 1; }

[ -f "$GRADLE" ] || die "$GRADLE not found (run from the repo root)"
[ "$BRANCH" != "HEAD" ] || die "detached HEAD; check out a branch first"

# 1. The bump commit must only touch the version files.
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

# 5. Update version files.
sed -i "s/versionCode = $OLD_CODE/versionCode = $NEW_CODE/" "$GRADLE"
sed -i "s/versionName = \"$OLD_NAME\"/versionName = \"$NEW_NAME\"/" "$GRADLE"
sed -i "s/v$OLD_NAME/v$NEW_NAME/g" "$README"

# 6. Commit, tag and push.
git add "$GRADLE" "$README"
git commit -m "chore(release): $NEW_TAG (build $NEW_CODE)"
git tag -a "$NEW_TAG" -m "Nova Store $NEW_NAME (build $NEW_CODE)"
git push origin "$BRANCH"
git push origin "$NEW_TAG"

echo "Done. CI will build and publish $NEW_TAG."
