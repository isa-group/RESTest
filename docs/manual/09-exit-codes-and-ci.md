# Exit codes and continuous integration

Every run ends with a number, its **exit code**, which is what a script or a build server acts on.
This chapter says what the numbers mean in practice and shows RESTest in a build. Every number, with
the details, is in [the exit codes](../command-line.md#exit-codes).

## The numbers

| Code | In short |
|---|---|
| `0` | The run finished and found no error, in what it could check |
| `1` | The run finished and found at least one error — or a warning, when asked |
| `2` | The command was wrong, or a plan, a settings file or a key it named could not be used. Nothing was sent |
| `3` | Nothing was tested: the document could not be read or has no operation that can be tried, there is no address to send to — or `--url` gave one RESTest refuses — or nowhere to write the results, nothing answered, or the budget ran out first |
| `4` | RESTest itself went wrong, so what it printed may be incomplete |
| `130` | The run was stopped with Ctrl-C |
| `143` | The run was stopped with `kill` or `docker stop` |

Two things are worth seeing in that table. An error is `1` rather than `0`, so that a build goes red
when the API is broken. And a run stopped from outside never ends with `0` or `1`, so that a run cut
short never passes for one that finished.

Two things are not in it. **A warning does not make `1` unless asked.** An error is the API failing
at its job — falling over, never answering, accepting what its document forbids — and a warning the
API answering something its document does not describe, a status code it leaves out, say, where the
document may be the one out of date ([errors and warnings](../faults.md#errors-and-warnings)). A run
that found only warnings ends with `0`, and its summary says how many there were and that they did
not fail it. And **a `0` covers what the run could check.** A reply whose body the document gives no
shape for has nothing to be compared with, so nothing can be found wrong with it, and that is no
fault and never fails a run. The summary says how many replies could not be checked and why, and
`notChecked` in `report.json` says which operations they came from
([what a run leaves behind](../report.md#notchecked)).

After a run, the shell keeps the number in `$?`:

```bash
restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
echo $?
```

## In a script

A script that tells the three outcomes apart — no error, errors found, and RESTest not able to test
at all — and says which:

```bash
restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
code=$?
case $code in
  0) echo "RESTest found no error" ;;
  1) echo "RESTest found errors: see restest-out/report.json"; exit 1 ;;
  *) echo "RESTest could not test the API (exit code $code)"; exit 2 ;;
esac
```

The difference between `1` and the rest matters most when a build fails. `1` is a finding about the
API, and the report says what. Anything else is a finding about the run: the API was not up yet, the
address was wrong, a file was misspelt. [Chapter 10](10-troubleshooting.md) goes through them.

One thing to watch for in a script that starts the API and RESTest together: RESTest starts
quickly, and an API that is still starting answers nothing — not even its own document, when it
serves one — which is `3`. Wait until the API answers
before running RESTest, as the example below does.

## In GitHub Actions

A workflow that starts the API as a service container, fetches RESTest's archive, waits for the API,
tests it for two minutes and keeps the report whatever happens. The clinic stands in for your API here;
replace the image, the address and the document with your own.

```yaml
name: API tests

on: [push, pull_request]

jobs:
  restest:
    runs-on: ubuntu-latest
    services:
      api:
        image: springcommunity/spring-petclinic-rest:4.0.2
        ports:
          - 9966:9966
    steps:
      - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1
        with:
          distribution: temurin
          java-version: '21'

      - name: Get RESTest
        run: curl -fsSL https://github.com/isa-group/RESTest/releases/download/v2.2.0/restest-2.2.0.tgz | tar -xz

      - name: Wait for the API
        run: |
          for attempt in $(seq 60); do
            curl -sf http://localhost:9966/petclinic/api/pettypes > /dev/null && exit 0
            sleep 2
          done
          exit 1

      - name: Test the API
        run: restest-2.2.0/bin/restest run http://localhost:9966/petclinic/v3/api-docs --budget 2m

      - name: Keep the report
        if: always()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: restest-report
          path: restest-out/report.json
```

Each action is pinned to a commit rather than to a version tag, which whoever owns an action can
move to other code at any time; the comment says which version the commit is, and Dependabot, if you
turn it on for GitHub Actions in your repository, keeps both up to date.

The step that tests the API fails the job when RESTest ends with anything but `0`, which is usually
what a build wants. `if: always()` keeps the report even then, and it is the report that says what
went wrong: download it from the run's page on GitHub.

Some things to decide for your own build:

- **The budget.** A longer run finds more, but more slowly the longer it goes; a few minutes is a
  reasonable start.
- **What the API starts with.** A run creates and deletes, so start the API afresh for each build,
  as a service container does, rather than pointing RESTest at one shared by other builds.
- **Whether warnings should fail the build.** By default only errors do. A team that owns its
  document and wants the build to go red the moment the API and the document disagree makes
  warnings fail it too:

  ```bash
  restest-2.2.0/bin/restest run http://localhost:9966/petclinic/v3/api-docs --budget 2m --set checks.failOnWarnings=true
  ```

  The summary then says so under the warnings it found, and the run ends with `1`. From a run of the
  clinic with its errors switched off, so that only warnings are left:

  ```
  2843 warnings, on 37 operations, which fail the run, as checks.failOnWarnings asks:
    2077 x Undeclared status code  (F920)
          declare the status code for the operation in the document, or stop the API answering it
    749 x Undeclared content type  (F921)
          declare the content type for that status code in the document, or have the API send the one declared
    17 x Reply off schema  (F200)
          the API and its document disagree about the reply's body: compare the places listed with the document, and fix whichever is wrong
  ```
- **Whether errors should fail the build.** An API with known errors fails every build until they
  are fixed. Until then, the step can let them through and fail on everything else — the API not
  answering, a misspelt file — by treating `1` as a pass:

  ```bash
  restest-2.2.0/bin/restest run http://localhost:9966/petclinic/v3/api-docs --budget 2m || [ $? -eq 1 ]
  ```

The [next chapter](10-troubleshooting.md) is about runs that do not do what you expected.
