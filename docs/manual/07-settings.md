# Settings

A **setting** is a number, or a yes or a no, that says how the tool behaves: how many requests it
keeps in flight, how long it waits for an answer, how much of a reply it keeps, whether it does a
given thing at all. You never need to change one. When you do, it is one line, and nothing is
rebuilt. Every setting, with its default and what it does, is in [The settings](../settings.md).

## Settings and plans

Settings and plans are two different files, and the difference is worth having clear. A plan
([chapter 5](05-plans.md)) is about **the API**: where values come from, which operations to touch.
A setting is about **the tool**: it would mean the same against any other API on the same machine.
Twenty requests in flight at once suits a fast API on a big machine and none on a laptop, whatever
the API is.

## Seeing them

```bash
./restest run --print-settings
```

```
TO BE RUN: ./restest run --print-settings (the first lines)
```

Every setting is printed, grouped, with a line saying what it does and a note saying where its value
came from. The output is itself a settings file: save it, change a line, and hand it back.

## Changing them

There are three ways, and a setting given in more than one place takes the last of these:

1. **A file**, named with `--settings`. A file holds only what you change:

   ```yaml
   engine:
     maxConcurrency: 1
   ```

   ```bash
   ./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --settings settings.yaml
   ```

2. **An environment variable**, the setting's name in capitals, with `RESTEST_` in front and an
   underscore wherever the name has a capital of its own. This is how a container is configured:

   ```bash
   RESTEST_ENGINE_MAX_CONCURRENCY=1 ./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s
   ```

3. **`--set`** on the command line, as often as you like:

   ```bash
   ./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --set engine.maxConcurrency=1
   ```

A run with any setting other than RESTest's own says so under its first lines, so that a variable
set in the environment and forgotten does not go unnoticed. `report.json` lists every setting the run
used and where each came from.

## The ones most often changed

**An API that falls over when asked two things at once.** RESTest keeps up to sixteen requests in
flight, and slows down by itself when the API does. An API that cannot take even that, or one you do
not want loaded, gets one request at a time with `--set engine.maxConcurrency=1`.

**A slow API.** RESTest waits thirty seconds for an answer once connected. For an API that takes
longer, `--set engine.readTimeout=2m`. Lengths of time are written `500ms`, `30s`, `5m`, `2h`, or as
a plain number of seconds.

**A reply that is too large to keep.** RESTest keeps the first megabyte of any reply, which is enough
to judge it by. `engine.maxRetainedResponseBytes` changes that.

## Switches

Some settings are `true` or `false`, and each turns off one thing the tool does: the opening lap,
one kind of change to an accepted request, one kind of series. They are there so that something an
API dislikes can be turned off in a line, and so that what each idea is worth can be measured by
running the same tool with it and without it. A run without the opening lap:

```bash
./restest run http://localhost:9966/petclinic/v3/api-docs --budget 30s --set schedule.openingLap=false
```

[The switches](../switches.md) lists every one, says what turning it off does, and gives files that
turn off a whole idea at once — among them the one that, with a plan, makes `--seed` repeat a run
exactly.

## What is not a setting

The **budget**, the **seed**, the **output directory** and the **key** an API asks for are options
of the command, not settings: they belong to one run, not to a machine. The key in particular is
never printed by `--print-settings` or written into `report.json`, which is what would happen to a
setting. [The next chapter](08-an-api-that-asks-for-a-key.md) is about it.
