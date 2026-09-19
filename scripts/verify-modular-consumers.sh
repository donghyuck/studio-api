#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
review_dir="$(mktemp -d "${TMPDIR:-/tmp}/studio-consumer-matrix.XXXXXX")"
cd "$project_dir"
version="$(sed -n 's/^buildApplicationVersion=//p' gradle.properties)"
./gradlew -q -I verification/publish-local.gradle \
  "-PmodularityRepository=$review_dir/maven" publishAllPublicationsToModularityRepository
for modules in base team workspace ai team-ai full rag-minimal; do
  ./gradlew -q -p verification/modular-consumer \
    "-PstudioRepository=$review_dir/maven" "-PstudioVersion=$version" "-PmoduleSet=$modules" test
  cp -R verification/modular-consumer/build/test-results/test "$review_dir/$modules-results"
  echo "PASS: $modules"
done
echo "Consumer verification evidence: $review_dir"
