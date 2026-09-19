---
name: changelog
description: Add a VERSION entry to the PortfolioBoss changelog in AppMetadata.java. Derives the changes from git — the tracked diff AND new untracked files — and writes them in the established bullet-point style. Explicit command only; never part of the end-of-session updates.
disable-model-invocation: true
allowed-tools: Bash(git diff *), Bash(git log *), Bash(git status *), Bash(git ls-files *), Read, Edit
---

# PortfolioBoss Changelog Skill

Add a new VERSION entry to the changelog comment block in
`src/main/java/portfolioboss/AppMetadata.java`, and keep the `VERSION` constant in step with it.

**Explicit command only.** Run this when the user types `/changelog`. It is deliberately *not* part
of "עדכונים של סוף סשן": that ritual never touches git, and this skill has to read it.

---

## Changelog location

The changelog lives below the class in `AppMetadata.java`, inside a block comment:

```java
/*
 * Changelog:
 * VERSION X.Y.Z: [short title]
 * <bullet>
 * <bullet>
 * ...
 *
 * VERSION X.Y.(Z-1): [short title]
 * ...
 */
```

The new entry goes **at the top**, immediately after the ` * Changelog:` line, above the previous
latest version.

---

## Process

1. Read `AppMetadata.java` to get the current `VERSION` constant and the newest changelog entry.

2. Collect what changed — **tracked and untracked**. `git diff` never shows a file that git does not
   track yet, so every new class, and the whole of a new directory like `ui/`, would be missed.
   - `git status --short` — the overview (` M` modified, `A ` staged new, `??` untracked). An
     untracked directory shows up collapsed as `?? ui/`.
   - `git diff HEAD --stat`, then `git diff HEAD` on the source files — tracked changes, staged and
     unstaged together.
   - `git ls-files --others --exclude-standard` — every untracked file that is not gitignored, one
     per line (so a collapsed directory is expanded). Ignored build output such as `out/`,
     `ui/node_modules/`, `ui/dist/` and logs is already excluded; never list it.
   - **Read every untracked source file** (Java, TypeScript/TSX, scripts, config) — new features live
     there. For a whole new directory, read its entry points and main components, not lockfiles or
     generated files.
   - If the working tree is clean, the work is already committed: use `git log --oneline` to find the
     commits since the newest entry, and `git diff <first-commit>^..HEAD` over them.

3. Decide the version:
   - `$ARGUMENTS` given → use it.
   - Otherwise, if `VERSION` is *ahead of* the newest entry (bumped by hand) → use `VERSION`.
   - Otherwise (the newest entry already matches `VERSION`) → pick the next one. While the major
     version is 0: a new capability bumps the minor (0.2.0 → 0.3.0), a fix or tweak bumps the patch
     (0.2.0 → 0.2.1). Tell the user which you chose and why.

4. Draft the entry following the style rules below.

5. Edit `AppMetadata.java` with the Edit tool:
   - Replace the line
     ```
      * Changelog:
     ```
     with
     ```
      * Changelog:
      * VERSION <X.Y.Z>: [<short title>]
      * <bullet 1>
      * <bullet 2>
      * ...
      *
     ```
   - If the new version differs from the `VERSION` constant, set the constant to it.

6. Reply with the version, the title and the bullets you added, and whether you bumped `VERSION`.

---

## What counts

- **Include:** new or changed classes, records and endpoints; API payload fields; UI components and
  behavior; `run.sh` / launcher / config behavior; bug fixes; changes to the printed output.
- **Ignore:** import reorganizations, whitespace, renames with no effect, doc-only files (`CLAUDE.md`,
  `README.md`, `TODO.md`), `.claude/` tooling, `package-lock.json`, and the changelog block itself.

---

## Style rules

- Header: `VERSION X.Y.Z: [short title]` — a few words naming the theme of the release. No date.
- Each bullet is one line starting with ` * ` (space-asterisk-space).
- No sub-bullets. No markdown. No empty lines between bullets.
- One blank ` *` line separates this entry from the previous version.
- Each bullet describes **one cohesive change**: what was added/changed/fixed and *why* or *what it
  enables*, in plain language.
- Lead with the class/file/feature name, then a colon or em-dash, then the description.
  Example: `PortfolioSnapshot added: account, timestamp, net liquidation, cash and holdings of one IB download.`
- Behavior changes: state the old behavior and the new behavior.
  Example: `Main no longer exits after printing: it serves the snapshot until Ctrl+C.`
- Group related sub-points into one bullet when possible. Avoid one bullet per field.
- Do **not** mention refactors that have no observable effect.
- Keep each bullet under ~200 characters.
- Never write `*/` inside the entry — it would close the comment.

---

## Rules

- Git is **read-only** here: `status`, `diff`, `log`, `ls-files`. Never stage, commit, reset or stash.
  Committing belongs to the `commit` skill, and only when the user asks.
- The only file you edit is `AppMetadata.java`.

---

## Arguments

If `$ARGUMENTS` is provided, treat it as the version number to use (e.g. `/changelog 0.3.0`).
Otherwise decide the version as described in step 3.
