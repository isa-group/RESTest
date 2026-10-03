# A first run

This chapter starts an API on your own machine, runs RESTest against it, and looks at what comes
back. Every chapter after it uses the same API.

## A practice API

The examples in this manual run against **Spring PetClinic REST**, the API of a fictional veterinary
clinic: owners, their pets, the pets' visits, and the vets who see them. Its developers publish it as
a Docker image, so one command starts it:

```bash
docker run -d --name petclinic -p 9966:9966 springcommunity/spring-petclinic-rest
```

It keeps its data in memory, which makes it a good API to practise on: RESTest can create, change
and delete whatever it likes, and removing the container and starting it again gives you a clinic as
good as new. It takes a few seconds to start. When this prints a list of pet types, it is ready:

```bash
curl -s http://localhost:9966/petclinic/api/pettypes
```

```
TO BE RUN: curl -s http://localhost:9966/petclinic/api/pettypes
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

```
TO BE RUN: ./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
```

What you are looking at, from the top:

1. **What will be tested**: the API's title and address, how many of the document's operations
   RESTest can send requests to, the seed and the budget. The line under it says that this run, like
   every run of the plan RESTest carries, learns from the API's replies, so running the same command
   again makes a similar run rather than the same one.
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
docker run -d --name petclinic -p 9966:9966 springcommunity/spring-petclinic-rest
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
