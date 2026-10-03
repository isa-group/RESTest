# Troubleshooting

When a run does not do what you expected, it nearly always says why, either in a line beginning with
`restest:` or in its exit code. This chapter goes from what you see to what to do about it.

## RESTest does not start

**`RESTest has not been built in this checkout yet`**, and the exit code `127`. The `./restest`
script found nothing to run: build it with `./mvnw -q install -DskipTests` ([chapter
2](02-installing.md)), and read what that prints if it fails.

**`UnsupportedClassVersionError`**, or *has been compiled by a more recent version of the Java
Runtime*, and the exit code `1`. The Java that ran RESTest is older than 21. `java -version` says
which one the terminal finds; install Java 21 or later, or point `JAVA_HOME` and `PATH` at it. That
`1` comes from Java, not from RESTest, and says nothing about the API.

**On Windows, `./restest` is not recognised.** It is a shell script: run it from Git Bash.

## The run refuses to begin: exit code `2`

Something on the command line, or in a file it names, could not be used, and nothing was sent. The
line beginning with `restest:` says what: an option misspelt, a value of the wrong kind, a plan or a
settings file with a word RESTest does not know, a key that cannot be placed. Plans and settings
files are refused whole, never half-read, because what a file leaves out may be exactly what you
meant it to keep away from. Fix the line it names, and run again.

## Nothing was tested: exit code `3`

The run ended without evidence about the API, and the last line says which of these it was.

**`nothing at … answered any of the … requests`.** RESTest reached nothing at the address it was
using. Either the API is not running yet — usual when a script starts the API and RESTest together,
since RESTest starts quickly — or the address is wrong. The first line of the run says which address
it used. Check it with `curl`, wait for the API to answer, and run again.

**`the budget of … ran out before a single request could be sent`.** Reading the document and
starting up take a moment, paid out of the budget. Give the run more time.

**`the document describes no operation that could be tested`**, or a count of the operations that
could not be. RESTest read the document and found nothing it can send. The lines before it say why;
an upload that the API insists on, for example, is one kind of request it does not build.

**No address.** A document whose `servers` gives no address RESTest can reach — none at all, or only
a path such as `/api/v3` — needs one: give it with `--url`.

## Almost every request is refused

The summary line counting replies by class — `2xx`, `4xx`, `5xx` — is mostly `4xx`.

**Nearly all `401` or `403`.** The API wants a key, and was not given one. The first lines of the run
say whether the document declares one, and how to hand it over: see [chapter
8](08-an-api-that-asks-for-a-key.md).

**Nearly all `404`.** The requests are going to the wrong place. `--url` given with a path uses that
path instead of the one the document declares, so `--url http://localhost:9966` keeps the document's
`/petclinic/api` while `--url http://localhost:9966/api` replaces it. Compare the address in a
fault's `curl` command with one you know works.

**Mostly `400`.** The API refuses the values. Read a few of the replies — `jq '.findings'` in the
report, or `run.sqlite` with `--store` — to see what it objects to, and hand over values it accepts
in a dictionary ([chapter 6](06-dictionaries.md)). Remember that some refusals are meant: the
summary says how many requests pushed at the API on purpose or changed one thing in a request it
had accepted.

## The API falls over under the run

RESTest keeps several requests in flight at once, and slows down by itself when the API does. An API
that cannot take that — it answers 503, or stops answering, or its log fills with errors about
connections — gets one request at a time with `--set engine.maxConcurrency=1`. An API that is simply
slow gets longer to answer with `--set engine.readTimeout=2m`. [Chapter 7](07-settings.md) has more.

## The run left data behind

It did: RESTest creates, changes and deletes whatever the document says it can. Point it at a copy
of the API you can start afresh, and never at one whose data matters. To keep a run to requests that
change nothing, use the plan's filter on methods ([chapter 5](05-plans.md#changing-nothing-on-the-api)).

## Two runs of the same command differ

They are meant to. A run learns from what the API answers and changes requests it accepted, so the
same `--seed` makes a similar run rather than the same one; and the API is not in the same state the
second time, because the first run changed it. Start the API afresh before each run you mean to
compare. To make `--seed` repeat a run exactly, use the plan and the settings in [Getting the seed
back](../switches.md#getting-the-seed-back). To keep the run you had, use `--store`.

## RESTest itself went wrong: exit code `4`

RESTest lost requests on their way to the API or back, had to keep exchanges without their details,
or broke outright, so what it printed may be incomplete. The lines beginning with `restest:` say
which, with the first error in full. That is a fault in RESTest rather than in the API: report it
with those lines, the two lines `./restest version` prints, and the command you ran.

## No report was written

A run stopped with `kill -9`, or by a container's memory limit, ends at once with `137` and leaves
nothing behind: nothing of RESTest ran after it. Every other way of stopping a run — Ctrl-C, `kill`,
`docker stop` — leaves the summary and the report, saying that the run was cut short, or says plainly
what it could not leave. [Stopping a run](../command-line.md#stopping-a-run) has the details.

The [last chapter](11-glossary.md) explains the words this manual uses.
