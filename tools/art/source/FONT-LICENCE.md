# Curse of the Zombie — licence

`curse-of-the-zombie.ttf` is used to render `src/main/resources/deathplateau/wordmark.png`
via `tools/art/make_wordmark.py`. The rendered PNG ships inside the client jar, which is
distributed to players.

**The font's own metadata reads `Free for personal use ONLY`**, in name-table record 13, which
is where a font states its terms. That string travels with the file whether or not anyone reads
it.

A distributed game client is not personal use, so shipping the wordmark needs a commercial
licence. **One was obtained by the project owner on 2026-10-19.**

Keep the purchase record beside this file, or note where it is:

    licensee:   [WHO IT WAS BOUGHT UNDER]
    vendor:     [WHERE FROM]
    reference:  [ORDER OR LICENCE NUMBER]
    scope:      [WHAT IT COVERS - seats, distribution, redistribution of rendered artwork]

Worth checking `scope` covers *redistributing rendered output*, not just using the font on a
machine, since that is what the client does.

## If the licence ever lapses, or the face is replaced

Nothing in the client needs changing. Drop the new `.ttf` in beside this file under the same
name, re-run `python3 tools/art/make_wordmark.py`, and commit the regenerated PNG. The client
loads a picture and knows nothing about fonts.
