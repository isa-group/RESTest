# RESTest documentation

One page per subject. The [README](../README.md) at the root of the repository is where to start:
it installs the tool and makes a first run. These pages are what to look things up in afterwards.

## Using RESTest

| Page | What it covers |
|---|---|
| [The command line](command-line.md) | Every command, every option of a run, the environment variables, how to stop a run, and the number a run ends with |
| [What a run leaves behind](report.md) | Every line a run prints, every key of `report.json`, and the stored run `--store` keeps |
| [The faults RESTest reports](faults.md) | The kinds of fault a run reports, what each means, and how they are counted |
| [The campaign file](campaign-format.md) | The plan a run follows: where its values come from, how much of it pushes at the API, which operations it may touch |
| [The dictionary format](dictionary-format.md) | Lists of values of your own, handed over with `--dictionary` |
| [The settings](settings.md) | Every number that says how the tool behaves, with its default, and the four ways to change one |
| [The switches](switches.md) | The settings that turn one thing a run does off, and what each was found to be worth |

## Building RESTest

| Page | What it covers |
|---|---|
| [Design](DESIGN.md) | What the tool is, how it is put together, the rules the build enforces, and a glossary |
| [Continuous integration](ci.md) | What every pull request is checked for, and how to run the same checks locally |
| [Decision records](adr/README.md) | Why the tool is shaped the way it is, one decision per file |
| [Contributing](../CONTRIBUTING.md) | How a change is proposed, built and reviewed |
