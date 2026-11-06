# ADR-0047: The documentation is a website, built from a released version in a repository of its own

**Status:** Accepted
**Date:** 2026-11-05

## Context

RESTest's documentation is Markdown in this repository: the README, the manual, a page per subject,
the design and the decision records ([ADR-0031](0031-the-manual-is-markdown-only.md)). GitHub shows
each file well, but it offers no search across the pages and no menu, and what it shows by default
is `master`, which describes the next version rather than the one people install. There is no
address for the documentation as a whole that the README, the skill for coding agents or a paper
can give.

ADR-0031 §3 left room for a site: "A documentation site, if one is built, reads the same files."

## Decision

### 1. A site of its own, in `isa-group/restest-site`

The documentation is published as a website by the repository `isa-group/restest-site`, built with
Astro and its documentation theme, Starlight, and published by GitHub Pages at
`https://isa-group.github.io/restest-site/docs/`. Its search runs in the browser, with no service,
and `/llms.txt` lists the Markdown of every page in its menu, for coding agents. The same site is to hold a landing
page and a showcase.

RESTest's build knows nothing of the site: no Node, no npm, nothing more for a contributor to
install. The site's repository stays public while the organisation is on GitHub's free plan, under
which GitHub Pages publishes public repositories only.

### 2. It reads RESTest's files as they are, at a release's tag

Before each build the site takes RESTest at a version's tag and reads the Markdown under `docs/`,
`README.md`, `AGENTS.md` and `CONTRIBUTING.md`. Nothing is copied into
RESTest, and the pages are not written for the site: each page's first `#` heading becomes its
title, links to other pages lead to the site's pages, images are copied, and a link to any other file
leads to it on GitHub at that tag. The one thing here that names the site's parts is
`docs/README.md`: its sections are the site's groups (§6).

### 3. What it depends on

- **Held here by `DocumentationSiteTest`**, on every pull request:
  - `docs/manual/README.md` has a numbered list, each item starting with a link to a page, and
    every chapter of `docs/manual/` is in it;
  - `docs/README.md` has `##` sections, each with a table — a header, the line of dashes under it
    and at least one row — and each row's first cell links a page;
  - every page is an entry of one of the two indexes — a list item's first link, or a row's — but
    the two indexes, the README and the decision records, which have an index of their own; a link
    in an index's prose is no entry;
  - every page has a title, a line starting with `# `, and none starts with front matter.
- **Held here by `DocumentedLinksTest`**: every Markdown link, and the heading it names, leads
  somewhere; a page's own title is no such heading, since the site shows it above the page instead.
- **Checked only by the site's own build**, which names the file: links in raw HTML (`src`, `srcset`,
  `href`), reference-style links, links that leave the repository, a link to a heading the site
  names differently from GitHub, such as one with the same words as the page's title, and two files
  that would be published at one address, such as `AGENTS.md` and `docs/agents.md`, or two chapters
  of the manual whose names differ only by their number.
- **Also relied on:** the `<img>` of `RESTLogo_Black.png`, at the root, is left out by that file name,
  since the site's header shows the logo already; a `<picture>` with a source for a dark colour scheme
  follows the theme the reader picks; and whatever GitHub draws, Mermaid diagrams included, the site
  has to draw too, which is the site's work rather than a rule for the pages.
- **Held here by `DocumentedSkillTest`**: the README tells a coding agent what the page for coding
  agents tells it, in the first block of code under "Asking your agent", which the site's first page
  takes from that page and shows under "Coding agent".
- **Between the two repositories:** the site's file `restest-version` holds the tag the site
  documents, with its `v` (`v2.4.0`), the only form it reads; and the site's `llms.txt` opens with
  "The documentation of RESTest 2.4.0,", the sentence the workflow of §5 waits for.

### 4. The latest release, not `master`

The site documents what people install. What is merged into `docs/` reaches the site with the next
release, and until then GitHub shows it. Every week the site is also built from `master`, without
publishing it, so that a page the next version would not build with is known before its tag.

### 5. Moved on by a release's publication

`.github/workflows/documentation-site.yml`, started by a release's publication or by hand with a tag,
checks that the tag is a published release rather than a draft, writes it into the site's
`restest-version` — never over a higher version, and not again when it is there already — and waits
up to fifteen minutes for the site's `llms.txt` to name it. It writes with a **deploy key**, kept in
an environment of RESTest's, `restest-site`, which only `master` and version tags may use, as
Homebrew's tap and the jbang catalog are ([ADR-0040](0040-homebrew.md) §5,
[ADR-0045](0045-jbang.md) §5). A push made with a deploy key, unlike one made with a workflow's own
token, starts the site's workflow, which builds the site from the new tag and publishes it.

A tag never changes. Should a release's pages not build on the site, **the site is changed until it
builds that tag**; `restest-version` is never put back to an earlier version, and the site publishes
nothing new meanwhile (maintainer). This is why, before a version is tagged, the site's build from
`master` has to be green ([Continuous integration](../ci.md#releases)).

### 6. The menu is named by the indexes

The site's menu has the manual's chapters, in their order and with their numbers, and then a group for
each `##` section of `docs/README.md`, named by it, with a row for each page (maintainer). That is why
the first section is called "Reference".

## Consequences

- A page added to `docs/` needs an entry in an index, and a chapter one in the manual's list, or
  RESTest's build fails, saying which.
- A change to `docs/` is on the site with the next release, not when it is merged.
- A second rendering to check, which ADR-0031 counted against a PDF of the manual. What keeps it in
  step is `DocumentationSiteTest` on every pull request, and the site's weekly build from `master`.
- Two repositories and a key between them: the environment, the deploy key and its secret are set up
  once, by an owner of the organisation ([Continuous integration](../ci.md#the-documentation-site)).
- The address is GitHub's until the project has a domain of its own. GitHub then sends the old
  address on to the new one, and this record, the README, the skill and the workflow change with it.

## Alternatives considered

- **The site in this repository**, built from the same commit as the pages, with no second repository
  and no key: but Node and npm in a repository built with Maven, which every contributor would meet,
  and the site's showcase, which shows what RESTest finds on real APIs, is built from measurements
  made outside this repository, which keeps none (principle 9, [ADR-0011](0011-evaluation-harness.md)).
- **The site's own build, run on every pull request of RESTest**, in place of
  `DocumentationSiteTest`: one set of rules rather than two that may drift apart, and the checks that
  only the site makes would come before a merge too; but Node in RESTest's continuous integration,
  and every pull request here depending on the site's `main`, so that a change to the site could
  fail a pull request that changed no page.
- **GitHub alone**, as now: no search across pages, no menu, no `llms.txt`, and unreleased
  documentation by default.
- **`release.yml` telling the site to rebuild**, with a token: `release.yml` only drafts a release, so
  the site would have built the version before it.
- **The site asking GitHub which release is the latest**, at each build: the same commit of the site
  would build different pages on different days, and the version it documents would be written
  nowhere.
- **`repository_dispatch` with a personal token**: a token that expires, held by one person.
- **Publishing `master`**: the documentation would describe what no release does yet.
