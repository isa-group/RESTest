---
description: Start the next increment from the roadmap (or the one given as an argument)
---

Start work on an increment of RESTest 2.0.

Argument (optional): the increment number, e.g. `1.2`. If none is given, take the next unstarted
increment from `ROADMAP.md` in order.

Current branch and status:

!`git branch --show-current && git status --short`

Increments already merged into `v2`:

!`git log v2 --oneline -15 2>/dev/null || echo "(no v2 branch yet)"`

Follow these steps and do not skip any:

1. **Read the plan.** `ROADMAP.md` for this increment's row, `CLAUDE.md` for the rules, and any ADR
   in `docs/adr/` that governs what you are about to touch. State in one sentence what this increment
   must make possible.

2. **Check the scope.** If the increment turns out to be larger than one reviewable pull request,
   stop and propose a split before writing any code. Do not silently deliver half of it.

3. **Enter plan mode** and produce a plan: the files you will create or change, the interfaces you
   will introduce, the tests you will write, and anything you are deliberately leaving for later.
   Wait for approval before editing.

4. **Create the branch**: `git switch -c feat/m<milestone>-<n>-<slug>` from an up-to-date `v2`.

5. **Implement**, smallest demonstrable change first. Write the test alongside the code. Do not weaken
   an existing test to make a new one pass.

6. **Verify**: `./mvnw verify`, plus the architecture tests. If the increment touches oracles or
   constraints, run the mutation tests for that module too.

7. **Review**: run the `reviewer` subagent over the diff and act on its findings. If you disagree with
   a finding, say why in the pull request's "Decisions taken" section rather than ignoring it.

8. **Write the pull request** with `/pr`.

9. **Stop** if this increment is marked 🛑 in `ROADMAP.md`. Those are supervision points: report what
   was done and wait, rather than starting the next increment.
