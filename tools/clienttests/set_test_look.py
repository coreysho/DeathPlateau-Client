"""Set a local test account's appearance by editing its save, since nothing else can reach it.

The character design screen is the only in-game door to hair, top and legs, and it only opens at
login for a player at the very start of the tutorial. This writes the same seven kit choices and
five colours straight into the .sav, which on a throwaway local account is the shorter way round.

SAVE LAYOUT (Engine-TS Player.save): magic u2, version u2, x u2, z u2, level u1,
body[7] u1 (hair, beard, torso, arms, gloves, legs, boots), colors[5] u1, gender u1, ...

  python3 setlook.py <Engine-TS dir> <username> hair=2 torso_colour=1 legs_colour=2
"""
import os, sys

eng, user = sys.argv[1], sys.argv[2].lower()
args = dict(a.split('=', 1) for a in sys.argv[3:])

path = os.path.join(eng, 'data', 'players', 'main', user + '.sav')
if not os.path.exists(path):
    raise SystemExit(f'no save at {path} - log the account in once first')

b = bytearray(open(path, 'rb').read())
BODY = 9           # hair, beard, torso, arms, gloves, legs, boots
COLOR = 16         # hair, torso, legs, boots, skin
slot = {'hair': BODY + 0, 'beard': BODY + 1, 'torso': BODY + 2, 'arms': BODY + 3,
        'gloves': BODY + 4, 'legs': BODY + 5, 'boots': BODY + 6,
        'hair_colour': COLOR + 0, 'torso_colour': COLOR + 1, 'legs_colour': COLOR + 2,
        'boots_colour': COLOR + 3, 'skin_colour': COLOR + 4}

print(f'{user}: body={list(b[BODY:BODY+7])} colours={list(b[COLOR:COLOR+5])}')
for k, v in args.items():
    if k not in slot:
        raise SystemExit(f'unknown field {k}; one of {sorted(slot)}')
    b[slot[k]] = int(v)

# THE SAVE IS CRC'd and the last four bytes are it (Player.save: p4(Packet.getcrc(...)) over
# everything before them). Edit a byte without this and the login server answers "Could not
# complete login", which is not a word about appearance and cost a run to work out.
import zlib
crc = zlib.crc32(bytes(b[:-4])) & 0xFFFFFFFF
b[-4:] = crc.to_bytes(4, 'big')
open(path, 'wb').write(bytes(b))
print(f'   -> body={list(b[BODY:BODY+7])} colours={list(b[COLOR:COLOR+5])}')
