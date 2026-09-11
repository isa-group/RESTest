---
description: Write and open the pull request for the current increment, in the house format
---

Open the pull request for the work on this branch.

Branch and diff against `v2`:

!`git branch --show-current && git diff v2...HEAD --stat`

Commits on this branch:

!`git log v2..HEAD --oneline`

Write the description using `.github/PULL_REQUEST_TEMPLATE.md`. Fill in **every** section. The
reviewer is a professor of software engineering who does not want to read Java to understand what
changed, and who will actually run the commands you give.

Section by section:

**What you can do now that you could not before.** One or two sentences, concrete and user-facing.
Right: "the tool now fills in dates, e-mails and identifiers in a format real APIs accept, so
operations that used to return 400 now return 200." Wrong: "added the ValueProvider chain and the
FormatAwareProvider." If the honest answer is "nothing yet, this is groundwork", say exactly that and
name the increment that will make it visible.

**Try it yourself.** Copy-paste commands from a clean checkout, with the expected output shown
underneath. They must work on a laptop with only Java and git installed. **Run them yourself first,
in a temporary clone, and paste the real output** — never output you expect. If the change is not
visible from the command line, say so and give the next best thing: a test to run, a file to open, a
number to compare.

**How it works.** Three short paragraphs at most, for a reader who does not program in Java. Explain
the idea, not the syntax. Define any unavoidable term inline, or point at the glossary in
the glossary in `docs/DESIGN.md`. A small ASCII or Mermaid diagram is welcome when it replaces a
paragraph.

**What changed in the code.** For the code reviewer: files, key classes, design decisions, anything
deliberately left for later.

**Evidence.** Real numbers, not placeholders. Tests added and the CI link; architecture and mutation
test status; per-request overhead; the smoke run's operations covered and unique faults with the
previous values for comparison; idle time.

**Decisions taken.** Anything you chose that could reasonably have gone the other way — including any
reviewer finding you decided not to act on, and why.

**Open questions.** Anything you need from the maintainer.

The title says what became possible, not what was edited: "Generate values that match declared
formats", not "Add FormatAwareValueProvider".

Then: commit anything outstanding, push the branch, and open the pull request with `gh pr create
--base v2`. Report the URL. Do not merge it.
