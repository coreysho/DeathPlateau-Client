#!/usr/bin/env python3
"""Is the engine this suite is about to render against the one on this disk?

SIX SUITES CONNECT TO AN ENGINE THEY DID NOT START. run_coursecam, run_wearshots, run_zulrahshots,
run_weaponanim, run_resizableshots and run_worldmaporb all document the command and then connect to
port 43694, which is the right arrangement - a world takes half a minute to start and a shot takes
seconds, so one engine serves a morning of runs. What they could not do is tell the difference
between these three:

    nothing is there                 the client cannot connect, and the failure says so
    an engine whose pack is broken   the client gets a 404 for an archive and dies at 30%
    an engine whose pack is STALE    the client loads a WORLD THAT IS NOT THE ONE ON DISK

The last one is the dangerous one, because it does not fail. The engine reads its cache once at
startup; rebuild the pack underneath it and it keeps serving what it already has, happily, for as
long as it is up. A suite rendering against it takes correct-looking screenshots of the previous
build - and those get compared against expectations, approved, and committed.

The second one cost most of 2 November. Two builds had interleaved their blocks in
main_file_cache.dat, so the engine could not read config or interface and served a crc of 0 for
each; the client asked for /config0, got a 404, and sat at 30% on "Connection error". Four runs of
another suite went looking in the engine, the ports and the client's own fallback before anybody
read the four bytes the cache was returning.

HOW THIS KNOWS. The engine publishes the crc table it will serve, at /crc<anything>-377, and that
table is a crc32 of each archive exactly as the cache holds it. So the same archives can be walked
out of main_file_cache on disk, crc'd, and compared - no engine cooperation needed beyond the reply
it already gives every client. A 0 in the table is an archive the engine cannot read at all; a
difference is an engine serving something else.

    python3 tools/clienttests/enginecheck.py                    # the usual 43694 / 8694
    python3 tools/clienttests/enginecheck.py 43594 8080         # somewhere else
"""
import os
import socket
import struct
import sys
import urllib.error
import urllib.request
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ENGINE = os.path.join(os.path.dirname(ROOT), 'Engine-TS')

# idx0, in the order the client asks for them. File 0 is unused.
ARCHIVES = ('title', 'config', 'interface', 'media', 'versionlist', 'textures', 'wordenc', 'sounds')

BLOCK = 520
HEADER = 8


def listening(port, host='127.0.0.1'):
    """Whether anything answers on this port.

    A connect, not a listener table: netstat and Get-NetTCPConnection both came back empty on one
    machine while two engines were plainly serving.
    """
    with socket.socket() as s:
        s.settimeout(1.5)
        return s.connect_ex((host, port)) == 0


def served(webport, host='127.0.0.1'):
    """The crc table the engine will hand the next client, or None if it will not say.

    /crc<n>-377 is what the client itself fetches before anything else, so this asks exactly what a
    client asks and gets exactly what a client would get.
    """
    try:
        with urllib.request.urlopen('http://%s:%d/crc0-377' % (host, webport), timeout=10) as r:
            body = r.read()
    except (urllib.error.URLError, OSError):
        return None
    if len(body) < 36:
        return None
    return list(struct.unpack('>9i', body[:36]))


def u24(b, i):
    return (b[i] << 16) | (b[i + 1] << 8) | b[i + 2]


def on_disk(engine=ENGINE):
    """The same crcs, from the cache in the engine's data/pack, or None if there is no cache there.

    The archives are walked out of the .dat the way the client reads them - every block carries the
    file and chunk it belongs to - so a chain that does not deliver reads as nothing here too,
    rather than as a wrong answer.
    """
    pack = os.path.join(engine, 'data/pack')
    try:
        with open(os.path.join(pack, 'main_file_cache.dat'), 'rb') as f:
            dat = f.read()
        with open(os.path.join(pack, 'main_file_cache.idx0'), 'rb') as f:
            idx = f.read()
    except OSError:
        return None

    crcs = [0] * 9
    for fid in range(1, min(9, len(idx) // 6)):
        size, block = u24(idx, fid * 6), u24(idx, fid * 6 + 3)
        if size == 0 or block == 0:
            continue
        out, chunk, ok = bytearray(), 0, True
        while len(out) < size and ok:
            if block == 0 or (block * BLOCK) + HEADER > len(dat):
                ok = False
                break
            off = block * BLOCK
            hfid, hchunk = struct.unpack('>HH', dat[off:off + 4])
            if (hfid, hchunk, dat[off + 7]) != (fid, chunk, 1):
                ok = False
                break
            want = min(BLOCK - HEADER, size - len(out))
            out += dat[off + HEADER:off + HEADER + want]
            block = u24(dat, off + 4)
            chunk += 1
        if ok and len(out) == size:
            crcs[fid] = struct.unpack('>i', struct.pack('>I', zlib.crc32(bytes(out))))[0]
    return crcs


def complaint(port, webport, engine=ENGINE, host='127.0.0.1'):
    """One sentence about why this engine is not fit to render against, or None if it is.

    Written to be printed and acted on: every case says what to do, because the whole point is that
    none of these is guessable from what the client does when it hits them.
    """
    if not listening(port, host):
        return ('no engine on %s:%d - start one with "NODE_PORT=%d WEB_PORT=%d '
                'WEB_MANAGEMENT_PORT=%d npx tsx src/app.ts" in Engine-TS'
                % (host, port, port, webport, webport + 100))

    table = served(webport, host)
    if table is None:
        return ('the engine on %s:%d is not serving its cache on %d - it may still be starting, '
                'or WEB_PORT is not what this suite expects' % (host, port, webport))

    missing = [ARCHIVES[i - 1] for i in range(1, 9) if table[i] == 0]
    if missing:
        return ('the engine on %s:%d cannot read %s out of its pack, so the client will get a 404 '
                'and stop at 30%%: delete Engine-TS/data/pack and build again'
                % (host, port, ', '.join(missing)))

    disk = on_disk(engine)
    if disk is None:
        # No cache to compare against - a checkout with no engine beside it, or an engine that has
        # never built. Everything above still held, so this is not a refusal.
        return None

    stale = [ARCHIVES[i - 1] for i in range(1, 9) if disk[i] != 0 and disk[i] != table[i]]
    if stale:
        return ('the engine on %s:%d is serving %s from a pack that is no longer on disk - it was '
                'started before the last build, so every shot would be of the PREVIOUS one: '
                'restart it' % (host, port, ', '.join(stale)))
    return None


def require(port, webport, engine=ENGINE, host='127.0.0.1', suite=None):
    """complaint(), printed and exited on. 2, because "could not run" is not a failing render."""
    why = complaint(port, webport, engine, host)
    if why:
        print('%s: %s.' % (suite or os.path.basename(sys.argv[0]), why))
        sys.exit(2)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 43694
    webport = int(sys.argv[2]) if len(sys.argv) > 2 else 8694
    why = complaint(port, webport)
    print(why + '.' if why else 'the engine on %d is up, serving, and current with data/pack' % port)
    return 1 if why else 0


if __name__ == '__main__':
    sys.exit(main())
