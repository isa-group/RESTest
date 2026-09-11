# RESTest 2.0

> **This branch (`v2`) is a complete rewrite.** It is not backwards-compatible with RESTest 1.x.
> The 1.x code and documentation remain on `master`.

A black-box testing tool for REST APIs. Provide an OpenAPI specification; RESTest generates and
executes test cases and reports the failures it finds — with zero configuration to get started.

## Status

Active development. See `ROADMAP.md` for the milestone plan and `docs/PROPOSAL.md` for the
full design rationale.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Building

Requires JDK 21 or later and Git.

```bash
git clone https://github.com/isa-group/RESTest.git
cd RESTest
git switch v2
./mvnw verify
```
