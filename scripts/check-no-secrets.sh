#!/usr/bin/env bash
set -euo pipefail

# Lightweight pre-commit guard, not a replacement for GitHub secret scanning.
fail=0
if git diff --cached --name-only --diff-filter=ACMR | grep -Eq '(^|/)\.env($|\.)'; then
  if git diff --cached --name-only --diff-filter=ACMR | grep -Eq '(^|/)\.env($|\.)' && \
     git diff --cached --name-only --diff-filter=ACMR | grep -E '(^|/)\.env($|\.)' | grep -vq '^\.env.example$'; then
    echo 'Refusing to commit a local .env file.' >&2
    fail=1
  fi
fi
if git diff --cached --name-only --diff-filter=ACMR | grep -Eq '^(target|\.idea|\.vscode)/|\.(pem|key|p12|jks|gguf)$'; then
  echo 'Refusing to commit local configuration, build output, or model weights.' >&2
  fail=1
fi
if git diff --cached -U0 -- . ':!scripts/check-no-secrets.sh' | grep -E '^\+' | \
   grep -Eq '(sk-proj-[A-Za-z0-9_-]{20,}|sk-[A-Za-z0-9]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)'; then
  echo 'Possible credential in staged additions. Inspect the diff before committing.' >&2
  fail=1
fi
if ! git diff --cached --check; then
  fail=1
fi
exit "$fail"
