# Autonomous GitHub Repository Maintainer Powered by Google Jules

## 1. Overview & Architecture

This repository is maintained autonomously by **Google Jules**, an advanced AI coding agent developed by Google. The integration connects GitHub Actions with the official **Google Jules REST API (`v1alpha`)** to provide two independent, production-grade capabilities:

1. **Scheduled & On-Demand Repository Audit (`.github/workflows/jules-audit.yml`):**
   Periodically scans the codebase, detects genuine architectural, testing, security, or bug issues, implements targeted improvements, runs `./mvnw test` locally, and opens a structured Pull Request.
2. **Automatic CI Failure Investigation & Repair (`.github/workflows/jules-fix-ci.yml`):**
   Listens for failed runs of the primary CI pipeline (`Backend CI Pipeline`) on the default branch (`main`). It extracts failure job metadata and sanitized logs, creates a Jules repair session, and prompts Jules to fix the underlying code or test issue without human intervention.

```
+-----------------------------------------------------------------------------------+
|                              GitHub Repository                                    |
|                                                                                   |
|  +-------------------------+               +-----------------------------------+  |
|  |     jules-audit.yml     |               |         jules-fix-ci.yml          |  |
|  |  (Cron / Manual Trigger)|               |  (workflow_run on CI failure)     |  |
|  +------------+------------+               +-----------------+-----------------+  |
|               |                                              |                    |
|               v                                              v                    |
|  +-----------------------------------------------------------------------------+  |
|  |                 Helper Client (.github/scripts/jules-client.mjs)            |  |
|  |  - Discovers Jules source identifier via Sources API                        |  |
|  |  - Prevents duplicate sessions & recursive execution loops                  |  |
|  |  - Redacts sensitive credentials (keys, tokens, passwords)                  |  |
|  |  - Bounds and formats diagnostic logs                                       |  |
|  +-------------------------------------+---------------------------------------+  |
+----------------------------------------|------------------------------------------+
                                         | REST API (X-Goog-Api-Key)
                                         v
+-----------------------------------------------------------------------------------+
|                         Google Jules Cloud Platform                               |
|                                                                                   |
|  1. GET  /v1alpha/sources           -> Validate repository connection             |
|  2. GET  /v1alpha/sessions          -> Deduplicate active & recent tasks          |
|  3. POST /v1alpha/sessions          -> Launch coding session (AUTO_CREATE_PR)     |
|  4. Jules Agent Environment         -> Clone repo, reproduce, fix, run tests      |
|  5. Pull Request Created            -> Ready for human code review                |
+-----------------------------------------------------------------------------------+
```

---

## 2. Prerequisites & Setup

### 2.1. Obtain a Google Jules API Key
1. Go to the [Google Jules Console](https://jules.google).
2. Navigate to **Settings** > **API Keys**.
3. Generate a new API key.
4. Copy the key (starts with `AIza...`).

### 2.2. Connect the Repository to Google Jules
1. In the [Google Jules Console](https://jules.google), navigate to **Sources** or **Repositories**.
2. Click **Connect Repository** and choose **GitHub**.
3. Grant access to the GitHub organization/account: `Cruise-JavaCLass/Cruise-api`.
4. Ensure the repository appears in your connected sources list.

### 2.3. Discovering the Connected Source Identifier
You do not need to guess source identifiers. The integration automatically queries the Jules Sources API:
```bash
curl -H "X-Goog-Api-Key: $JULES_API_KEY" \
  "https://jules.googleapis.com/v1alpha/sources"
```
The client matches the source where `githubRepo.owner == "Cruise-JavaCLass"` and `githubRepo.repo == "Cruise-api"`, returning the exact identifier (e.g. `sources/github-Cruise-JavaCLass-Cruise-api`).

### 2.4. Configure GitHub Secrets
Navigate to **Settings** > **Secrets and variables** > **Actions** in your GitHub repository and add:

| Secret Name | Description | Required By |
| :--- | :--- | :--- |
| `JULES_API_KEY` | Google Jules REST API key (`AIza...`) | Both workflows |
| `SONAR_TOKEN` | *(Existing)* SonarCloud analysis token | Log redaction mask |

> [!NOTE]
> `GITHUB_TOKEN` is automatically provided by GitHub Actions and does not need to be manually created.

---

## 3. Capability 1: Scheduled Repository Audit (`jules-audit.yml`)

### How It Works
- **Schedule:** Runs every Monday at **03:00 UTC** (`cron: '0 3 * * 1'`).
- **Target Branch:** Automatically uses the repository's default branch (`main`).
- **Deduplication:** Checks existing sessions via `GET /v1alpha/sessions`. If an audit session was already triggered in the last 24 hours, it safely skips to avoid spamming pull requests.
- **Single Task Constraint:** The prompt instructs Jules to select **exactly ONE** high-value, well-scoped task per audit session.
- **Verification:** Instructs Jules to run `./mvnw test` or `./mvnw -B verify --file pom.xml` to verify compilation and test passes before opening a Pull Request.

### Manual Execution (`workflow_dispatch`)
You can trigger an audit on demand with a specific scope:
1. Go to the **Actions** tab in GitHub.
2. Select **Jules Repository Audit**.
3. Click **Run workflow**.
4. Select the **Audit Scope**:
   - `full` *(default)*: Comprehensive review balancing bugs, tests, security, and quality.
   - `bugs`: Focus on edge cases, NPEs, unhandled exceptions, and concurrency issues.
   - `tests`: Identify untested service methods and missing boundary tests.
   - `security`: Inspect input validation, token handling, and dependency vulnerabilities.
   - `code-quality`: Refactor high complexity, duplicate code, or SonarCloud code smells.
   - `documentation`: Check consistency between documentation and Spring Boot REST endpoints.
5. *(Optional)* Check **Bypass deduplication check** (`force_audit: true`) if you need to run an audit even if one occurred recently.

---

## 4. Capability 2: Automatic CI Failure Investigation & Repair (`jules-fix-ci.yml`)

### How It Works
- **Trigger Event:** `workflow_run` on `Backend CI Pipeline` with `types: [completed]`.
- **Filtering Rules:**
  - **Conclusion:** Only triggers if `conclusion == 'failure'`. Ignores `success`, `cancelled`, `skipped`, and `neutral`.
  - **Branch Security:** Only triggers if the failed run was on the repository's default branch (`main`). It will **never** automatically run with elevated privileges on untrusted forks or unreviewed pull requests.
  - **Loop Prevention:** Ignores runs from maintainer workflows (`Jules Repository Audit`, `Jules CI Failure Repair`) to prevent infinite recursion.
- **Deduplication:** Checks if a repair session already exists for the specific `run_id` or `commit_sha`.

### Diagnostic Log Extraction & Sanitization
When a failure occurs, the runner script:
1. Queries GitHub Actions REST API to find the failed jobs and failed steps.
2. Downloads the job log for each failed job.
3. Passes logs through `redactSensitiveData()`:
   - Masks Google API keys (`AIza...`).
   - Masks GitHub tokens (`ghp_...`, `gho_...`, `github_pat_...`).
   - Masks Bearer and Basic authentication headers.
   - Masks environment secrets, passwords, and custom tokens (`SONAR_TOKEN`, `JULES_API_KEY`).
4. Bounds logs with `truncateLog()` (keeping the head and the error tail up to 250 lines / 25 KB).
5. Transmits the sanitized diagnostic summary in the prompt to Jules.

### Infrastructure vs Code Failure Decision
The prompt explicitly instructs Jules:
- If the failure is caused by an external service outage, missing secret (e.g. `SONAR_TOKEN`), or runner environment issue, **do not make code changes**; report the diagnosis in the session output.
- If the failure is a Java compilation error, broken test assertion, or business logic bug, implement a minimal fix, add a regression test, run `./mvnw test`, and submit a Pull Request.

---

## 5. Security & Safety Policies

1. **No Direct Pushes to Main:** Jules operates strictly in `automationMode: AUTO_CREATE_PR`. It creates isolated feature branches (`jules/...`) and opens a Pull Request.
2. **Never Auto-Merge:** All Jules PRs require human review, status checks pass, and standard branch protection compliance before merging.
3. **Branch Protection Remains Untouched:** The workflows have zero administrative permissions to modify GitHub branch rules, secrets, or repository settings.
4. **Minimal Permissions:**
   - **jules-fix-ci.yml:** `contents: read`, `actions: read` (reads failed job logs; PR creation is handled through connected Jules GitHub App).
   - **jules-audit.yml:** `contents: read`.
   - Both workflows configure `persist-credentials: false` on repository checkout.
5. **Prompt Injection Defense:** Commit metadata, job names, failed steps, and workflow logs are treated as untrusted data within strict prompt trust boundaries. Prompts isolate external values into explicit untrusted blocks with escaped delimiters and instruct Jules to ignore any instructions found within that data.

---

## 6. Monitoring & Reviewing Jules Sessions

### In GitHub Actions
Each workflow run produces a structured **GitHub Actions Step Summary** containing:
- Jules Session ID and State (`QUEUED`, `IN_PROGRESS`, `COMPLETED`).
- Direct clickable link to the session in the Google Jules Console.
- Audit scope or failed CI run details.

### In the Jules Web Console
Click the link from the Step Summary (or navigate to [jules.google](https://jules.google)) to:
- View real-time thought processes and bash commands executed by Jules.
- Review proposed diffs.
- Track generated Pull Requests.

---

## 7. Troubleshooting & Failure Modes

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| `Jules API Authentication failed (HTTP 401)` | Invalid or missing `JULES_API_KEY` | Check GitHub Secret `JULES_API_KEY` in repository settings. |
| `No connected Jules source matches repository` | Repository not linked in Jules | Go to [jules.google](https://jules.google) and connect `Cruise-JavaCLass/Cruise-api`. |
| `Skipping duplicate audit session` | Audit already ran in the last 24h | Use `workflow_dispatch` with `force_audit: true` to bypass. |
| `Ignored branch: feature/...` | CI failed on a feature/PR branch | Automated CI repair only processes `main` to protect security. Use manual dispatch with `run_id` if needed. |
| Jules PR fails Maven build | Jules patch didn't account for all tests | Review the PR branch, add comments, or reject the PR. |

---

## 8. Disabling the Automations

- **To temporarily pause the weekly audit:**
  Go to **Actions** > **Jules Repository Audit** > Click `...` > **Disable workflow**.
- **To temporarily pause CI failure repair:**
  Go to **Actions** > **Jules CI Failure Repair** > Click `...` > **Disable workflow**.
- **To completely remove:** Delete `.github/workflows/jules-audit.yml` and `.github/workflows/jules-fix-ci.yml`.

---

## 9. Human Responsibilities

While Jules is autonomous, human engineers remain the decision-makers:
- **Reviewing PRs:** Always review the diff and verify that code changes align with project standards.
- **Secret Management:** Rotating and provisioning credentials (`JULES_API_KEY`, `SONAR_TOKEN`).
- **Merging:** Reviewing CI status and clicking Merge when satisfied.
