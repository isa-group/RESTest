# ADR-0029: A key is handed over by the person running the tool, sent where the document says, and hidden in everything the run writes

**Status:** Accepted
**Date:** 2026-09-30

## Context

An API that wants a key answers 401 to every request that comes without one. A run against it is
a run of refusals: nothing past the check is reached, and no fault behind it is found. The call for
participation of the 2027 competition says each tool is handed the document, the address and "any
required authentication material (e.g., API keys)". Where one of the five known APIs needs signing
in, the benchmark's own proxy signs the tool in, so the tool is handed nothing. Whether the five
undisclosed APIs are signed in the same way, or hand the tool a key, is not known. Roadmap row 11.3
was added by the maintainer on 29 September for the second case.

### What a document says, and what it cannot

OpenAPI says **where** a key goes and never **what** it is. A document declares its ways of proving
who one is under `components.securitySchemes` (`securityDefinitions` in Swagger 2.0), each under a
name of its own. One of type `apiKey` says the key travels `in` a header, the query or a cookie,
under a `name`. The `security` list then says which of them a request needs:
- for the whole API, and again for one operation, which then wins;
- as alternatives, any one of which will do;
- each alternative naming the schemes it needs at once;
- an empty alternative, `{}`, meaning anybody may call;
- and `security: []` on an operation meaning that operation asks for nothing.

There is no field for the key itself, and a document published for everybody to read has no business
holding one. The Petstore's test key, `special-key`, is a sentence in its description.

### What the corpus shows

- Eight of the corpus's 46 documents declare a key, four in a header and four in the query, none in
  a cookie.
- Two of those eight, BigOven and Tumblr, never ask for theirs on any operation.
- DHL names its scheme `API Key`, with a space in it.
- Two documents carry a key as an ordinary input. LanguageTool declares no scheme and asks for
  `apiKey` as a field of the form it takes on three operations and in the query on the fourth. The
  Petstore declares `api_key` as a scheme and, beside it, as an optional header of `deletePet`,
  whose requirement is OAuth 2 rather than the key.
- Of the seven APIs RESTest 1.x's own configurations reached with a key, three documents do not
  declare it.
- None of the five priority documents declares a key.

`SecurityReadingTest` pins those numbers.

### What the related tools do

Surveyed on 29 September from each tool's documentation and source: Schemathesis, EvoMaster,
RESTler, CATS, RestTestGen, WuppieFuzz, AutoRestTest, ARAT-RL and RESTest 1.x.
- The value always comes from outside the document: a flag, a configuration file, a script, or a
  sign-in the tool performs.
- Only Schemathesis reads the document to place a key: a credential named after a scheme goes where
  the scheme says, and only to the operations whose requirements it satisfies. Every other tool
  sends the same credentials with every request.
- RestTestGen lets a credential take the place of a parameter of the same name.
- Masking is uneven. RESTler replaces tokens in its logs, and Schemathesis masks values by the names
  of the headers that carry them. CATS writes a variable name in place of each value in a replay.
  EvoMaster and RestTestGen write credentials into the tests they generate.

### Web Fuzzing Commons

WFC Authentication 0.7.0, published in WebFuzzing/Commons release 0.9.0 beside the fault
catalogue RESTest already uses, is a file format for authentication. EvoMaster, Schemathesis, CATS
and WuppieFuzz read it. It describes **users**, `auth[]`, with shared parts in `authTemplate`. Each
user can have:
- `fixedHeaders`, sent with every request;
- a sign-in, `loginEndpointAuth`: a request to an endpoint whose reply yields a token, from its body
  or a header, sent in a header or the query with a template such as `Bearer {token}`, or cookies
  instead;
- users the tool creates itself, `createUsers`.

It does not look at a document's security schemes, and it has no way to say that a fixed key goes
in the query or in a cookie. The maintainer asked, on 30 September, that this be the shape the rest
of authentication comes in by.

### A key is neither a setting nor part of a plan

[`docs/settings.md`](../settings.md) asks of every setting: *would it mean the same thing for a
different API on the same machine?* A key does not. Nor is it part of a plan, which is written once
for an API and means the same on every machine. A key belongs to one deployment and one person.
Treating it as a setting would also be the surest way to leak it, because every setting is printed
by `--print-settings` and recorded in `report.json` with where it came from.

## Decision

### 1. What is read from the document

`ApiModel` carries the schemes a document declares and what it asks of every request; `Operation`
carries what it asks for itself; `ApiModel.securityFor(operation)` works out which applies, as
`serversFor` does for servers.

A scheme is a `SecurityScheme`, one of four kinds:
- `ApiKey`: in a header, the query or a cookie, under a name;
- `Http`: `bearer`, `basic` and the others HTTP defines;
- `Other`: OAuth 2, OpenID Connect or a client certificate, kept by name;
- `Unreadable`: a key with no name or no place, a reference to a scheme that is not there, or a
  chain of references that goes round in a circle, each saying why.

Only the first is sent. The others are kept as what they are, so that a key handed over for one of
them is refused with the reason, and so that a later version finds them in the model.

What an operation asks for is a `SecurityRequirement`: its alternatives in the document's order,
each the names of the schemes it needs at once.

Reading all of this never costs a document anything else. `SecurityConverter` catches everything,
and whatever it cannot make sense of becomes an unreadable scheme or no requirement, never an
exception that would take an operation down with it. A requirement naming a scheme the document
never declares is reported, once, where it is written. A scheme RESTest does not send is not a fault
of the document and is not reported. The requests of every document of the corpus that declares or
carries a key were pinned before any of this was read, and are unchanged
(`RequestsWithoutAKeyTest`).

One limit is inherited. The parser drops a `security: []` written for the whole document, so it reads
as the document saying nothing. Only rule 2b below is affected, and no document of the corpus writes
one.

### 2. How a key is handed over: `--auth`, or `RESTEST_AUTH`

One option, repeated for several keys. It is written in three ways:

```bash
restest run petstore.yaml --auth special-key            # the document declares one API key
restest run api.yaml --auth api_key=special-key         # it declares several: name the one
restest run languagetool.json --auth query:apiKey=k3y-4-lt   # it declares none: say where it goes
```

- **A key for a scheme** names the scheme before an `=`. The text is read that way only when what
  comes before the first matching `=` is a scheme the document declares:
  - the longest such name first;
  - otherwise the one scheme whose name matches without regard to capitals.

  So a key in Base64, which ends in `=`, is read as itself.
- **A key given with its place** begins `header:`, `query:` or `cookie:`, in any capitals, and then
  the name it goes under.
- **Anything else is a key on its own.** It answers the document's one API key, and is refused
  where the document declares none or several. It may hold `=` only at its end, as Base64 does: a
  text with an `=` that has anything else after it reads as a scheme's name and its key, and is
  refused when the name is none the document declares, so a scheme's name typed wrong is never sent
  as part of the key. A key that holds an `=` of its own is sent by naming its scheme first.

`RESTEST_AUTH` holds what one `--auth` holds, so that a key need not sit in a shell's history or in
the list of running processes. A key typed for the same place wins over it. An empty variable is how
a shell says a variable is not there for one command, and counts as none. A line break at the end of
the variable, as a file read into one often leaves, is not part of the key.

The option is named `--auth`, not `--api-key`, by the maintainer's choice on 30 September. The same
option is meant to carry the other credentials later, under the name of the scheme they answer, and a
file in the Web Fuzzing Commons format would come in beside it as `--auth-file`.

**A key typed that cannot be used ends the command with 2, before anything is sent, and the message
never repeats the key.** It names the key by where it came from: "the key given with the second
--auth". A key is refused when:
- it is on its own, and the document declares no API key or several (which are named);
- it has an `=` with more after it, and what comes before names no scheme the document declares;
- it is given with its place and has nothing after the name but `=`. A key in Base64 typed without
  the name it goes under reads that way, so the name, which is then most likely the key, is not
  repeated either;
- it is shorter than four characters. A key is hidden wherever it appears (section 6), and one that
  short would be hidden inside ordinary words and numbers everywhere the run writes;
- it runs straight on from `--auth`, with no space or `=` between them (`--authKEY`). This is
  checked before anything reads the command line, in the words of a file of arguments named with
  `@` as well, and nowhere after a `--`, where only the document's name can come. Read any other
  way, such an argument would be repeated whole: as an option that does not exist, or as the value
  of the option before it, even as the name of a directory to write into. The value of another
  option that begins with `--auth` goes after its `=`, as in `--out=--auth-results`;
- it names a scheme that is not an API key: "RESTest 2.0 sends only API keys";
- another key typed goes to the same place;
- it holds a character that is not plain printable ASCII, or a space at either end, which the HTTP
  client would silently trim, so that the key sent would not be the key given;
- it goes in a cookie and holds what a cookie cannot carry;
- it goes in a header, or a cookie, whose name is not one HTTP allows, or in a header the client
  writes itself;
- it appears inside the text written in its place (section 6);
- it goes in a header or a cookie while `engine.followRedirects` is on. The HTTP client carries
  every header but `Authorization` across a redirection to another machine, and nothing the run
  writes would show it.

A run of a protected API without the key somebody meant it to have would answer a question nobody
asked.

**A key left in `RESTEST_AUTH` that cannot be used is left out with a warning, and the run goes
on.** A variable can outlive the command it was set for, exported in a shell or by a script that
tests one API after another, and a key that fits nothing in a document is no reason not to test it.
A key that does fit is sent. RESTest cannot tell which API a key was meant for, so a variable holding
one is an instruction for every run it is set for, and the line before the first request names it
("the key in RESTEST_AUTH goes with …"). A script that tests several APIs sets the variable for each
run, `RESTEST_AUTH=… restest run …`, rather than once for all of them.

### 3. Which operations get a key, and where

Operation by operation, `CredentialPlan` decides:

1. **A key given with its place** goes with every operation, in that place.
2. **A key for a scheme** goes with an operation that asks for the scheme. What the operation asks
   for is its own requirement, or the document's where it has none:
   - `security: []` asks for nothing, and the key does not go;
   - of the alternatives, the first in the document's order that names at least one scheme and
     whose every scheme is an API key the run was given is the one taken;
   - an alternative that also needs something that is not a key cannot be completed, and nothing
     of it is sent;
   - a key given with the place a scheme's key goes counts as that scheme's key.

   2b. **A key the document declares and never asks for** goes with every operation that does not
   say `security: []`. BigOven and Tumblr are why: a document that bothered to declare a key meant
   it to be used. This departs from the letter of OpenAPI, which would send it nowhere.
3. **An input an operation declares under the key's name is filled with the key**, whatever the
   operation asks for:
   - in the same part of the request: a header named alike whatever its capitals, or a query
     parameter or cookie named exactly;
   - and, for a key that goes in the query, a field of the form the operation takes, which is
     LanguageTool's `apiKey`;
   - never a part of the path, which names what is asked for rather than who asks.

   Limited to the key's own part of the request, so that a query key called `key` does not end up
   in some unrelated field called `key`, where an API might store it and show it to others.
4. **Two different keys that would share one place of one operation** are refused.

### 4. What a key fills is taken away from what is invented

The part of the tool that invents values is handed the model without the inputs rule 3 fills, and it
is the only part that is (`CredentialPlan.modelToFillIn`). The oracles, the plan, the dictionaries
and what the run prints before it starts keep the document as it is.

For a field of a form, the following are taken out:
- the property;
- the requirement that it be there;
- the member, in every sample of a whole form the shape and the body write down, in its default and
  in its allowed values;
- one from the smallest and largest number of fields allowed, per field taken out, since the key's
  field is added back to every request.

A form whose shape is named, and referred to by that name, gets a copy of the shape without the
field, and so loses the name for the lists that are keyed by it. A choice between shapes loses the
field in each of them. Each named shape is copied once. One met again while it is still being
copied, in choices that lead back to one another, is left as it is: copied afresh each way round,
the work would multiply at every turn and a run would never start. The field stays in that one
shape, and the key added as the request leaves takes that field's place.

So nothing is invented for such an input, no change to an accepted request can pick it, and no
test case ever holds the key. With no key given, or none that fills an input, the model handed over
is the very same object, and every draw of every run is what it was.

A dictionary entry written for such an input loads and goes unused while a key fills it.

### 5. Added as the request leaves

`CredentialedEngine` stands in front of the HTTP engine, and only when a key was handed over. It adds
each key that goes with the request's operation as the request leaves, just as the engine adds the
tool's own name to it:
- **a header** of the key's name replaces any other of that name;
- **a query parameter** is written as the rest of RESTest writes an address, before any `#`, in
  place of any parameter of its name;
- **a cookie** goes into the request's one `Cookie` header exactly as given, since servers read a
  cookie back as it was sent;
- **a field** goes at the end of a form, in place of any field of its name.

A field is only added to a body that really is a form written from its fields. A body sent as text
of its own is sent as the change that made it made it: one broken on purpose, or labelled as a form
while holding JSON. So what a test case says it changed is what was changed. There is no field to
add where no body is sent, or where the operation also takes JSON and JSON is what is sent.

### 6. Hidden in everything the run writes

Every exchange passes through the same door on its way back, before anything else sees it:
- the console;
- `report.json` and its `curl` commands;
- the stored run;
- the rules that judge the reply;
- the memory of what the API returned;
- the series built around a thing the run created, which read the reply before the loop announces
  it.

It comes out with every appearance of every key hidden, under the same identity, from:
- the address, and the names and values of headers;
- the bodies of the request and the reply, as bytes, before anything trims them or writes them as
  Base64;
- the reason phrase of the status line;
- the reasons a request failed, since the HTTP client quotes the address, and the value of a header
  it refused, in the words of the exception it throws;
- every value, `sentAs` text, change description and step description the test case carries, and
  what its origins say, but never the names of its inputs, which a change could make clash.

A number whose digits hold a key becomes the text that replaces it, since a number cannot hold
letters.

**Every way the key could have been written is looked for:**
- as it is;
- percent-encoded, in capital and small hexadecimal;
- the way most servers write a web form;
- escaped inside a JSON string, with and without `\/`, and with every punctuation mark as a
  `\u` code;
- escaped for a web page.

**Not only whole keys.** Any piece of a key eight characters or longer, in any of those spellings,
is hidden wherever it is. A reply that repeats an address back may break it across two lines with
something else between the halves. WireMock's page for a request it does not recognise does exactly
that, and the leak test found it. At the very end of a reply the tool kept only part of, what is
left of a key is hidden from four characters.

Eight and four are safeguards of the kind ADR-0027 keeps in the code beside `breakAPattern`'s
sixteen, not settings. A piece of eight random characters turning up in ordinary text is next to
impossible, and a key broken in two leaves at most seven of its characters on either side of each
break.

**Everything is replaced in one pass.** The pass marks every stretch to replace on the text as it
arrived, merges the stretches that touch, and writes the text once. What was written in a key's
place is never looked into again, so nothing loops and no replacement is corrupted by another.

**What is written in a key's place names it.** It is what was typed after `--auth`, without the key:
- `REDACTED-AUTH` for a key on its own;
- `REDACTED-AUTH.api_key` for the scheme `api_key`;
- `REDACTED-AUTH.header.X-API-Key` for a key given with its place.

It holds only letters, digits and `- . _ ~`, any other character of a name becoming `_`
(`API Key` → `API_Key`). No two keys of a run share one: where two names come out the same, the
second gets `.2` after it, the third `.3`. The series read an identifier out of a `Location` header by parsing it as
an address, which angle brackets would break. The same text is safe in a header, a cookie, a form,
a JSON string and a single-quoted shell argument.

A `curl` command copied from a report shows it where the key went, and is run by putting the key
back:

```
curl -i -X GET 'http://localhost:8080/api/v3/store/inventory' -H 'Accept: application/json' -H 'api_key: REDACTED-AUTH'
```

**Hiding cannot fail.** An exchange that could not be passed on would be lost to every report. If
something goes wrong nonetheless, the exchange is passed on with everything that could hold a key
replaced whole: every header value, every body, the address, the test case's values. The run then
says, at the end, how many times that happened.

**What is not hidden:**
- a body the API compressed with something the tool never asked for;
- a body written in UTF-16;
- a key inside something else encoded, such as a JWT or a Base64 blob the API made;
- what the tool never wrote itself: the recording the benchmark's proxy keeps, a shell's history,
  the list of running processes;
- a key typed where another option's value goes, which that option's complaint repeats as it would
  any value. The command never repeats an argument it does not understand: it names an option that
  does not exist up to any `=`, and no further than `--auth` for one that begins with it. It refuses
  an argument that runs straight on from `--auth` before anything reads it. It takes whatever was
  typed after `--auth` out of any other complaint, as when an option missing its value is followed
  by `--auth=<key>`. And it does not repeat an address given with `--url` that begins with `-`,
  which is what the next argument becomes when the address is left out.

### 7. What the rules and the memory see

What they judge and learn from is what was hidden.

The rule that checks a reply against the document judges every reply as it always has. In a reply
holding the text written in a key's place, it lets pass what that text could have caused:
- an objection reached through a choice between shapes or a condition: `oneOf`, `anyOf`, `not`,
  `if`, `then`, `else`, `dependentSchemas`, `dependencies` and `discriminator`, and what `contains`,
  `minContains`, `maxContains`, `unevaluatedProperties` and `unevaluatedItems` decide from how other
  parts turned out. The shape the reply really has can fail on the replacement, and then another
  shape objects to parts of the reply the replacement never touched. A member that happens to be
  called by one of those words, such as a package's `dependencies`, is only a member;
- an objection to something under a member whose name holds the replacement;
- an objection to something holding the replacement that the replacement can change: a length, a
  pattern, a value from a list, or the names of an object's members when one of them holds it;
- a body that is not JSON, because a key was hidden inside a number.

Hiding a key never changes what kind of value something is, nor how many members or items there
are, nor a number. So, outside a choice, those objections stand, one item more than a list allows
among them, and so does anything wrong elsewhere in the same reply. An API that repeats the address back in every reply is judged on all
the rest of each one, short of what its choices decide. Reporting a fault the run itself caused is
the one thing a testing tool must not do.

At the end, a run says how many replies repeated a key back. They are not quite what the API sent,
and an API that hands a key back to whoever sent it is worth knowing about in itself.

The memory of observed values learns the replacement, never the key, so it can never send a key
somewhere else.

### 8. Said before the first request

Straight under the count of operations, one line per key handed over:

```
19 of 19 operations can be tested, seed 20260929, budget 30s
  the key given with --auth goes with 3 of them, in the header api_key; what the run writes says REDACTED-AUTH in its place
```

Where a key goes with no operation, the line says so and why.

Then one line per key the document asks for and nobody handed over, naming the option that would
give it:

```
  2 of them ask for an API key that was not given (api_key, in the header api_key): --auth <key> gives it
```

A key declared and asked for nowhere gets a line saying that, given, it would go with every
operation. It is said only when none of those keys was given. Only operations that can be tested are
counted, since those are what "of them" refers to.

A run whose key is missing is not refused. Anybody may be testing what an API does without one.

### 9. No switch

ADR-0025 asks for a switch on every lever. A key handed over is an instruction, not a lever: leave
it out, and nothing of this happens.

Three rules that come with it could be taken for levers of their own: rule 2b, rule 3's reach into
a form's fields, and the letting pass of what the hiding changed (section 7). None of them trades
one result for another that an experiment would weigh. Each says where a key given goes, or what is
not blamed on the API once one was given. Turned off, 2b sends BigOven's and Tumblr's key nowhere,
rule 3 sends LanguageTool a made-up `apiKey` in the form beside the real one, and section 7 reports
faults RESTest wrote itself. So none gets a switch, and each is named here for whoever disagrees.

### 10. Replaying a run, and judging it again

There is no `restest replay` in v2.0; it is row 3.5. When it comes:
- A stored run holds every request whole but the key, so replaying it needs the keys handed over
  again. Each replacement names the key it stands for, and no two keys of a run share one, so
  putting them back is not ambiguous. The longest replacement is put back first, since
  `REDACTED-AUTH` begins every other.
- The stored test cases never held a key at all, so a replay that rebuilds requests from them sends
  them through the same door, which adds the keys as the original run did.
- What is lost is a byte-for-byte replay from the file alone, and that is the point. The file can be
  shared or published, as 8.2 publishes its raw data, without the key going with it. A key that has
  been changed since is simply the one handed over.
- The `Content-Length` a stored request records is the one of the body with the key in it. Whoever
  sends it again works the length out afresh, as the `curl` command already leaves it out.

Judging a stored run again, `restest recheck` (3.3), gives the verdict the run gave: it judges the
same hidden replies.

### 11. The door the rest comes through

11.3 builds the pieces the rest of authentication fits into, and names none of them after keys:
- **A credential** is a `Place` and a `Secret`: a value, and the part of a request it travels in. A
  fixed header of Web Fuzzing Commons is one; the token a sign-in yields is one obtained rather than
  handed over, sent where `sendIn` and `sendName` say, inside `sendTemplate`.
- **`CredentialPlan`** says which credentials go with which operation: from the document's
  requirements, or with every request, which is what a WFC file asks of its fixed headers.
- **`CredentialedEngine`** adds whatever the plan says as the request leaves, and hides it in what
  comes back.
- **`Secrets`** is the set of values never written. A value obtained during a run joins it before
  its first use.

An HTTP bearer token or a user name and password, schemes of type `http`, come through the same
option under the scheme's name: `--auth bearerAuth=…`, `--auth basicAuth=ana:secret`. They are one
more kind of scheme, sent in `Authorization`, hidden the same way. The command line learns no second
vocabulary for them.

What does not fit through this door is a credential the tool has to obtain: a sign-in, OAuth 2's
client credentials, refreshing what expires. Nor does holding several users at once, one per test
case. Those are the rest of row 2.6, after v2.0, and a WFC file (`--auth-file`) is their format.
11.3 does not read one yet. WFC 0.7.0 has no fixed key in the query or a cookie, and no link to the
operations that need each credential, which are the two things a key needs.

## Consequences

- An API that asks for a key is tested past its check. `AuthCommandTest` shows the key reaching a
  stand-in that refuses every request without it, in a header, the query, a cookie and a form field,
  and never reaching an operation that asks for nothing.
- `AKeyIsWrittenNowhereTest` hands over three keys, one of them in the environment, to a stand-in
  that repeats every one back in every way it can. It then reads every file the run leaves behind,
  and everything it printed, byte by byte, for any spelling of any key and for their Base64. It finds
  none, and it checks that each key did reach the API and that what stands in for each is where the
  key went.
- The smoke job runs one more test in its plain Java runtime: the tool as its own process, the key in
  the real `RESTEST_AUTH`, against a stand-in that refuses without it.
- A run handed no key sends exactly the requests it sent before. The model handed to the generator
  is the same object, the engine is not wrapped, and the requests of every keyed document of the
  corpus are pinned.
- The store, `report.json` and the `curl` commands hold every exchange exactly as it went, except
  the keys. ADR-0005 and ADR-0006 are amended to say so.
- In a reply that repeated a key back, the rule that checks replies against the document lets pass
  what the hiding may have changed, and judges the rest, short of what its choices decide. A run
  says how many replies did. In a run
  handed no key nothing is hidden, and every reply is judged as before, unless an API writes
  RESTest's replacement text of its own accord.
- A dictionary entry for an input a key fills goes unused while a key fills it.

## Alternatives considered

- **The key as a setting.** Rejected for the reason in the context. Every setting is printed and
  recorded with where it came from, and a key must be neither.
- **The key in the plan.** Rejected: a plan is about an API and travels with it, while a key belongs
  to one deployment and one person.
- **A placeholder in the test case, swapped for the key as the request leaves.** Every part of the
  tool that builds, changes or remembers a test case would then carry a value it must not change, and
  a text that happened to look like the placeholder would be sent as the key. Leaving the input out
  of the test case needs nothing from any of them.
- **Hiding by the names of headers**, as Schemathesis does. It misses a key in the query, a body, or
  a reply that repeats it. The tool knows the values it was handed, so it hides those, wherever they
  appear.
- **Hiding in each place that writes**: the console, the report, the store. Every new writer would
  have to remember, and the memory and the series read replies before any writer does. One door
  before all of them cannot be forgotten.
- **Sending every key with every request**, as most related tools do. It is simpler, and it sends a
  key to operations the document says need none, and one key where another was asked for. The
  document's own requirements are there to be read; a key given with its place does go everywhere.
- **Reading a WFC file now.** It would read fixed headers and nothing else, which
  `--auth header:<name>=<value>` already does. *Amended at M12.1a: this once said 12.1's `--header`
  would, and 12.1a added none, for the reason ADR-0015's amendment of that name gives.* The part of WFC that matters, signing in and users, is the rest of 2.6.
- **`--api-key`, or `--credential`, as the option's name.** `--api-key` reads best today and wrongly
  the day a bearer token comes through it. `--credential` is general and long. The maintainer chose
  `--auth` on 30 September.
- **Refusing a run whose document asks for a key it was not given.** Rejected: testing an API without
  its key is a legitimate thing to want, and the line before the first request already says what
  would change it.
- **A switch.** Rejected in section 9.
