# ADR-0031: The manual is the Markdown GitHub shows, and a release carries no PDF of it

**Status:** Accepted
**Date:** 2026-10-11

## Context

On 1 October 2026 the manual was given two forms (ADR-0024, alternatives): the Markdown in
`docs/manual/` as GitHub shows it, and a PDF attached to each release. 2.0.0 and 2.1.0 carry one,
`restest-manual-2.0.0.pdf` and `restest-manual-2.1.0.pdf`, and it is the only file either release
carries.

The PDF had a cost on every change of the manual and at every release:

- **Three files to keep working**: `docs/manual/pdf.sh`, which ran pandoc with LaTeX and the
  Eisvogel template in a container pinned by its digest; a Lua filter that rewrote the manual's
  links, written for GitHub, into links inside one document or to GitHub at the right commit; and
  the PDF's metadata.
- **A workflow**, `.github/workflows/manual.yml`, that built the PDF on every change of the manual,
  so that the script was known to work before a release needed it.
- **A step by hand at every release**: build the PDF from the tag and attach it.
- **A second rendering to check.** A chapter could read well on GitHub and badly in the PDF, and the
  manual could use nothing pandoc does not render — no Mermaid diagram, which GitHub draws.

Six other tools that test REST APIs from an OpenAPI document were read on 11 October 2026 for how
they document and release. Schemathesis and CATS publish a documentation site, built with MkDocs and
Docusaurus; EvoMaster, RESTler, WuppieFuzz and Tcases keep their guides as pages in their
repositories. None of them attaches a PDF of its guide to a release. EvoMaster's repository holds
PDFs, but of its papers. CATS's site follows its unreleased `master`, so it describes features no
release has yet.

## Decision

**The manual is the Markdown in `docs/manual/`, and nothing else.** No PDF is built, in a workflow
or by hand, and a release carries none.

1. **A release's manual is that folder at the release's tag.** GitHub shows it when the tag is
   chosen, and the release notes link to it there, as they link the README and the reference pages.
2. **`docs/manual/pdf.sh`, its folder `docs/manual/pdf/` and `.github/workflows/manual.yml` are
   removed.** The last commit that has them is `285117d7`, should a PDF be wanted again.
3. **A documentation site, if one is built, reads the same files.** The manual is already written
   for that: plain GitHub-flavoured Markdown, one chapter per file, relative links.

## Consequences

- One step less at every release, one workflow less in CI, and no container image to pin and bump
  for documentation.
- The manual has one rendering, the one its readers see on GitHub, and may use what GitHub renders,
  Mermaid diagrams included.
- The PDFs attached to 2.0.0 and 2.1.0 stay where they are; no later release has one. A reader who
  wants the manual offline has it in the release's source archive, or in a clone at the tag.
- There is no longer one file to print or to hand to somebody without a browser.

## Alternatives considered

- **Keep the PDF, built and attached by hand.** What 2.0.0 and 2.1.0 did. It costs a step at every
  release and the three files above, for a form none of the comparable tools offers.
- **Build the PDF in a release workflow and attach it on every tag.** It removes the step by hand,
  but keeps the script, the filter, the pinned image and the second rendering to check, and RESTest
  has no release workflow yet to put it in.
- **One HTML page instead of a PDF.** The same pipeline with a different output, and a documentation
  site would make it redundant.
