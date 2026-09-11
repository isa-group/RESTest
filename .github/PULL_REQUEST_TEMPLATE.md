<!--
Title: say what became possible, not what was edited.
  Good: "Generate values that match declared formats"
  Bad:  "Add FormatAwareValueProvider"

Fill in every section. Delete nothing.
-->

**Increment:** <!-- e.g. 2.4, from ROADMAP.md -->

## What you can do now that you could not before

<!--
One or two sentences. Concrete and user-facing.

Good: "The tool now fills in dates, e-mails and identifiers in a format real APIs accept, so
operations that used to return 400 now return 200."

Bad: "Added the ValueProvider chain and the FormatAwareProvider."

If this increment is groundwork with nothing visible yet, say so plainly and name the increment
that will make it visible.
-->

## Try it yourself

<!--
Copy-paste commands from a clean checkout, with the real expected output underneath. They must work
on a laptop with only Java and git installed. Run them yourself first and paste the actual output.

    git fetch && git switch feat/m2-4-format-aware-values
    ./mvnw -q install -DskipTests
    ./restest run examples/petstore.yaml --url https://petstore3.swagger.io/api/v3 --budget 30s

    Expected:
      47 test cases, 3 failures, report written to ./restest-report/index.html
      The "Values by source" panel is new: 31 values came from format-aware generators.

If the change is not visible from the command line, say so and give the next best thing: a test to
run, a file to open, a number to compare.
-->

## How it works

<!--
Three short paragraphs at most, for a reader who does not program in Java. Explain the idea, not the
syntax. Define any unavoidable term inline or point at the glossary in docs/PROPOSAL.md section 0.
A small diagram is welcome when it replaces a paragraph.
-->

## What changed in the code

<!-- For the code reviewer: files, key classes, design decisions, anything left for later. -->

## Evidence

- [ ] Tests: <!-- N added, all green --> — <!-- CI run link -->
- [ ] Architecture tests: <!-- unchanged / N added --> — none weakened
- [ ] Mutation score (if oracles or constraints were touched): <!-- X% (previous Y%) -->
- [ ] Per-request overhead: <!-- X ms (previous Y ms) -->
- [ ] Smoke run, two containerised APIs: operations covered <!-- X (previous Y) -->, unique faults <!-- Z (previous W) -->
- [ ] Idle time: <!-- X% of budget -->
- [ ] Artefact: <!-- report file, terminal transcript, or benchmark table -->

## Decisions taken

<!--
Anything you chose that could reasonably have gone the other way, including any reviewer finding
you decided not to act on, and why. If a decision is expensive to reverse, it needs an ADR instead.
-->

## Open questions

<!-- Anything needed from the maintainer. "None" is a valid answer. -->

---

- [ ] Targets `v2`, not `main`
- [ ] English throughout, including comments and test names
- [ ] Nothing from the deferred backlog (`docs/PROPOSAL.md` §11)
- [ ] No AI abstraction; no benchmark-platform reference under `src/`
- [ ] Reviewed by the `reviewer` subagent
