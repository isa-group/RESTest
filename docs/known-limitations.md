# Known limitations

What RESTest does not do yet, or does in a way that may surprise you. Each entry says what happens
today and, where there is something you can do about it, what that is. The list is kept to the
current release: an entry leaves it in the release that lifts it, and that release's notes say so.

## Pointed at somebody else's API

- **A run does not wait when the API asks it to.** A reply of 429, or of 503 with `Retry-After`, is
  an ordinary answer: the header is not read and nothing pauses. A 429 is usually a quick answer, and
  the engine's limiter judges only how long answers take, so a stream of 429s reads to it as room to
  send more. Against an API that limits its callers, lower `engine.maxConcurrency`
  ([the settings](settings.md)) before you start.
- **There is no ceiling on requests per second.** What bounds a run is the number of requests in
  flight, sixteen at most by default (`engine.maxConcurrency`). The limiter halves it whenever a
  request goes unanswered and sends fewer as answers slow down, but no setting promises an API's
  owner a rate. Lowering `engine.maxConcurrency` is the nearest thing.
- **A run does not stop when every request is refused.** If the API answers 401 or 403 to
  everything, because a key is wrong or was revoked, the run goes on until its budget ends, and a 401
  is not a fault, so nothing is printed while it runs; the summary at the end counts the replies by
  status. Try a new key with a short `--budget` first.
- **A run does not stop when the API stops answering.** The limiter falls to one request in flight
  as unanswered requests pile up, and the run keeps trying until its budget ends. Keep `--budget` to
  what you are prepared to spend on an API that may go down.

Every request names the tool in its `User-Agent` (`engine.userAgent`), so that an API's owner can
tell RESTest's traffic apart and refuse it, unless the document declares that header as a parameter
of its own. Whether you may test an API at all is not something any tool can check.

## Credentials

- **Only credentials you already hold.** An API key, or a bearer token or cookie given with the
  place it goes, through `--auth` ([an API that asks for a key](manual/08-an-api-that-asks-for-a-key.md)).
  RESTest does not sign in by itself, does not do OAuth2, does not refresh what expires, and holds one
  set of credentials per run.

## What a run sends

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
- **`report.json` does not say that a run broke.** When a run ends with `4`, the file reads like that
  of a finished run. Read the exit code, not only the file.
