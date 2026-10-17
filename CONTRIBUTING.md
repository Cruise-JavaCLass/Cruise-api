# Contributing to Cruise Activity & Service Management System (Backend API)

Thank you for contributing! All team members and AI coding assistants must adhere to the engineering standards described here.

---

## 1. Jira Ticket & Subtask Requirements

- **Jira Instance:** `https://huytran20052022.atlassian.net/` | Project Key: `KAN`
- Every functional change must map to a Jira Story/Task.
- Complex stories should have subtasks covering:
  1. Implementation
  2. Unit Tests
  3. Integration Tests
  4. Documentation & Validation
- Jira issues must only be moved to `Done` after PR approval, passing CI, and merge to `main`.

---

## 2. Git Workflow & Branching

- We use **GitHub Flow**.
- Always branch off the latest `main`:
  ```bash
  git checkout main
  git pull origin main
  git checkout -b feat/KAN-xxx-feature-name
  ```
- Allowed branch prefixes:
  - `feat/KAN-xxx-...` (Feature)
  - `fix/KAN-xxx-...` (Bug fix)
  - `test/KAN-xxx-...` (Testing)
  - `docs/KAN-xxx-...` (Documentation)
  - `refactor/KAN-xxx-...` (Refactoring)
  - `chore/KAN-xxx-...` (CI/Build/Dependencies)
- **Direct push to `main` is strictly forbidden.**

---

## 3. Commit Guidelines

- Commit message structure:
  ```
  [KAN-xxx] <type>(<scope>): <imperative summary>
  ```
- Use Conventional Commit types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `perf`.
- Do not commit sensitive credentials, keys, or build artifacts (`target/`, `.class`).

---

## 4. Pull Request Requirements

- PR Title format: `[KAN-xxx] type: concise description`
- Fill out all required sections in `.github/PULL_REQUEST_TEMPLATE.md`:
  - `### Summary`
  - `### Changes`
  - `### Testing`
  - `### Jira`
  - `### Notes`
- Automated GitHub Actions validate the title and body format on every PR.
- Required checks (Build & Test, SonarCloud) must be green.

---

## 5. Local Build & Test Verification

Before submitting code, run:
```powershell
./mvnw clean verify -DskipTests=false
```
All unit tests and integration tests must pass 100%.
