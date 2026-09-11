#!/usr/bin/env bash
# Fails the build if any published skainet-audio POM contains invalid coordinates.
#
# Adapted from the SKaiNET engine repo's script, which catches the class of bug shipped in
# SKaiNET 0.19.0: a module's POM declared a sibling dependency at `<group>:<artifact>:unspecified`
# because the sibling was not configured to publish and the root `allprojects { group = ... }`
# disagreed with `GROUP=` in gradle.properties.
#
# Two checks per generated POM under ~/.m2/repository/sk/ainet/audio/**:
#   1. No `<version>unspecified</version>` anywhere in the POM.
#   2. Every `<dependency>` whose `<artifactId>` starts with `audio-` uses
#      `<groupId>sk.ainet.audio</groupId>` — `project(...)` deps on sibling
#      modules must resolve to the same publish group.

set -euo pipefail

GROUP_PATH="sk/ainet/audio"
GROUP_ID="sk.ainet.audio"
REPO_ROOT="${HOME}/.m2/repository/${GROUP_PATH}"
EXPECTED_MODULES=(audio-core audio-wav audio-mel audio-source audio-stream audio-vad)

if [[ ! -d "${REPO_ROOT}" ]]; then
  echo "ERROR: no published artifacts found under ${REPO_ROOT}" >&2
  echo "Did ./gradlew publishToMavenLocal run successfully?" >&2
  exit 1
fi

# Every library module must have produced a root (KMP umbrella) POM at the expected coordinates.
for module in "${EXPECTED_MODULES[@]}"; do
  if [[ ! -d "${REPO_ROOT}/${module}" ]]; then
    echo "ERROR: ${GROUP_ID}:${module} was not published (missing ${REPO_ROOT}/${module})" >&2
    echo "Is com.vanniktech.maven.publish applied in ${module}/build.gradle.kts?" >&2
    exit 1
  fi
done

# Collect POM paths without `mapfile`: the workflow runs on macOS, whose /bin/bash is 3.2 and
# has no `mapfile`/`readarray`. A `while read` loop is portable to every bash the runners ship.
POMS=()
while IFS= read -r pom_path; do
  POMS+=("${pom_path}")
done < <(find "${REPO_ROOT}" -type f -name '*.pom' | sort)

if [[ ${#POMS[@]} -eq 0 ]]; then
  echo "ERROR: no .pom files under ${REPO_ROOT}" >&2
  exit 1
fi

echo "Scanning ${#POMS[@]} published POMs..."

report_file="$(mktemp)"
trap 'rm -f "${report_file}"' EXIT

for pom in "${POMS[@]}"; do
  rel="${pom#${REPO_ROOT}/}"

  if grep -Fq '<version>unspecified</version>' "${pom}"; then
    {
      echo "FAIL  ${rel}: contains <version>unspecified</version>"
      grep -n '<version>unspecified</version>' "${pom}" | sed 's/^/      /'
    } >> "${report_file}"
  fi

  bad_deps="$(awk -v group="${GROUP_ID}" '
    /<dependency>/                          { inDep=1; block=""; next }
    inDep                                   { block = block "\n" $0 }
    /<\/dependency>/ {
      inDep=0
      if (block ~ /<artifactId>audio-/ && block !~ ("<groupId>" group "</groupId>")) {
        print block
      }
    }
  ' "${pom}")"

  if [[ -n "${bad_deps}" ]]; then
    {
      echo "FAIL  ${rel}: audio-* dependency with non-${GROUP_ID} group"
      printf '%s\n' "${bad_deps}" | sed 's/^/      /'
    } >> "${report_file}"
  fi
done

if [[ -s "${report_file}" ]]; then
  cat "${report_file}" >&2
  echo "" >&2
  echo "POM validation failed." >&2
  exit 1
fi

echo "All ${#POMS[@]} POMs look good."
