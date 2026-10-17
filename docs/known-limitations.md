# Known limitations

What RESTest does not do yet, or does in a way that may surprise you. Each entry says what happens
today and, where there is something you can do about it, what that is. The list is kept to the
current release: an entry leaves it in the release that lifts it, and that release's notes say so.

## Pointed at somebody else's API

- **A run does not wait when the API asks it to.** A reply of 429, or of 503 with `Retry-After`, is
  an ordinary answer: the header is not read and nothing pauses. A 429 is usually a quick answer, and
  the engine's limiter judges only how long answers take, so a stream of 429s reads to it as room to
  send more. Against an API that limits its callers, lower `engine.maxConcurrency`
  ([the settings](settings.md)) before you start. A 503 the run's own pace provoked is reported as a
  fault, `F121`, like any other; `checks.otherServerError=false` stops that
  ([the faults](faults.md#f121-a-server-error-other-than-500)).
- **There is no ceiling on requests per second.** What bounds a run is the number of requests in
  flight, sixteen at most by default (`engine.maxConcurrency`). The limiter halves it whenever a
  request goes unanswered and sends fewer as answers slow down, but no setting promises an API's
  owner a rate. Lowering `engine.maxConcurrency` is the nearest thing.
- **A run does not stop when every request is refused.** If the API answers 401 or 403 to
  everything, because a key is wrong or was revoked, the run goes on until its budget ends. A 401 the
  document declares is not a fault, and one it leaves out is reported, `F920`, on every request; the
  summary at the end counts the replies by status. Try a new key with a short `--budget` first.
- **A run does not stop when the API stops answering.** The limiter falls to one request in flight
  as unanswered requests pile up, and the run keeps trying until its budget ends. Keep `--budget` to
  what you are prepared to spend on an API that may go down. An API that still takes connections and
  no longer answers on them has every request from then on reported as a fault, `F312`, though the
  run's own pace may be what brought it down; `checks.noReplyInTime=false` stops that
  ([the faults](faults.md#f312-no-reply-in-time)).

Every request names the tool in its `User-Agent` (`engine.userAgent`), so that an API's owner can
tell RESTest's traffic apart and refuse it, unless the document declares that header as a parameter
of its own. Whether you may test an API at all is not something any tool can check.

## Credentials

- **Only credentials you already hold.** An API key, or a bearer token or cookie given with the
  place it goes, through `--auth` ([an API that asks for a key](manual/08-an-api-that-asks-for-a-key.md)).
  RESTest does not sign in by itself, does not do OAuth2, does not refresh what expires, and holds one
  set of credentials per run.

## What a run sends

- **Dependencies between parameters are not read.** RESTest 1.x read them in IDL - "if `from` is
  given, `to` must be too" - and built requests that honoured them. This version reads no IDL yet,
  whether in the document or beside it, so a request may break such a rule and be refused for it;
  the refusal is counted like any other.
- **Optional parameters and optional bodies share one number.** With
  `generation.optionalParametersBySize` off, each optional parameter is decided on a coin weighted by
  `generation.optionalBodyChance`, the same number that decides whether a request sends a body it
  may leave out, so changing one changes the other ([the settings](settings.md#four-things-worth-knowing)).
  Leave `optionalParametersBySize` on, which is the default, if you want to change only bodies.
- **A value can be offered back to the operation that first used it.** A value an accepted request
  carried is offered to the other operations and not back to its own, but a value seen again replaces
  what was known of it. An e-mail address goes from a registration to a login; the login is accepted;
  the address is then remembered as the login's, and the registration is offered it again and
  refused as already made. There is nothing to set; it costs the run some refused requests.

## What a run says

- **"No faults found" does not mean every reply was checked.** A run reports how many replies it
  judged and how many faults it found, not how many replies it could not judge. If the shape the
  document declares for a reply is one the checker cannot use — a pattern its regular-expression
  engine rejects, a shape that names something absent — those replies are skipped, and the run can
  end with exit code `0` while the check never ran on that operation ([ADR-0014](adr/0014-response-conformance.md)).
- **A check is switched off for the whole run, never for one operation.** Turning
  `checks.replyOffSchema` off because one operation's error replies are not the shape its document
  gives hides every other reply off its schema too, on every operation
  ([the faults](faults.md#switching-a-check-off)).
- **A document that leaves out its refusals makes many faults, and a build fail.** Every reply whose
  status code the document does not declare is a fault, `F920`, and so is every reply sent as a
  content type it does not declare, `F921`; a document that lists only what goes well can make most
  of a run's replies faults, and turn a run that ended with `0` into one that ends with `1`. Each is
  true, and RESTest does not yet tell a fault the API should fix from one its document should: fix
  the document, or switch the check off for the run (`checks.undeclaredStatusCode`,
  `checks.undeclaredContentType`; [the faults](faults.md#f920-a-status-code-the-document-does-not-declare)).
- **The faults written out on the screen are the first of any kind.** The screen writes out the
  first 50 faults whole (`report.faultsShownOnTheConsole`) and counts the rest. A document that
  leaves out its refusals fills those places with `F920` from the first requests, so a 500 found
  later is only counted there: in a minute's run of an API with both, the 50 were all `F920` and
  `F205`, and none of its 10,902 replies of 500. Read the counts under the faults, and `report.json`,
  which writes out the first five of each kind on each operation; or switch `F920` off for the run.
- **A type that only resembles a declared one is reported.** `application/problem+json`, where the
  document declares `application/json`, is `F921`, though both are JSON
  ([the faults](faults.md#f921-a-content-type-the-document-does-not-declare)).
- **A reply the document declares in a form RESTest cannot read is reported as undeclared.** The
  library RESTest reads documents with drops a reply written as something other than an object —
  `"404": "not found"` — and says so before the run, without naming the operation. That operation's
  404s are then `F920`, though the document meant to declare them. An operation RESTest knows it read
  only in part, such as one with a reply pointing at a shape the document never defines, is left
  alone.
- **A body the document describes only in words is not checked against a type.** A reply with a body
  whose status code the document declares with no content type at all is left alone, whatever type
  it comes as.
- **Not every failure of RESTest's own ends in `4`.** Exit code `4` means RESTest itself broke
  ([the exit codes](command-line.md#exit-codes)), but three such failures still read as something the
  API did ([ADR-0015](adr/0015-command-line-contract.md)):
  - a key that cannot be added to a request: the request is not sent and is recorded as a failure on
    the way to the API, so a run where every request goes that way ends with `3` and points at the
    address, not at the key;
  - a failure in RESTest's own code inside the engine, in the code that records what went over the
    wire, say, which is recorded as a network failure;
  - a failure while the run deals with an answer, such as handing it to a series, which is lost: the
    request counts as answered and no report hears of it.

  A run that ends with `3` although the API is reachable is worth reading as one of these.
- **A request on a connection something in between dropped is reported as one the API sat on.** The
  engine keeps a connection open between requests, for up to five minutes. A load balancer or a
  firewall that drops one without a word leaves a request sent on it unanswered, and RESTest reports
  it as `F312` once it stops waiting, although it reached nobody
  ([the faults](faults.md#f312-no-reply-in-time)).
- **With redirections followed, a request the next address sits on is reported on the operation.**
  With `engine.followRedirects` on, which it is not by default, an operation that sends the request
  on to another address that takes it and never answers is reported as `F312`, and the `curl`
  printed with it shows only the first answer, the redirection. The same goes for the reply at the
  end of the redirections, which is judged against what the document declares for the operation
  that was asked: a login page answered with `200` as `text/html` is `F921` there.
- **A reply that comes, but slowly, is not reported.** Only a request that got no reply before
  RESTest stopped waiting is a fault; one answered in twenty-nine seconds, against the half a minute
  it waits by default, is not. Nor is a reply whose status line and headers arrived and whose body
  never finished.
- **`report.json` does not say that a run broke.** When a run ends with `4`, the file reads like that
  of a finished run. Read the exit code, not only the file.

## The files a run reads

- **The settings file has no JSON Schema.** A dictionary and a plan have one ([the JSON
  Schemas](schemas.md)); a file of settings is checked only by RESTest, as it starts. Start one from
  what `restest run --print-settings` writes, which RESTest reads back as it is.
- **A file is read up to three million characters and fifty levels deep.** A dictionary, a plan or
  a file of settings longer or deeper than that is refused by name, before the run starts. A
  dictionary a program writes for a large API is tens of thousands of characters and three levels
  deep, so neither limit is one a file somebody meant reaches. A list or a map that contains itself,
  through a YAML alias to something it is inside of, is refused the same way.
