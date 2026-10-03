# An API that asks for a key

Many APIs want each request to say who is sending it, most simply with a **key**: a string the API
gave you, sent in a header, in the address or in a cookie. An API run without its key answers almost
every request with `401 Unauthorized` or `403 Forbidden`, and a run of refusals finds nothing behind
them. `--auth` hands the key over. The rules are in [Handing over a key or a
token](../command-line.md#handing-over-a-key-or-a-token).

The clinic asks for no key, so this chapter uses the OpenAPI project's demonstration pet shop, whose
document declares one. It is somebody else's server: keep these runs short.

## What a run says before it starts

Run against the pet shop without a key, and the run says so before its first request:

```bash
./restest run https://petstore3.swagger.io/api/v3/openapi.json \
    --url https://petstore3.swagger.io/api/v3 --budget 10s
```

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at https://petstore3.swagger.io/api/v3

19 of 19 operations can be tested, seed -2597290240742393242, budget 10s
  2 of them ask for an API key that was not given (api_key, in the header api_key): --auth <key> gives it
  what it sends depends on the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent
```

The document declares a key called `api_key`, sent in a header of the same name, and two of the
pet shop's operations ask for it.

## Handing it over

```bash
./restest run https://petstore3.swagger.io/api/v3/openapi.json \
    --url https://petstore3.swagger.io/api/v3 --budget 10s --auth special-key
```

```
RESTest testing Swagger Petstore - OpenAPI 3.0 at https://petstore3.swagger.io/api/v3

19 of 19 operations can be tested, seed 5938395244660637024, budget 10s
  the key given with --auth goes with 3 of them, in the header api_key; what the run writes says REDACTED-AUTH in its place
  what it sends depends on the API's own replies, so the seed alone does not repeat this run; --store keeps what it sent
```

RESTest sends the key where the document says, with the operations that ask for it, and nowhere
else. It goes with three operations where two asked for it, because the pet shop's deletion of a pet
also declares, among its own parameters, a header called `api_key`: an input named like the key is
filled with the key. The last part of that line is the word the run writes wherever the key went,
`REDACTED-AUTH`.

`--auth` takes a key in one of three ways:

- **The key alone**, `--auth special-key`, when the document declares exactly one.
- **Named**, `--auth api_key=special-key`, when it declares several: the name is the one the
  document gives the key.
- **With its place**, when the document declares none, or for something you already hold that the
  document does not describe: `--auth header:X-API-Key=…`, `--auth query:apiKey=…`,
  `--auth cookie:JSESSIONID=…`. A key given with its place goes with every request.

A bearer token you already have goes the last way, as the header it is:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s \
    --auth 'header:Authorization=Bearer eyJhbGciOi...'
```

Give `--auth` more than once for several keys. RESTest does not sign in by itself, ask for a token or
renew one: it sends what it is given.

## Keeping the key out of your shell's history

A key typed on the command line stays in the shell's history. `RESTEST_AUTH` holds the same as one
`--auth`, and set only for the command that needs it, it goes nowhere else:

```bash
RESTEST_AUTH=special-key ./restest run https://petstore3.swagger.io/api/v3/openapi.json \
    --url https://petstore3.swagger.io/api/v3 --budget 10s
```

Set it this way, for one command, rather than once for the whole session: while it is set, it goes
to whatever API any run tests.

## What happens to the key afterwards

**It is written into nothing a run leaves behind.** The screen, `report.json` and its `curl`
commands, and `run.sqlite` all show a word such as `REDACTED-AUTH.api_key` where the key went. A
`curl` command copied from a report therefore needs the key put back in place of that word before it
does what the run did.

An API that repeats the key back in a reply — some echo the request's headers — has it hidden there
too, and the run says at the end how many replies did.

The [next chapter](09-exit-codes-and-ci.md) puts RESTest in a build.
