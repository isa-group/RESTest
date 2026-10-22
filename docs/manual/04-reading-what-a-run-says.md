# Reading what a run says

A run says what it found three times over: on the screen as it goes, in `report.json` when it ends,
and, with `--store`, in `run.sqlite`. This chapter reads each with a question in mind. Every line and
every key is listed in [What a run leaves behind](../report.md), and every kind of fault in [The
faults RESTest reports](../faults.md); this chapter is about using them.

## A fault, line by line

A fault from the clinic, as the screen prints it:

```
error    Internal server error  (F100)
         getVisit - GET http://localhost:9966/petclinic/api/visits/-2147483649  ->  500
         the API answered 500, so it fell over while handling this request
         curl -i -X GET 'http://localhost:9966/petclinic/api/visits/-2147483649' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0'
```

- `error` says how serious it is. An **error** is the API failing at its job: it fell over, never
  answered, or accepted what its own document forbids. A **warning**, below, is the API answering
  something its document does not describe, where the document may be the one out of date. A run
  that finds an error ends with `1`; one that finds only warnings, with `0` ([chapter
  9](09-exit-codes-and-ci.md)).
- `Internal server error  (F100)` is the kind of fault, in RESTest's words, and its number from a
  catalogue several testing tools share. A number from 900 up, such as `F920`, is one of the few
  kinds the catalogue has no number for, numbered by RESTest itself. The title is also the name of
  the check that reports it: `checks.internalServerError` switches it off.
- The second line is the request: the operation, by the name the document gives it, then the method
  and the address, and after the arrow what the API answered. This one asked for a visit by an
  identifier one below the smallest whole number the clinic can hold, and instead of refusing it,
  the clinic fell over.
- The third says what is wrong, in a sentence.
- The last is a `curl` command that sends exactly the same request again. Paste it into a terminal
  and the clinic falls over the same way, which is what whoever fixes it will want to see.

A reply off its schema, `F200`, is a warning, and says more, because a reply can be wrong in several
places at once. The screen prints the first ten warnings a run finds, and on the clinic those are
nearly all status codes and content types its document leaves out, so this one is from a run with
those two checks switched off (`checks.undeclaredStatusCode` and `checks.undeclaredContentType`):

```
warning  Reply off schema  (F200)
         listPets - GET http://localhost:9966/petclinic/api/pets  ->  200
         the body does not match the shape the specification declares for it, answering 200 as application/json
           /2/type: null found, object expected
           /3/type: null found, object expected
         curl -i -X GET 'http://localhost:9966/petclinic/api/pets' -H 'Accept: application/json' -H 'User-Agent: RESTest/2.0'
```

The indented lines under the sentence are the places in the reply that disagree with the document,
and how: here, two pets of the list have no type, where the document says every pet has one. A
reply that leaves out something the document calls required, or sends a number as text, shows here.
That is why it is a warning: either side can be wrong, the API or the document describing it.

## The summary

```
7015 requests to 43 operations in 30.0s, 9% of it idle
  opening lap: 43 requests in 2.4s, 35 of 43 operations answered 2xx
  2422 2xx, 936 3xx, 2637 4xx, 1020 5xx
  482 of them were pushing at the API with values nobody sensible would send, which accounts for some of the 2637 refusals above
  1032 of them changed one thing in a request the API had accepted
  407 of them were steps of 119 series about a thing the run created: createTwice 67, safeGet 72, putTwice 132, writeUnderDeleted 12, readAfterDelete 71, deleteTwice 53; 114 creation(s) meant to begin one were not accepted
  30 operation(s) answered 500, 30 answered some 5xx
1034 errors, on 30 operations:
  1020 x Internal server error  (F100)
        look in the API's log for what failed while it handled the request; the document is not what to fix
  14 x Invalid data accepted  (F206)
        make the API refuse what its document forbids; where the document forbids more than the API should, loosen the document instead
2662 warnings, on 37 operations, which do not fail the run (checks.failOnWarnings):
  1960 x Undeclared status code  (F920)
        declare the status code for the operation in the document, or stop the API answering it
  674 x Undeclared content type  (F921)
        declare the content type for that status code in the document, or have the API send the one declared
  28 x Reply off schema  (F200)
        the API and its document disagree about the reply's body: compare the places listed with the document, and fix whichever is wrong
```

Read it from the top:

- **How much was sent**: requests, operations, time, and how much of that time RESTest had nothing in
  flight. That last number, *idle*, is kept as close to nothing as possible: the budget is for
  testing.
- **The opening lap**: the first round, which sent every operation once with the request most likely
  to be accepted, and how many operations answered it with a success.
- **How the API answered**, by class of status code. `3xx` is a redirection, which RESTest reports
  rather than follows. Mostly `4xx` means mostly refused, and then the
  requests are what need work: [chapter 6](06-dictionaries.md) shows how to hand over values the API
  will accept.
- **What kind of requests were sent**: how many pushed at the API on purpose, how many changed one
  thing in a request the API had accepted, how many were steps of a series. Their refusals are
  expected, and they are counted here so that they do not read as the API turning away ordinary
  requests.
- **How many operations answered 500**, and how many some 5xx. This counts operations, not replies:
  one broken operation asked six hundred times is six hundred replies, but one operation.
- **The errors**, on how many operations, each kind with what to look at under it; then **the
  warnings**, the same way, and whether they failed the run. Errors come first because they are what
  ends a run with `1`.
- **What could not be checked**, if anything: how many replies a check could not judge, in which way
  and why — the shape of a body the document gives no shape for, say. `no faults found` says nothing
  about a reply nobody could judge.
- **What could not be tested**, if anything, each operation with the reason. `no faults found` says
  nothing about an operation that was never tried, so read this line before celebrating.

## Asking the report

`report.json` holds all of it for a program to read. [`jq`](https://jqlang.org/), a small
command-line tool for JSON, answers most questions in one line. These assume the report of the last
run is in `restest-out/`.

**Which operations broke, and how often?**

```bash
jq -r '.faultsByOperation[] | select(.severity == "error") | "\(.count)\t\(.title)\t\(.operation)"' restest-out/report.json | sort -rn | head -8
```

```
61	Internal server error	getOwner
56	Internal server error	getSpecialty
56	Internal server error	getPet
54	Internal server error	getVisit
54	Internal server error	getPetType
52	Internal server error	addUser
47	Internal server error	getVet
46	Internal server error	addVisitToOwner
```

Without the `select`, the warnings come too: on the clinic, most of them are a redirection its
document does not declare, on the six operations that send a browser to its documentation page.

**How do I reproduce the first fault?**

```bash
jq -r '.findings[0].curl' restest-out/report.json
```

Every fault written out whole has one: the first five of each kind on each operation, up to a
thousand in all. The rest are counted, never lost: `faultsByOperation` has every one.

**Did the run test everything?**

```bash
jq '.skippedOperations' restest-out/report.json
```

An empty list means every operation the document describes was tried. Otherwise each entry names an
operation and says why it could not be.

**What did the API answer most?**

```bash
jq '.replies' restest-out/report.json
```

**Is the second run better than the first?** Keep each in a directory of its own with `--out`, and
compare their totals:

```bash
jq -c '.totals' runs/first/report.json runs/second/report.json
```

Two runs are only worth comparing if the API was in the same state at the start of each: start the
clinic again between them.

## Asking the stored run

A run made with `--store` keeps every request and reply in `run.sqlite`, an ordinary SQLite database
with one table, `interaction`. Every run replaces what the one before left in `restest-out/`, the
stored run included, so if your last run was not made with `--store` — the one stopped with Ctrl-C at
the end of the last chapter was not — make one now:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --store
```

[`sqlite3`](https://sqlite.org/cli.html), SQLite's own command-line tool, asks it anything:

```bash
sqlite3 restest-out/run.sqlite "SELECT operation, status_code, COUNT(*) FROM interaction GROUP BY operation, status_code ORDER BY operation, status_code" | head -12
```

```
addOwner|201|32
addOwner|400|19
addOwner|500|4
addPetToOwner|201|8
addPetToOwner|400|15
addPetToOwner|404|14
addPetToOwner|500|17
addPetType|201|42
addPetType|400|12
addPetType|500|2
addSpecialty|201|31
addSpecialty|400|18
```

Each row's `document` column holds the whole exchange as JSON — what the request was meant to be,
where every value in it came from, what was sent and what came back — in the same shape as the
exchange a fault in `report.json` carries under `interaction`:

```bash
sqlite3 restest-out/run.sqlite "SELECT document FROM interaction WHERE status_code = 500 ORDER BY sent_at_nanos LIMIT 1" | jq .
```

[What a run leaves behind](../report.md#runsqlite) lists every column, and every key of that
document.

The [next chapter](05-plans.md) changes what a run sends.
