#!/usr/bin/env bash
set -euo pipefail
before="${PRGATE_BEFORE:?Missing PRGATE_BEFORE}"
after="${PRGATE_AFTER:?Missing PRGATE_AFTER}"
ref="${PRGATE_REF:?Missing PRGATE_REF}"
emit() {
  local output
  output=$(printf 'eligible=%s\nreason=%s\nchanged_files=%s\nrelevant_files=%s\n' "$1" "$2" "$3" "$4")
  printf '%s\n' "$output"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then printf '%s\n' "$output" >> "$GITHUB_OUTPUT"; fi
}
if [[ ! "$before" =~ ^([0-9a-f]{40}|[0-9a-f]{64})$ || ! "$after" =~ ^([0-9a-f]{40}|[0-9a-f]{64})$ ]]; then
  echo "Invalid revision" >&2; exit 1
fi
if [[ "$ref" != refs/heads/* ]]; then emit false NOT_A_BRANCH 0 0; exit 0; fi
if [[ "$after" =~ ^0+$ ]]; then emit false BRANCH_DELETED 0 0; exit 0; fi
# Never invent a baseline or replay history when the branch is new.
if [[ "$before" =~ ^0+$ ]]; then emit false BASELINE_UNAVAILABLE 0 0; exit 0; fi
if ! git cat-file -e "$before^{commit}" 2>/dev/null || ! git cat-file -e "$after^{commit}" 2>/dev/null; then
  emit false BASELINE_UNAVAILABLE 0 0; exit 0
fi
paths=$(mktemp)
trap 'rm -f "$paths"' EXIT
git diff --name-only -z --no-renames "$before" "$after" -- > "$paths"
changed=0
relevant=0
while IFS= read -r -d '' file; do
  changed=$((changed + 1))
  case "${file,,}" in
    *.md|*.markdown) ;;
    *) relevant=$((relevant + 1)) ;;
  esac
done < "$paths"
if [[ "$changed" == 0 ]]; then emit false NO_CHANGES 0 0
elif [[ "$relevant" == 0 ]]; then emit false MARKDOWN_ONLY "$changed" 0
else emit true ELIGIBLE "$changed" "$relevant"
fi
