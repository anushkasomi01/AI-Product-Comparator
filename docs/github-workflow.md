# GitHub setup and milestone commits

## Repository and branch reality

This work began in a checkout of `anushkasomi01/AI-Product-Comparator`. Arena fixes the working branch to:

```text
arena/3c24c2ea-ai-product-comparator
```

Do not rename or switch that branch during this session. The existing `origin` remains connected to the original repository. The requested new public repository is:

```text
anushkasomi01/ai-multimarketplace-laptop-finder
```

The connected GitHub credentials were verified as `anushkasomi01`. Both GraphQL and REST repository-creation attempts returned `Resource not accessible by integration` (HTTP 403 for REST). **The new repository has not been created by the agent.** GitHub connector availability was also checked and is unsupported in this session. This is a permission limitation, not a request for your password or token.

## Create the requested public repository securely

Preferred: create an empty public repository through GitHub's website, named exactly `ai-multimarketplace-laptop-finder`. Do not initialize it with a README, license, or `.gitignore`; the existing history already contains the project files.

Alternatively, in your own authenticated terminal:

```bash
gh auth login
gh auth status
gh repo create anushkasomi01/ai-multimarketplace-laptop-finder \
  --public \
  --description 'AI-powered laptop finder built with Java and Spring AI'
```

Use GitHub CLI's browser/device login or a credential manager. Never paste passwords, personal access tokens, or API keys into this conversation. If Arena's GitHub connection itself needs broader access, reconnect it through Arena's app settings rather than sharing a secret in chat.

From this existing checkout, add a second remote after creation:

```bash
git remote add laptop-finder https://github.com/anushkasomi01/ai-multimarketplace-laptop-finder.git
git remote -v
```

Do not run `git init` again in this checkout and do not replace `origin`. If `laptop-finder` already exists, inspect it with `git remote get-url laptop-finder` instead of blindly adding it again.

Because the session branch is fixed, its first push to an empty repository becomes that repository's initial branch:

```bash
git push -u laptop-finder arena/3c24c2ea-ai-product-comparator
git ls-remote --heads laptop-finder arena/3c24c2ea-ai-product-comparator
gh repo view anushkasomi01/ai-multimarketplace-laptop-finder \
  --json name,visibility,defaultBranchRef,url
```

You can later choose your normal `main` workflow outside this fixed Arena session. The commands here deliberately use the actual branch, not an assumed `main`.

## Phase 1 review, stage, commit, push

Run from the repository root:

```bash
./mvnw clean verify
git status --short --branch
git diff -- README.md
```

New files are untracked, so `git diff` alone does not show their contents. Stage only the intended files, then inspect the cached diff:

```bash
git add README.md .editorconfig .env.example .gitattributes .gitignore \
  pom.xml mvnw mvnw.cmd .mvn/wrapper/maven-wrapper.jar \
  .mvn/wrapper/maven-wrapper.properties \
  src/main/java src/main/resources \
  src/test/java src/test/resources \
  docs/architecture.md docs/github-workflow.md docs/limitations.md \
  docs/openapi.yaml docs/phase-1.md \
  scripts/check-no-secrets.sh \
  .github/workflows/ci.yml .github/workflows/live-model-smoke.yml

git diff --cached --stat
git diff --cached
git diff --cached --check
bash scripts/check-no-secrets.sh
git status --short
```

The Maven Wrapper JAR is an intentional, versioned build-bootstrap binary obtained from the upstream Spring AI repository at tag `v2.0.2`. It is not a build output or model file. All other JARs and local secrets remain excluded.

After reviewing and passing tests:

```bash
git commit -m "feat: add Spring AI chat foundation"

# Preserve the Arena repository connection:
git push origin arena/3c24c2ea-ai-product-comparator

# Also publish to the requested repository, once you have created it:
git push -u laptop-finder arena/3c24c2ea-ai-product-comparator
```

Verify, rather than assuming success:

```bash
git status --short --branch
git log -1 --oneline
git ls-remote --heads origin arena/3c24c2ea-ai-product-comparator
# After the second remote exists:
git ls-remote --heads laptop-finder arena/3c24c2ea-ai-product-comparator
gh run list --limit 5
```

The remote SHA should match `git rev-parse HEAD`. CI must independently report success; a push alone is not a passing build. The agent will report only operations actually executed and verified.

## Every later milestone

1. Define the acceptance criteria before implementation.
2. Implement a small vertical slice and update its tests and documentation.
3. Run `./mvnw clean verify`; run live tests only when relevant and configured.
4. Review status, unstaged and staged diffs, and the secret guard.
5. Stage explicit paths; never use `git add .` before inspection.
6. Commit with a Conventional Commit message.
7. Push the session branch to the configured remotes and verify their SHAs.
8. Check CI and record any remaining limitations.

Do not commit `.env`, credentials, private customer data, build output, IDE settings, model weights, or confidential documents. `.gitignore` is not a secret scanner: always inspect the staged diff. Enable GitHub secret scanning and push protection where available.
