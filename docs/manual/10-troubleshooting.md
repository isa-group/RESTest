# Troubleshooting

When a run does not do what you expected, it nearly always says why, either in a line beginning with
`restest:` or in its exit code. This chapter goes from what you see to what to do about it.

## RESTest does not start

**`restest: RESTest needs Java 21 or later, and … is Java 17.0.20.1`**, or **`… there is no java on
the PATH`**, and the exit code `127`, from the command in the archive. It found no Java it can use:
it takes the one `JAVA_HOME` names, and otherwise the first `java` on the `PATH`. Install Java 21
or later, or point `JAVA_HOME` at one you have ([chapter 2](02-installing.md#with-java)), or install
RESTest with Homebrew, whose command brings its own Java and never answers this
([chapter 2](02-installing.md#with-homebrew)). That `127` comes before RESTest starts, and says nothing
about the API.

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

## The API stopped the run: exit code `5`

The summary's first line ends with `stopped: the API answered 401 to every request of the opening
lap`, and the run ended long before its budget. Every operation was sent once, with the request it
was likeliest to accept, and the API answered each one with `401 Unauthorized`: it refuses all of
itself to a caller without a key it will take. Rather than send it the same refusal thousands of
times a second until the budget ends, the run stopped sending there. The pet shop of [chapter
8](08-an-api-that-asks-for-a-key.md), run against a stand-in that answers 401 to everything, ends
like this, after its findings:

```
19 requests to 19 operations in 1.2s, 98% of it idle, stopped: the API answered 401 to every request of the opening lap
  opening lap: 19 requests in 53ms, 0 of 19 operations answered 2xx
  19 4xx
no faults found
restest: the API answered 401 to each of the 19 requests of the opening lap, so the run stopped sending after 1.2s of its 30s budget (schedule.stopWhenTheOpeningLapIsRefused). It was handed no key, and the document asks for a token for petstore_auth (oauth2): --auth 'header:Authorization=Bearer <token>' gives one you already have; and an API key (api_key, in the header api_key): --auth <key> gives it. RESTest does not yet sign in by itself to get a token.
```

The last line says what to do. A run that was handed no key is told how the document's is handed
over; one that held a key is told where it went, and that the API refused it — check that it is the
right one and that it has not expired. `report.json` has the same under
[`unauthorized`](../report.md#unauthorized), and its `verdict` is `unauthorized`.

A single reply other than 401 to the opening lap — an operation anybody may call, a `400` for a value
the API checks before it asks who is calling, a `403` — or a request of it that got no reply, and the
run goes on as usual: it can still learn something. To have a run the API refuses go on until its
budget ends anyway, as an API that lets a caller in only after a while would need, turn the stop
off:

```bash
restest run openapi.yaml --set schedule.stopWhenTheOpeningLapIsRefused=false
```

The stand-in is a few lines of Java, for trying the stop out without an API to hand:

```java
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

/** Answers 401 to every request, as an API does to a caller with no key it will take. */
public class Refuse {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8401), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        System.out.println("answering 401 to every request at http://localhost:8401");
    }
}
```

Saved as `Refuse.java`, `java Refuse.java` starts it, and `--url http://localhost:8401` points a run
at it.

## Almost every request is refused

The summary line counting replies by class — `2xx`, `4xx`, `5xx` — is mostly `4xx`.

**Nearly all `401` or `403`.** The API wants a key, and was not given one, or not one it takes. The
first lines of the run say whether the document declares one, and how to hand it over: see [chapter
8](08-an-api-that-asks-for-a-key.md). When even the opening lap was refused whole, the run stopped
there, [as above](#the-api-stopped-the-run-exit-code-5); when some operations answer, it goes on and
tests those.

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

## The API asks the run to wait

The summary's first line gives, after the idle share, how much of it was `waiting as the API asked`,
and a line under the replies starts `told to wait:`. The API answered some requests with `429 Too
Many Requests`, or with `503` and a `Retry-After` header, which is how an API asks a caller to slow
down. RESTest then sends nothing to that operation for as long as the header says, or, when it says
nothing, for a second that doubles each time the operation is told again, up to a minute; the other
operations go on. When a second operation is told to wait while another still waits, the limit is
the whole API's, and nothing at all is sent until the wait is over. The pet shop of [chapter
8](08-an-api-that-asks-for-a-key.md), run for 20 seconds against a stand-in that lets 20 requests a
second through and asks for a second's quiet after that (below),

```bash
restest run https://petstore3.swagger.io/api/v3/openapi.json --url http://localhost:8429 --budget 20s
```

begins its summary like this:

```
541 requests to 19 operations in 20.0s, 99% of it idle (91% waiting as the API asked)
  opening lap: 19 requests in 181ms, 19 of 19 operations answered 2xx
  381 2xx, 148 4xx, 12 no reply
  46 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 148 refusals above
  86 of them changed one thing in a request the API had accepted
  38 of them were steps of 23 series about a thing the run created: createTwice 30, putTwice 2, deleteTwice 3, readAfterDelete 2, safeGet 1; 8 creation(s) meant to begin one were not accepted
  told to wait: the whole API 19 times, 18.1s in all; addPet 8 times, 7.1s in all, 0 turns skipped; findPetsByStatus 7 times, 6.1s in all, 0 turns skipped; findPetsByTags 7 times, 7.0s in all, 0 turns skipped; and 16 more; the run's report names every one
```

Nearly all of the run was spent waiting, as the API asked, and the idle share says so: the brackets
are the API's doing, not the tool's. The `12 no reply` are requests RESTest had already handed over
when the last wait began, held back and never sent once the run's time was up. `report.json` names every operation under
[`toldToWait`](../report.md#toldtowait). Each 429 the document does not declare is still a warning,
*Undeclared status code* (`F920`), and each 503 an error (`F121`); there are few of them once the run
waits. Against a quota that lasts longer than the run — so many creations an hour — that operation is
simply tried again once a minute while the others are tested. To send to such an API as RESTest did
before it waited, turn the waiting off:

```bash
restest run openapi.yaml --set schedule.waitWhenToldTo=false
```

The stand-in is a few lines of Java:

```java
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Lets 20 requests a second through and asks for a second's quiet after that. */
public class Limit {
    private static long second, inThisSecond, quietUntil;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8429), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            long now = System.currentTimeMillis();
            boolean let;
            synchronized (Limit.class) {
                if (now / 1000 != second) {
                    second = now / 1000;
                    inThisSecond = 0;
                }
                let = now >= quietUntil && ++inThisSecond <= 20;
                if (!let && now >= quietUntil) {
                    quietUntil = now + 1000;
                }
            }
            if (let) {
                byte[] body = "[]".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.getResponseHeaders().add("Retry-After", "1");
                exchange.sendResponseHeaders(429, -1);
            }
            exchange.close();
        });
        server.start();
        System.out.println("letting 20 requests a second through at http://localhost:8429");
    }
}
```

Saved as `Limit.java`, `java Limit.java` starts it, and `--url http://localhost:8429` points a run at
it.

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
