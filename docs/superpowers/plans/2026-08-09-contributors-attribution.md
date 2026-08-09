# Contributor Attribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add clear README attribution for GitHub maintainer `@yuhold`, Claude, and GPT, while ensuring future commits use the GitHub-linked author identity.

**Architecture:** Add one documentation-only section to `README.md` before the license section. Configure repository-local Git author metadata for future commits; do not rewrite history, change the Android app version, rebuild APKs, or create a release.

**Tech Stack:** Markdown, Git repository-local configuration, GitHub HTTPS links

## Global Constraints

- GitHub maintainer: `yuhold`.
- Git commit email: `yuhold.0757@qq.com`.
- Claude and GPT are acknowledgements, not synthetic GitHub contributors.
- Existing commit history must not be rewritten.
- Do not update the Android version or publish a new APK/Release.
- Preserve the non-commercial license and anti-resale notice.

---

### Task 1: Add contributor acknowledgements

**Files:**
- Modify: `README.md:103` (immediately before `## 许可`)

**Interfaces:**
- Consumes: Existing README section order and project attribution policy.
- Produces: A visible `## 贡献者与致谢` section with stable links and precise roles.

- [ ] **Step 1: Confirm the section does not already exist**

Run:

```bash
rg -n '^## 贡献者与致谢$|github.com/yuhold|anthropic.com/claude|openai.com' README.md
```

Expected: The repository link may appear, but no `贡献者与致谢` heading exists.

- [ ] **Step 2: Insert the attribution section**

Insert immediately before `## 许可`:

```markdown
## 贡献者与致谢

- [@yuhold](https://github.com/yuhold) — 作者、维护者与主要开发；Git 提交邮箱：`yuhold.0757@qq.com`。
- [Claude](https://www.anthropic.com/claude)（Anthropic）— 开发、代码审查与文档辅助。
- [GPT](https://openai.com/)（OpenAI）— 开发辅助。

> GitHub 的 Contributors 列表由实际提交记录自动生成；Claude 与 GPT 在此作为开发辅助工具致谢，不代表其为 GitHub 用户或项目著作权人。
```

- [ ] **Step 3: Verify wording, links, and license placement**

Run:

```bash
rg -n -A8 '^## 贡献者与致谢$' README.md
rg -n '^## 许可$' README.md
```

Expected:

- `@yuhold` links to `https://github.com/yuhold`.
- The email is exactly `yuhold.0757@qq.com`.
- Claude links to Anthropic and GPT links to OpenAI.
- The attribution section appears before `## 许可`.

- [ ] **Step 4: Configure repository-local Git identity**

Run:

```bash
git config user.name yuhold
git config user.email yuhold.0757@qq.com
git config --local --get user.name
git config --local --get user.email
```

Expected:

```text
yuhold
yuhold.0757@qq.com
```

- [ ] **Step 5: Validate the documentation patch**

Run:

```bash
git diff --check
git diff -- README.md
```

Expected: No whitespace errors; only the approved contributor section is added to README.

- [ ] **Step 6: Commit the attribution change**

```bash
git add README.md
git commit -m "Add contributor acknowledgements"
```

Expected: Commit author is `yuhold <yuhold.0757@qq.com>`.

- [ ] **Step 7: Verify commit identity and clean state**

Run:

```bash
git log -1 --format='%h %an <%ae> %s'
git status --short
```

Expected:

- Latest commit author is `yuhold <yuhold.0757@qq.com>`.
- Working tree is clean.

- [ ] **Step 8: Push the documentation commit**

Run:

```bash
git push origin main
```

Expected: `main` advances successfully. Do not create a tag or GitHub Release.
