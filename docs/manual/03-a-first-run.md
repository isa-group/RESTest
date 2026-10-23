# A first run

This chapter starts an API on your own machine, runs RESTest against it, and looks at what comes
back. Every chapter after it uses the same API.

## A practice API

The examples in this manual run against **Spring PetClinic REST**, the API of a fictional veterinary
clinic: owners, their pets, the pets' visits, and the vets who see them. Its developers publish it as
a Docker image, so one command starts it:

```bash
docker run -d --name petclinic -p 9966:9966 springcommunity/spring-petclinic-rest:4.0.2
```

The image is built for Intel and AMD processors. On a Mac with Apple silicon, Docker runs it in
emulation and warns that the platforms do not match: it works, and takes a little longer to start.

The `4.0.2` after its name is the version every output in this manual came from; a later one may
describe itself differently.

It keeps its data in memory, which makes it a good API to practise on: RESTest can create, change
and delete whatever it likes, and removing the container and starting it again gives you a clinic as
good as new. It takes a few seconds to start. When this prints a list of pet types, it is ready:

```bash
curl -s http://localhost:9966/petclinic/api/pettypes
```

```
[{"name":"bird","id":5},{"name":"cat","id":1},{"name":"dog","id":2},{"name":"hamster","id":6},{"name":"lizard","id":3},{"name":"snake","id":4}]
```

The clinic also serves its own OpenAPI document, which is everything RESTest needs:

```bash
curl -s http://localhost:9966/petclinic/v3/api-docs | head -c 300
```

> **No Docker?** The OpenAPI project keeps a demonstration pet shop online, and RESTest's
> [README](../../README.md#your-first-run) runs against it. It is somebody else's server, shared by
> everyone who tries it, so keep your runs there short. The examples in this manual use the clinic.

## The first run

```bash
restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

That is the whole command: the document, and how long to test for. RESTest finds the address of the
API in the document itself. Without `--budget` it tests for a minute. With Docker, write
`host.docker.internal` in place of `localhost` ([chapter 2](02-installing.md#with-docker)).

It begins like this, with what it will test and then each fault as it finds it:

```
RESTest testing REST Petclinic backend API documentation at http://localhost:9966/petclinic

43 of 43 operations can be tested, seed -8717084955806451298, budget 30s
  what it sends depends on the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent
1 part(s) of the document could not be read:
  info: attribute info.license.extensions is unexpected

warning  Undeclared status code  (F920)
         redirectToSwagger_5 - HEAD http://localhost:9966/petclinic/  ->  302
         the API answered 302, which the document does not declare for this operation; it declares 200
         curl --head 'http://localhost:9966/petclinic/' -H 'Accept: */*' -H 'User-Agent: RESTest/2.0'
```

and a few faults later comes the first error:

```
error    Internal server error  (F100)
         addUser - POST http://localhost:9966/petclinic/api/users  ->  500
         the API answered 500, so it fell over while handling this request
         curl -i -X POST 'http://localhost:9966/petclinic/api/users' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0' -H 'Content-Type: application/json' --data-binary '{"username":"john.doe","password":"1234abc","enabled":true}'
```

After ten warnings it stops printing them, after fifty faults it stops printing any, and it ends with
a summary:

```
... more faults are being found; every one of them is counted in the run's report and in the total below

6991 requests to 43 operations in 30.5s, 8% of it idle
  opening lap: 43 requests in 2.3s, 35 of 43 operations answered 2xx
  2351 2xx, 942 3xx, 2690 4xx, 1008 5xx
  504 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 2690 refusals above
  1043 of them changed one thing in a request the API had accepted
  328 of them were steps of 97 series about a thing the run created: readAfterDelete 81, createTwice 52, deleteTwice 51, writeUnderDeleted 15, putTwice 72, safeGet 57; 92 creation(s) meant to begin one were not accepted
  30 operation(s) answered 500, 30 answered some 5xx
1032 errors, on 30 operations:
  1008 x Internal server error  (F100)
        look in the API's log for what failed while it handled the request; the document is not what to fix
  24 x Invalid data accepted  (F206)
        make the API refuse what its document forbids; where the document forbids more than the API should, loosen the document instead
2723 warnings, on 36 operations, which do not fail the run (checks.failOnWarnings):
  1972 x Undeclared status code  (F920)
        declare the status code for the operation in the document, or stop the API answering it
  742 x Undeclared content type  (F921)
        declare the content type for that status code in the document, or have the API send the one declared
  9 x Reply off schema  (F200)
        the API and its document disagree about the reply's body: compare the places listed with the document, and fix whichever is wrong
report written to restest-out/report.json (1.4 MiB)
the run itself was not kept; pass --store to keep every request and reply
```

What you are looking at, from the top:

1. **What will be tested**: the API's title and address, how many of the document's operations
   RESTest can send requests to, the seed and the budget. The line under it says that this run, like
   every run of the plan RESTest carries, learns from the API's replies, so running the same command
   again makes a similar run rather than the same one. The last two say that one part of the
   clinic's document could not be read: a detail of its licence, which no operation depends on. A
   part of a document RESTest cannot read is said, never silently ignored, and an operation it
   affects is named at the end as one that could not be tested.
2. **The faults**, printed as they are found. Each says first whether it is an *error*, the API
   failing at its job — here, falling over with a 500 — or a *warning*, the API answering something
   its document does not describe — here, a status code the document leaves out. Then what kind of
   fault it is, in RESTest's words and by number; which request caused it; what the API answered;
   what is wrong; and a `curl` command that sends the request again. At most ten of the first fifty
   are warnings, so that errors found later still have room; after that, the run only counts them.
3. **The summary**: how many requests went to how many operations, how the API answered them, then
   the errors and the warnings by kind, each with a line on what to look at.
4. **Where the report was written**: `restest-out/report.json`.

The run ends with `1`, because it found errors. Warnings alone would have ended it with `0`: the
clinic's document leaves out most of the ways the clinic says no, and that is worth knowing, but it
is not the clinic failing. [Chapter 9](09-exit-codes-and-ci.md) says what every number means.

The [next chapter](04-reading-what-a-run-says.md) reads all of this in detail. For now, two lines of
the summary are worth a look. The one counting replies by class — `2xx`, `4xx`, `5xx` — says whether
the run was mostly accepted or mostly refused: an API that refuses almost everything usually means
the requests were the problem, not the API. And the one counting the operations that answered 500
is the number to quote, because it counts operations rather than replies.

## Running it again

The same command again:

```bash
restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

replaces what the first run left in `restest-out/`. Give a run a directory of its own with `--out` to
keep it:

```bash
restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --out runs/first
```

The numbers will differ from run to run. A run chooses values by chance, and the clinic has changed:
the first run created owners and pets, and deleted some. Starting the clinic again puts it back as it
was:

```bash
docker rm -f petclinic
docker run -d --name petclinic -p 9966:9966 springcommunity/spring-petclinic-rest:4.0.2
```

## Keeping everything a run sent

`report.json` keeps every fault counted, and the first few of each kind written out whole. To keep
every request and every reply, add `--store`:

```bash
restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --store
```

It writes a second file, `restest-out/run.sqlite`, a database that the next chapter reads. It is off
by default because it is large: a minute against a fast API keeps hundreds of megabytes.

## Stopping a run early

Press Ctrl-C. The run stops sending, waits a moment for the answers already on their way, and prints
its summary and writes its report as usual, saying that it was cut short. It ends with the number
`130` rather than `0` or `1`, so that a script never mistakes a run stopped half-way for one that
finished:

```bash
echo $?
```

```
130
```

`kill` and `docker stop` stop a run the same way, and it ends with `143`. Chapter 9 says what every
number means.

The [next chapter](04-reading-what-a-run-says.md) reads what a run says.
