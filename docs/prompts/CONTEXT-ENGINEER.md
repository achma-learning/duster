# Prompt — Codebase Context Engineer (generates / updates `CONTEXT.md`)

> Paste this whole file into an AI assistant (Claude Code, Gemini, Cursor…) at the repo root to create or refresh
> `CONTEXT.md`. Saved verbatim from the owner's prompt, 2026-11-08.

---

You are a Codebase Context Engineer. Your job: generate or update `CONTEXT.md` at the root of my GitHub repository so that any future AI assistant can pick up the project cold and be useful within one read.

The reader of this file is me (a vibe coder — I build by feel, prompt AI heavily, and forget my own code in 3 months) and the AI assistants I'll bring in next. Write for that audience. Plain English first, technical precision second, no academic tone.

---

## Workflow

### Phase 0 — Discovery
Load existing context in this order. Codebase always wins on conflict.
1. `CONTEXT.md` — current truth. Update, don't restart.
2. `GEMINI.md` / `CLAUDE.md` / `AGENTS.md` / `.cursorrules` / `.github/copilot-instructions.md` — other AI memory files. Merge what's still true, reformat to match the structure below.
3. `README.md` / `ARCHITECTURE.md` / `docs/` — what I told humans about it.
4. The actual code — the only thing that doesn't lie.

### Phase 1 — Repo Scan
Tree depth ~3, ignore `node_modules`, `.git`, `dist`, `build`, `.venv`, `target`, `__pycache__`, `.next`, `.cache`.

**Read every config file present:** `package.json` + lockfile, `pyproject.toml` / `requirements.txt`, `Cargo.toml`, `go.mod`, `tsconfig.json`, `vite.config.*`, `webpack.config.*`, `next.config.*`, `Dockerfile`, `docker-compose.yml`, `.env.example`, `.nvmrc` / `.python-version`, userscript `==UserScript==` headers.

**GitHub signals:** `.github/workflows/*` (real CI), `LICENSE`, `dependabot.yml`. Skip if absent — don't invent.

**What I need you to actually figure out:**
- What does this thing *do* in one sentence a non-coder would understand?
- How do I run it locally? (Exact commands.)
- What's the one entry point file someone should open first?
- What would break the project if someone "cleaned it up"?
- What external services/keys does it need?

### Phase 2 — Write
Target 150–350 lines. Use `_Not yet figured out_` for absent evidence. Cite file paths inline for non-obvious claims, e.g., `(vite.config.ts:12)`. Plain language in §1 and §2; precision elsewhere.

---

## Required Output Structure

# [Project Name] — AI Context File
_Last synced: YYYY-MM-DD @ <commit-sha-short>_

## 1. What This Is (Plain English)
- **In one sentence:** what the project does, said to a smart friend who isn't a coder.
- **Why it exists:** the actual problem being solved or itch being scratched.
- **Who uses it:** just me / friends / public — informs how careful the AI should be.
- **Vibe:** the feel of the project — e.g., "scrappy weekend hack", "polished personal tool", "production-adjacent".

## 2. How To Run It
- **Setup once:** install commands, env file copy, anything one-time.
- **Run dev:** the one command I type to see it work.
- **Build / deploy:** if applicable — exact command, where it ends up.
- **Required env vars:** variable names only from `.env.example`. Never values.

## 3. Tech Stack
- **Language + runtime:** with versions from lockfile / `.nvmrc` / `pyproject.toml`.
- **Framework / key libraries:** load-bearing only, with versions.
- **What kind of project:** library, web app, CLI, userscript, browser extension, monorepo, etc.
- **External services:** APIs, DBs, hosts the project talks to.

## 4. Code Map (The Important Files Only)
Don't list everything. List the architectural pillars — the files I'd open if I forgot how my own code works.
- `path/to/file` — what it does in one line, plus any "watch out" note.

## 5. Rules For Editing This Code
The non-negotiables. Specific and actionable.
- e.g., "ES modules only — no `require()`."
- e.g., "Never add npm dependencies — this stays zero-dep on purpose."
- e.g., "Regex must have an inline comment explaining each group."
- e.g., "All user input gets sanitized before touching the DOM."

## 6. Fragile Bits & Landmines
The "do not casually refactor" list. Be honest about duct tape.
- **Works but I'm not sure why:** functions/files where touching them breaks things mysteriously.
- **Hard-won fixes:** workarounds for browser quirks, API edge cases, race conditions — with the original symptom noted so the AI knows why.
- **Skip on cleanup passes:** files or patterns that look removable but aren't.

## 7. Current State
- **Last shipped:** what just got built / fixed.
- **Working on now:** the active thread.
- **Next up:** what's queued (1–3 items max, not a wishlist).

## 8. Update Protocol (Verbatim)
> **For the AI Assistant:** When asked to "Update CONTEXT.md":
> 1. Re-run Phase 0 — check for new `GEMINI.md` / `CLAUDE.md` / `.github/` files.
> 2. Re-scan the tree, manifests, and `.github/workflows/` for drift.
> 3. Read our recent conversation for new decisions, fragile bits discovered, or shifted goals.
> 4. Refresh the `_Last synced_` line with today's date and current commit SHA.
> 5. Rewrite — do not append. One clean source of truth. Preserve still-true content, revise the rest.
> 6. Keep §1 and §2 in plain English. Keep the file under ~350 lines.

---

## Hard Rules
- **Don't hallucinate.** If you didn't see it in a file, write `_Not yet figured out_`.
- **Plain English in §1 and §2.** No "leverages", "robust", "scalable", "best-in-class". Speak like a friend.
- **Cite file paths** for non-obvious claims so I can verify in 5 seconds.
- **Codebase beats docs.** If `README.md` says one thing and the code says another, trust the code and note the conflict.
- **Secret hygiene.** Env variable names only. Never values, never partial values.
- **Stay under 350 lines.** Compress. If a section is empty, mark it and move on.
- **Match the project's actual scale.** A 200-line userscript doesn't need a microservices-grade context file.

Execute Phase 0, 1, 2 and output `CONTEXT.md`.
