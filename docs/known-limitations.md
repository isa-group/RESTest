# Known limitations

What RESTest does not do yet, or does in a way that may surprise you, as of 2.1. Each entry says
what happens today and what you can do about it. The list shrinks as the limitations are lifted; a
release's notes say when one is.

## Pointed at somebody else's API

- **A run does not slow down when the API asks it to.** A reply of 429, or of 503 with
  `Retry-After`, is treated as an ordinary answer, and a quick one, so the engine's limiter reads it
  as room to send more. The header is not read. Against an API that limits its callers, lower
  `engine.maxConcurrency` ([the settings](settings.md)) before you start.
- **There is no ceiling on requests per second.** What bounds a run is the number of requests in
  flight, sixteen at most by default (`engine.maxConcurrency`). The limiter halves it whenever a
  request goes unanswered and sends fewer as answers slow down, but no setting promises an API's
  owner a rate.
- **A run does not stop when every request is refused.** If the API answers 401 or 403 to
  everything, because a key is wrong or was revoked, the run goes on until its budget ends. Check the
  first replies of a run, or use a short `--budget` the first time you try a key.
- **A run does not stop when the API stops answering.** The limiter falls to one request in flight
  as unanswered requests pile up, and the run keeps trying until its budget ends.

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
- **A value can be offered back to the operation that first used it.** A value an accepted request
  carried is offered to the other operations and not back to its own, but a value seen again replaces
  what was known of it. An e-mail address goes from a registration to a login; the login is accepted;
  the address is then remembered as the login's, and the registration is offered it again and
  refused as already made.

## What a run says

- **`report.json` does not say that a run broke.** When RESTest itself fails during a run, the exit
  code is `4` ([the exit codes](command-line.md#exit-codes)), but the report does not record it.
