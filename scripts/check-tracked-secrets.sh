#!/usr/bin/env bash
set -euo pipefail

# These paths deliberately contain only local/test credentials or non-usable examples:
# compose.yaml, src/main/resources/application.properties, .env*.example, src/test/**.
# Keep any new exception path-specific and document it in docs/production-deployment.md.
allowed_paths=(
  'compose.yaml'
  'src/main/resources/application.properties'
  '.env.oidc.example'
  '.env.production.example'
  'src/test/'
)

# Limit the signal to tracked configuration and documentation formats. Source code may legitimately
# use words such as "token" without representing a configured credential.
matches=$(git grep -nEI '(password|secret|token)[[:alnum:]_.-]*[[:space:]]*[:=][[:space:]]*[^[:space:]#]+' -- \
  '*.properties' '*.yaml' '*.yml' '.env*' '*.md' || true)
if [[ -z "$matches" ]]; then
  exit 0
fi

unexpected=()
while IFS= read -r match; do
  path=${match%%:*}
  allowed=false
  for allowed_path in "${allowed_paths[@]}"; do
    if [[ "$path" == "$allowed_path" || "$path" == "$allowed_path"* ]]; then
      allowed=true
      break
    fi
  done
  if [[ "$allowed" == false ]]; then
    unexpected+=("$match")
  fi
done <<< "$matches"

if ((${#unexpected[@]})); then
  printf '%s\n' "Credential-like values found outside the documented path allowlist:" >&2
  printf '%s\n' "${unexpected[@]}" >&2
  exit 1
fi

printf '%s\n' "Credential-pattern check passed; documented local/test/example matches only."
