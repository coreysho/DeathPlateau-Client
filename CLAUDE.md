# DeathPlateau Client

Java game client (2006 era), launcher and plugin API/hub. The server is DeathPlateau-Engine,
content is DeathPlateau-Content, and bugs and decisions live on DeathPlateau-Bugs.

## Working rules (all DeathPlateau repos)

- Write a short plain-language TLDR for the owner at the top of every PR and report.
- Game behaviour follows 2006 RuneScape where it is documented; otherwise follow Old School.
  Say which source you used in the PR.
- Merge order across repos: Content, then Engine, then Client.
- Merge a PR only when every check is green. Never skip, disable or loosen a check to get green.
- Post owner decisions on the tracker issue, first line bold and verbatim:
  `**Decision from the owner, 24 Nov 2026: "<owner's words>"**`
- Claim a tracker issue with the `claude-working` label before working it; remove it when you stop.
- Use subagents for read-heavy sweeps.

## Branches and releases

`dev-logging` is the default branch. Every push to it publishes a client release to players,
so merge only finished, green work.

## Layout

- `src/main/java/jagex2/` is the client; the plugin API is `jagex2/client/plugin/`, the hub is `plugin/hub/`.
- `launcher/` is the launcher (also compiled into the client jar).
- `plugins/` has example plugins and the plugin API docs (`plugins/README.md`).
- `tools/clienttests/` holds the test suites; `tools/worldmap|clues|slayer/` generators read `../DeathPlateau-Content`.

## Build and test

Source level is Java 8. The Gradle wrapper alone sets the Gradle version.

```bash
./gradlew build
./gradlew proguard
python3 tools/clienttests/run_<name>test.py
python3 tools/clienttests/mutate_guard.py
```

- Test exit code 2 means skipped. Several suites need sibling checkouts `../Engine-TS` (with a packed cache) and `../DeathPlateau-Content`.
- A new `mutate_*.py` must also be added to the nightly matrix in `.github/workflows/mutations-nightly.yaml`.
- Refresh the bundled quest helper table by copying the engine's `data/quests.json` over the committed copy;
  commit it as `chore(quests): refresh the bundled helper table ...`.

## Conventions

- PR titles are Conventional Commits: `fix(scope): plain-English outcome`.
- Cite tracker issues as `DeathPlateau-Bugs#N`.
- Java files use tabs, LF endings; `.bat` files use CRLF.

## Security

- Keep launcher and plugin hub download URLs pointing at this repo's releases and the plugins index. Change them only when an issue asks for it.
- Never commit keystores, signing keys, tokens, built jars or logs.
- Read secrets only from GitHub secrets. Never write a secret value into code, workflows, PRs, issues or logs.
- Do not add hostnames, IP addresses or server paths to code, commits, PR text or issues unless an issue asks for it.
