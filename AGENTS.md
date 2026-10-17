# AI Coding Agent & Contributor Directives (AGENTS.md)

This repository (`Cruise-api`) contains the Spring Boot microservices backend for the **Cruise Activity and Service Management System**.
All AI coding agents (Claude, Copilot, Jules, Gemini, Antigravity, etc.) and human developers working in this repository must strictly adhere to the policies established in this document.

---

## 1. Jira Project & Issue Key Rules

- **Jira Project Key:** `KAN` (Domain: `https://huytran20052022.atlassian.net/`)
- **Hierarchy:**
  - **Epic:** Major capability (e.g., Shore Excursion Management, POS Cashless System).
  - **Story / Task:** Deliverable functional unit (e.g., `KAN-185`: Implement Excursion Status Tracking).
  - **Subtask:** Specific, actionable implementation or test step (e.g., `KAN-187`: Transitions, `KAN-190`: Unit tests).
- **Rule of Record:** Every feature branch, commit message, and PR title MUST explicitly reference a valid Jira issue key (`KAN-xxx`).
- **Do Not Fabricate Keys:** Always verify the Jira issue key from the active user prompt or Jira API.
- **Completion Guarantee:** Merely pushing code or opening a PR does NOT mark a Jira task Done. An issue is only eligible for `Done` once CI passes, code review completes without blocking comments, and the PR is merged to `main`.

---

## 2. Branching Strategy (GitHub Flow)

- **Default & Protected Branch:** `main` (Strictly protected; NO direct pushes allowed).
- **Branch Naming Standard:**
  - `feat/KAN-xxx-short-description` (New features / business logic)
  - `fix/KAN-xxx-short-description` (Bug fixes / error corrections)
  - `test/KAN-xxx-short-description` (Test suite additions / mock repairs)
  - `docs/KAN-xxx-short-description` (Documentation updates)
  - `refactor/KAN-xxx-short-description` (Code restructuring without behavior changes)
  - `chore/KAN-xxx-short-description` (Build, dependency, or CI/CD workflow updates)
- **Scope:**
  - Develop on one feature branch per parent story/deliverable. Related subtasks share the parent branch.
  - Never stage or commit unrelated changes.
  - Verify `git status` and working tree before creating commits.

---

## 3. Commit Message Conventions

Follow Conventional Commits with Jira issue reference:

```
[<JIRA-KEY>] <type>(<scope>): <concise description in imperative mood>

[optional body explaining rationale]
```

### Commit Types:
- `feat`: New feature or endpoint.
- `fix`: Bug fix in logic or error handling.
- `test`: Unit or integration tests.
- `refactor`: Code improvements that neither fix bugs nor add features.
- `docs`: Documentation, README, or OpenAPI changes.
- `chore`: Maven pom, Docker, or GitHub Actions changes.

### Commit Rules:
1. Reference the Jira issue key in every commit (e.g., `[KAN-185] feat: ...`).
2. Keep commits atomic, cohesive, and free of extraneous or generated files (`.class`, `target/`, IDE configs).
3. Never force-push (`git push --force`) to shared branches.

---

## 4. Pull Request Standards & Title Format

- **PR Title Format:**
  ```
  [KAN-xxx] type: concise description
  ```
  *Example:* `[KAN-185] feat: implement excursion status tracking and transition validation`
- **PR Description:** Must follow the repository PR template (`.github/PULL_REQUEST_TEMPLATE.md`), containing:
  - Summary (1–3 sentences explaining the outcome).
  - Changes made (bulleted list).
  - Testing performed and verified evidence.
  - Jira ticket link (`https://huytran20052022.atlassian.net/browse/KAN-xxx`).
  - Risks and notes.
- **Validation:**
  - Never submit raw escape sequences (`\n\n`) in PR markdown.
  - Ensure all assertions, test names, and files changed match the actual git diff.

---

## 5. Testing & Code Quality Gates

- **Backend Stack:** Java 21/25 (LTS), Spring Boot 3.x, Maven, PostgreSQL, JaCoCo, SonarCloud.
- **Mandatory Local Verification:**
  Before pushing, execute the verification command locally:
  ```powershell
  ./mvnw clean verify -DskipTests=false
  ```
- **Testing Requirements:**
  - Unit tests for service layer business rules (`*UnitTest.java`).
  - Integration tests using MockMvc or Testcontainers for API endpoints (`*IntegrationTest.java`).
  - Never weaken assertions or delete tests simply to get a passing build.
  - SonarCloud Quality Gate must remain **PASSED** (0 security vulnerabilities, 0 bugs, minimal code smells).

---

## 6. AI-Assisted Remediation & Safety Guardrails

When an AI agent is invoked to triage or repair a build/CI failure:
1. **Analyze Failure Logs:** Inspect compiler errors, Surefire XML reports, or SonarCloud findings.
2. **Minimal Patch Principle:** Apply targeted, minimal fixes within the authorized task scope.
3. **No Secret Exposure:** Never log, commit, or print tokens, private keys, or passwords.
4. **No Destructive Operations:** Do not drop database tables, delete git history, or bypass tests without explicit user consent.
