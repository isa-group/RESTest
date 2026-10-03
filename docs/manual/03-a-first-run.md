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
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

That is the whole command: the document, and how long to test for. RESTest finds the address of the
API in the document itself. Without `--budget` it tests for a minute.

It begins like this, with what it will test and then each fault as it finds it:

```
RESTest testing REST Petclinic backend API documentation at http://localhost:9966/petclinic

43 of 43 operations can be tested, seed -78297543526922448, budget 30s
  what it sends depends on the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent
1 part(s) of the document could not be read:
  info: attribute info.license.extensions is unexpected

F100  HTTP Status 500
      getVisit - GET http://localhost:9966/petclinic/api/visits/99999999999999999999999999999999  ->  500
      the API answered 500, so it fell over while handling this request
      curl -i -X GET 'http://localhost:9966/petclinic/api/visits/99999999999999999999999999999999' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0'
```

and after fifty faults it stops printing them and ends with a summary:

```
... more faults are being found; every one of them is counted in the run's report and in the total below

3388 requests to 43 operations in 30.1s, 14% of it idle
  opening lap: 43 requests in 3.5s, 34 of 43 operations answered 2xx
  1153 2xx, 462 3xx, 1145 4xx, 628 5xx
  561 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 1145 refusals above
  329 of them changed one thing in a request the API had accepted
  98 of them were steps of 26 series about a thing the run created: putTwice 32, createTwice 4, deleteTwice 15, safeGet 27, readAfterDelete 20; 44 creation(s) meant to begin one were not accepted
  30 operation(s) answered 500, 30 answered some 5xx
730 faults:
  628 x F100  HTTP Status 500
  102 x F200  Schema Violation: Received A Response From API With A Structure/Data That Is Not Matching Its Schema
report written to restest-out/report.json (452.0 KiB)
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
2. **The faults**, printed as they are found. Each says what kind it is, which request caused it,
   what the API answered, what is wrong, and gives a `curl` command that sends the request again.
   After the first fifty, the run stops printing them and only counts them.
3. **The summary**: how many requests went to how many operations, how the API answered them, and the
   faults by kind.
4. **Where the report was written**: `restest-out/report.json`.

The [next chapter](04-reading-what-a-run-says.md) reads all of this in detail. For now, two lines of
the summary are worth a look. The one counting replies by class — `2xx`, `4xx`, `5xx` — says whether
the run was mostly accepted or mostly refused: an API that refuses almost everything usually means
the requests were the problem, not the API. And the one counting the operations that answered 500
is the number to quote, because it counts operations rather than replies.

## Running it again

The same command again:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

replaces what the first run left in `restest-out/`. Give a run a directory of its own with `--out` to
keep it:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --out runs/first
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
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --store
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
