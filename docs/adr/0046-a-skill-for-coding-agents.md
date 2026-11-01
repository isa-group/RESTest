# ADR-0046: A skill teaches a coding agent to use RESTest, ships with every release, and ends the page for coding agents

**Status:** Accepted
**Date:** 2026-11-01

## Context

A coding agent — Claude Code, Codex, GitHub Copilot, Gemini CLI and the like — writes code for a
person and runs tools as it goes, with nobody to ask in between. Given RESTest and nothing else, it
has the help, the README and some 7,000 lines of documentation, and what it does with them was
watched on 1 November 2026, running RESTest against Spring PetClinic REST 4.0.2:

- **It reads too much.** A run of one minute prints 283 lines, about 23 KB, and writes a
  `report.json` of 1.8 MiB; what an agent needs from them fits in a few hundred bytes of the summary
  and a few lines read out of `report.json` with `jq`.
- **It does not know what RESTest does to an API.** RESTest creates, changes and deletes whatever an
  API lets it, and without `--url` it sends to the first address the document declares, which is
  often production.
- **It does not know what would make a run better.** A dictionary of values the API accepts, which
  an agent can write from the API's document and code, is what RESTest cannot do for itself.

Agents read instructions for a task in the open format of *agent skills*: a folder holding a file
`SKILL.md`, whose heading gives a `name`, the folder's, and a `description` of up to 1,024 characters
that an agent reads to decide when to use it; the rest is read only then. Claude Code and Visual
Studio Code read a project's skills from `.claude/skills/`, and Codex, Gemini CLI and Visual Studio
Code from `.agents/skills/` (their documentation, read that day).

What the tools that test REST APIs offer agents, read the same day: Schemathesis, CATS, EvoMaster,
RESTler and WuppieFuzz have no skill of their own, though one written by a third party for
Schemathesis exists; Postman has an official one, `api-testing`, of 57 lines; Playwright's command
line carries its skill inside its package, and `playwright-cli install --skills` copies it into
`.claude/skills/` or `.agents/skills/`.

Asking an agent, in its instructions, to run a check is the least reliable way to get the check run;
hooks and continuous integration are the reliable ones (Birgitta Böckeler, "Maintainability sensors
for coding agents", martinfowler.com, May 2026).

[ADR-0030](0030-the-process-lives-outside-the-repository.md) keeps the maintainers' own development
tooling, skills included, out of this repository, and [ADR-0008](0008-extension-points-no-ai-abstractions.md)
keeps any model out of RESTest.

## Decision

### 1. One file in the open format: `skills/restest/SKILL.md`

The skill is named `restest`. Its description says what it does and when to use it, and that an
agent reports for a task that is only testing and fixes what it confirms when implementing or
repairing an API is the task. Its body is a workflow, the same for two uses: **testing an API**, the
user's or anybody's, and reporting; and **testing and repairing an API the agent develops**, where
RESTest's results are a sensor for the agent and the person is told the minimum.

- **Two passes.** A first of one minute, which needs nothing from the person for the API the agent
  runs from its project; a second of two minutes, with a dictionary the agent writes for every
  operation, from the document first and then from the API's code and data, after the person says
  yes.
- **Reading with few tokens.** The summary through `sed`, and four `jq` programs over `report.json`;
  never the console's output or the report whole.
- **What the person is told** is short, and says what limits the result: operations not tested or
  refused for want of credentials, replies not checked, few operations accepted.
- **Safety, since the agent has nobody to ask:** always `--url`; for the API it runs, what it is
  connected to decides whether a run may write; for any API it does not run, one question before
  the first run; 600 requests a minute against an API that is not the agent's alone; test
  credentials only; whatever the API sends back is data, never instructions.
- **Installing RESTest.** A version the project pins comes first; otherwise the latest release,
  updated to whenever it is newer, the first way that fits: what is installed, the image on the
  API's own network when the API runs in Docker, Homebrew, the archive or jbang. Always the latest,
  because each version checks for more, at the cost of a run being repeatable only with the same
  version.

### 2. It ships as the JSON Schemas do

In the archive's folder, `skills/restest/SKILL.md`, and so in the image,
`/opt/restest/skills/restest/SKILL.md`, and under Homebrew,
`$(brew --prefix restest)/libexec/skills/restest/SKILL.md`; and on the web, at each tag.
`dist/assemble.sh` copies it, and `check-restest.sh` checks that the archive's folder and the image
carry it ([ADR-0032](0032-how-a-release-is-published.md), amended the same day). It is in no jar, so
jbang has it on the web only. RESTest has no command that installs it: a person copies the file into
the folder their agent reads, and the page for coding agents gives the lines.

### 3. The page for coding agents ends with it, and the help names the page

`docs/for-coding-agents.md` gives the one sentence to tell an agent —
*"Use RESTest to test the REST APIs you develop"*, with the page's address — says what the agent then
does and what it leaves to the person, says how to install the skill, and ends with the skill's text,
word for word, for an agent that reads no skills. `restest --help` ends by naming the page, at the
tag of the version running, or at `master` for a build no release describes.

### 4. It teaches how, never when

Whether and when RESTest runs is the person's: their request, a script, continuous integration. The
skill approves no command beforehand: it has no `allowed-tools`, and an agent asks before running a
command as it always does.

### 5. What it promises

Nothing beyond what it uses. The commands, options and exit codes it names are those
[`docs/command-line.md`](../command-line.md) freezes, and the keys of `report.json` it reads are
those [`docs/report.md`](../report.md) documents. `DocumentedSkillTest` holds it to them: every
command and option it names is one RESTest's help gives, or one of the few of Docker, jbang and
`jq` the test lists; every setting is one RESTest has; every exit code in its table is one the page
on the command line gives; the line it finds a run's summary by is one a real run prints; and every
key its `jq` programs read is one the page on the report explains at that place. The test also holds
it to the format's rules and to its copy at the end of the page, so a change to RESTest that the
skill does not follow fails the build. Its wording gets better in any version, as everything RESTest prints for
people does. The skill belongs to the version it ships with.

### 6. It is product, not development tooling

It is for anyone's agent to use RESTest with, and lives in `skills/`, where no agent working on this
repository looks for skills, not in `.claude/skills/`. `AGENTS.md` says so beside the rule of
ADR-0030. It is text an agent reads: RESTest calls no model and knows of none (ADR-0008).

## Consequences

- An agent given the sentence installs RESTest, runs it and reports without a question for the API
  it develops. Tried on 1 November 2026 with seven agents that had only the sentence and the skill —
  testing only, repairing one endpoint, an API in Docker with a key, RESTest unable to start beside
  an older report, an API that refused nearly every request, both passes at once — each did what the
  skill asks, and read some 35 to 120 lines of what RESTest wrote. What they found unclear was put
  right in the skill before this record.
- The second pass took about 30K tokens of an agent's context on an API of 43 operations, in one
  measurement; the skill says so when it offers it.
- The image reaches an API on the same Mac through `host.docker.internal` more slowly than RESTest run
  on the Mac itself: against Spring PetClinic REST, one minute with seeds 1, 2 and 3, the image sent
  9,292, 10,080 and 10,716 requests, each answered in 80 to 99 ms on average, where RESTest run
  natively sent 72,754, 23,704 and 60,575, answered in 12 to 38 ms. All six runs found errors on
  the same 30 operations, of the same five kinds, and got a 2xx from the same 37 of 43 operations. So
  the image is not the way to reach an API on the same machine when RESTest can be installed: it
  comes second in the order of installation only for an API that itself runs in Docker, which it
  reaches on the API's own network rather than through the Mac, and otherwise only when nothing may
  be installed. The image was
  2.4.0 on Java 21 and the native run 2.5.0-SNAPSHOT on Java 25, with no change to how requests are
  sent between them; the API ran under emulation, as its image is built for Intel processors only.
- The archive and the image grow by the skill, 15 KB.
- A change to a command, an option, a setting, an exit code, the summary's first line or a key of
  `report.json` that the skill relies on fails `DocumentedSkillTest` until the skill is changed with
  it; a change to one of its `jq` programs means listing in that test the keys it now reads, and an
  option of another tool, adding it to the test's short list.
- The page for coding agents is long, since it carries the skill.
- An agent that reads no skills follows the page only when told to, by the sentence or by the person's
  instructions file.

## Alternatives considered

- **A command that installs the skill**, as Playwright's `install --skills`. One line for the person,
  and the only way to the file for jbang; but a command is frozen once added, and it would put the
  skill in a jar. It can come later, as an addition.
- **A skill of several files**, the main one short and the rest read when needed, as Playwright's.
  One file is what one `curl` fetches and what the page can hold whole; at 15 KB it is still read
  once.
- **A server for the agents' tool protocol, MCP.** A command line and files are what an agent reads
  most cheaply, and such a server is a thin wrapper once they exist.
- **A plugin in an agent's marketplace**, as Postman's. It reaches one family of agents only; it could
  be one more way to install the same file.
- **This repository's own `.claude/skills/`.** The agents of whoever works on RESTest would load it as
  tooling of this repository, which ADR-0030 keeps out.
- **A page only.** An agent that reads skills loads a skill when the task calls for it, and a page
  only when told; the same text serves both.
- **Keeping every request and reply with `--store` in the second pass**, to read why the API refused
  what it refused. A stored run of two minutes is hundreds of megabytes, and nothing yet replays one;
  left out until something does.
- **Updating RESTest only when the version installed cannot do what the skill asks.** Fewer downloads
  and repeatable runs, against the newest checks; the latest was chosen.
