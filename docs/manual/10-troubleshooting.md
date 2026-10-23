# Troubleshooting

When a run does not do what you expected, it nearly always says why, either in a line beginning with
`restest:` or in its exit code. This chapter goes from what you see to what to do about it.

## RESTest does not start

**`restest: RESTest needs Java 21 or later, and … is Java 17.0.20.1`**, or **`… there is no java on
the PATH`**, and the exit code `127`, from the command in the archive. It found no Java it can use:
it takes the one `JAVA_HOME` names, and otherwise the first `java` on the `PATH`. Install Java 21
or later, or point `JAVA_HOME` at one you have ([chapter 2](02-installing.md#with-java)). That `127`
comes before RESTest starts, and says nothing about the API.

**`RESTest has not been built in this checkout yet`**, and the exit code `127`. The `./restest`
script of a checkout found nothing to run: no build of the version the checkout declares, which is
also what you see after a `git pull` that moves it to a new version. Build it with
`./mvnw -q install -DskipTests` ([chapter 2](02-installing.md#from-the-source)), and read what that
prints if it fails.

**`Unrecognized option: --enable-native-access=ALL-UNNAMED`**, or **`UnsupportedClassVersionError`**
(*has been compiled by a more recent version of the Java Runtime*), and the exit code `1`. The Java
that runs a checkout's `./restest` is older than 21: the first message comes from Java 16 or older,
the second from 17 to 20. `./restest` uses the `java` the terminal finds first, which need not be
the one that built RESTest; `java -version` says which it is. Put Java 21 or later first on the
`PATH`. That `1` comes from Java, not from RESTest, and says nothing about the API.

**On Windows, `./restest` is not recognised.** It is a shell script: run it from Git Bash, or use
`bin\restest.cmd` from the archive.

**With Docker, nothing answers at `localhost`.** Inside the container, `localhost` is the container
itself. An API on your own machine is `host.docker.internal`, which Linux knows only with
`--add-host=host.docker.internal:host-gateway`; the `restest` script of
[chapter 2](02-installing.md#with-docker) has it. A document read from your machine that names
`localhost` as its API's address needs `--url` with `host.docker.internal` in its place.

**With Docker on Linux, nothing answers at `host.docker.internal` either**, while `curl` reaches the
API at `localhost`. The API listens only on `127.0.0.1`, and `host.docker.internal` is another
address of the machine. Put `--network host` in the script in place of `--add-host=…`, and give
`localhost` again; or have the API listen on every address, `0.0.0.0`.

**With Docker, the run cannot find its document, or writes nothing you can see.** A file is read
from the folder lent to the container as `/work`, and the report is written there: run the command
from the folder that holds the document, with the `-v "$PWD:/work"` of chapter 2's script. A file
named by a path outside that folder is not in the container at all.

## The run refuses to begin: exit code `2`

Something on the command line, or in a file it names, could not be used, and nothing was sent. The
line beginning with `restest:` says what: an option misspelt, a value of the wrong kind, a plan or a
settings file with a word RESTest does not know, a key that cannot be placed. Plans and settings
files are refused whole, never half-read, because what a file leaves out may be exactly what you
meant it to keep away from. Fix the line it names, and run again.

## Nothing was tested: exit code `3`

The run ended without evidence about the API, and its lines say which of these it was.

**`nothing could be read from …`**, under `the document describes no operation that could be
tested`. RESTest could not fetch or open the document at all. With the clinic, which serves its
own document, this is what a run says when the clinic is not running yet, or not yet ready, or the
address is mistyped:

```
restest: the document describes no operation that could be tested
1 part(s) of the document could not be read:
  location: nothing could be read from 'http://localhost:9966/petclinic/v3/api-docs'
```

Check the address with `curl`, wait for the API to answer, and run again. For a document that is a
file, check its path.

**`nothing at … answered any of the … requests`.** The document was read, but nothing answered at
the address the requests went to. Either the API is not running yet — usual when a script starts the
API and RESTest together, since RESTest starts quickly — or the address is wrong. The first line of
the run says which address it used. When the line goes on to say that some requests `were sent on an
open connection that never got a reply`, something there takes connections and answers none of
them: a service that does not speak HTTP, or an API whose every operation waits on something that
does not answer, such as a database that is down. Each of those requests is also reported as a
fault, `F312`. Check what is listening at the address; if it is the API and it is only slow, give it
longer with `--set engine.readTimeout=2m`.

**`the budget of … ran out before a single request could be sent`.** Reading the document and
starting up take a moment, paid out of the budget. Give the run more time.

**`the document describes no operation that could be tested`** with no `nothing could be read`
under it, or a count of the operations that could not be. RESTest read the document and found
nothing it can send. The lines after it say why; an upload that the API insists on, for example, is
one kind of request it does not build.

**No address.** A document whose `servers` gives no address RESTest can reach — none at all, or only
a path such as `/api/v3` — needs one: give it with `--url`.

**`the address given with --url carries …`.** RESTest refuses an address with a query string, or
with a user name or a password before its host, and does not repeat it, since it may hold a key:

```
restest: the address given with --url carries a user name or a password before its host, which is not repeated here. Give the address without them, such as http://localhost:8080/api/v3, and a key the API asks for with --auth
```

Give the address without them, and a key with `--auth` ([chapter 8](08-an-api-that-asks-for-a-key.md)).

**`nothing can be written to …`.** The directory given with `--out`, or `restest-out/` by default,
cannot be written to. Choose another with `--out`.

## Almost every request is refused

The summary line counting replies by class — `2xx`, `4xx`, `5xx` — is mostly `4xx`.

**Nearly all `401` or `403`.** The API wants a key, and was not given one. The first lines of the run
say whether the document declares one, and how to hand it over: see [chapter
8](08-an-api-that-asks-for-a-key.md).

**Nearly all `404`.** The requests are going to the wrong place. `--url` given with a path uses that
path instead of the one the document declares, so for the clinic, whose document declares
`http://localhost:9966/petclinic`, `--url http://localhost:9966` keeps `/petclinic` while
`--url http://localhost:9966/other` replaces it. Compare the address in a fault's `curl` command with
one you know works.

**Mostly `400`.** The API refuses the values. The report keeps only faults, and a refusal the
document declares is not one, so run again with `--store` and read a few of the refusals from the
stored run:

```bash
sqlite3 restest-out/run.sqlite "SELECT operation, json_extract(document, '$.outcome.body.text') FROM interaction WHERE status_code = 400 LIMIT 5"
```

What the API objects to is usually in its own words there. Hand over values it accepts in a
dictionary ([chapter 6](06-dictionaries.md)). Remember that some refusals are meant: the
summary says how many requests pushed at the API on purpose or changed one thing in a request it
had accepted.

## Most warnings are status codes the document does not declare

The summary's warnings are mostly *Undeclared status code* (`F920`), and the operations it names
answer `400` or `404`. The document lists what each operation answers when all goes well and leaves
out the ways it refuses, so every request that meets a refusal is a warning. Warnings do not fail the
run unless `checks.failOnWarnings` is on. Each one is true — the document says less than the API
does — and the fix is to declare those replies in the document, or `default` for every reply it does
not name. Until then `--set checks.undeclaredStatusCode=false` leaves the other checks as they are.
The same goes for *Undeclared content type* (`F921`) and a document that declares
`application/json` for replies the API sends as `application/problem+json`, which
`--set checks.undeclaredContentType=false` stops.

## The API falls over under the run

RESTest keeps several requests in flight at once, and slows down by itself when the API does. An API
that cannot take that — it answers 503, or stops answering, or its log fills with errors about
connections — gets one request at a time with `--set engine.maxConcurrency=1`. Each 503 is reported
as a fault, `F121`, which a run whose pace is to blame can stop with
`--set checks.otherServerError=false`; each request it stops answering, as `F312`, which
`--set checks.noReplyInTime=false` stops. An API that is simply slow gets longer to answer with
`--set engine.readTimeout=2m`. [Chapter 7](07-settings.md) has more.

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
with those lines, the two lines `restest version` prints, and the command you ran.

## No report was written

A run stopped with `kill -9`, or by a container's memory limit, ends at once with `137` and leaves
nothing behind: nothing of RESTest ran after it. Every other way of stopping a run — Ctrl-C, `kill`,
`docker stop` — leaves the summary and the report, saying that the run was cut short, or says plainly
what it could not leave. [Stopping a run](../command-line.md#stopping-a-run) has the details.

The [last chapter](11-glossary.md) explains the words this manual uses.
