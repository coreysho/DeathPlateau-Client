# Plugins

A plugin is a client feature in its own jar. It is loaded at startup, turned on and off from a
panel in game, and can be added or updated without rebuilding the client.

The API is shaped after RuneLite's on purpose - `Plugin` with `startUp`/`shutDown`, `@Subscribe`
event handlers, overlays, `@ConfigItem` settings - so what you know from writing one there carries
over. It is **not** RuneLite and is not compatible with it: RuneLite's API describes the modern
Old School client, and this one describes a 2006 client with a different cache, different
interfaces and different ids. Porting a plugin means keeping its logic and replacing its ids and
widget lookups.

## Installing one

**From the hub.** Open the sidebar's second tab (the download arrow), find the plugin, press
Install. It downloads, is checked, and appears in the plugin list ready to switch on. See
[The hub](#the-hub) for what it does and does not guarantee.

**By hand.**

1. Put the jar in `~/.deathplateau/plugins` (`%USERPROFILE%\.deathplateau\plugins` on Windows),
   beside the `client.jar` the launcher keeps there. The client creates the folder on first run.
2. Start the client. The plugin sidebar is down the right-hand side of the window.
3. Flip the switch on the plugin's row. It stays on next time.

**F8** shows and hides the sidebar.

**A new plugin starts switched off, and that is deliberate.** A plugin is ordinary Java running
inside your client, with your client's access to your filesystem and your connection. There is no
sandbox. Only install plugins whose source you can see and whose author you trust.

The reload button next to the search box re-reads the folder, so a plugin can be rebuilt and
picked up without restarting the client.

## The hub

The second sidebar tab lists plugins from an index and installs them, so a plugin arrives by
pressing a button rather than by finding a folder.

The index is read from
`https://raw.githubusercontent.com/coreysho/DeathPlateau-Plugins/main/index.json`, overridable
with `-Dlostcity.pluginindex=<url>` (or `LOSTCITY_PLUGININDEX`). Until that repo exists the hub
says so and shows the address it tried.

### Publishing plugins

Make a repo with an `index.json` at its root - `plugins/index.example.json` in this repo is a
filled-in sample with every field explained. Put the jars wherever the index can point at over
http or https; release assets are the usual answer, because their urls are stable.

Updating a plugin is: upload the new jar, bump its `version` and `sha256` in the index. Every
client sees the update the next time it opens the hub.

Give every entry a `clientApi` - see [Client API levels](#client-api-levels). It is what stops a
player on an older client installing a jar that cannot run there.

### What the hub checks, and what it does not

Before a downloaded jar is moved into the plugins folder:

- the url is http or https, so an index cannot point the client at its own disk
- the id is letters, digits, dash and underscore, so an index cannot write outside the folder
- the download is capped at 32MB
- if the index gives a `sha256`, it must match
- the file must open as a jar

A failed install leaves whatever was there working: the download lands in a temp file and is only
moved over once it has passed all of the above.

**None of that makes a plugin safe.** A plugin is ordinary Java running in the client's process
with the client's access to your machine - there is no sandbox. A checksum proves the jar is the
one the index meant, not that the jar is harmless. Install what you trust, from people you trust.

An index served over plain http is worth less than one over https: anything that can rewrite the
index can rewrite the checksum in it. The hub marks plugins whose jar is plain http, and the
default index address is https.

## The sidebar

A Swing panel beside the game, like RuneLite's, added to the window rather than drawn over the
game - the game keeps whatever size its display mode gives it and the window gets wider. In the
resizable modes the window's minimum grows by the sidebar's width, so the game is never squeezed
below its own minimum.

A rail of icons down its outer edge is the way in: the plugin list, the hub, and one per plugin
that has asked for a page. Clicking a different icon switches page; clicking the open one folds
the page away and leaves the rail, so the window goes back to the width of the game without
losing the way back. F8 and the title bar's chevron hide the lot.

### A page of your own

`addPanel(title, icon, rows)` in `startUp()` puts an icon on the rail and a page behind it. The
page is a `ConfigList` - the same rows the config page uses, with two more things a readout
wants:

```java
this.addPanel("Loot nearby", "list", new ConfigList() {
    public int size()            { return nearby().size(); }
    public String label(int i)   { return nearby().get(i).name; }
    public String detail(int i)  { return nearby().get(i).tiles + " tiles away"; }
    public String value(int i)   { return money(nearby().get(i).worth); }   // right-hand column
    public int progress(int i)   { return -1; }                             // 0-100, or -1 for no bar
});
```

| What | Where it goes |
| --- | --- |
| `label(i)` | The row's name. |
| `detail(i)` | A dimmer second line under it. |
| `value(i)` | A reading in a column on the right, where the eye can run down it. |
| `progress(i)` | 0 to 100, drawn as a bar under the row. -1 for none. |
| `action(i)` / `onAction(i)` | A button on the row. |
| `emptyMessage()` | Shown instead of the rows when there are none. Say what would put something there. |

**You hand over rows, not components.** There is no way to put a Swing component of your own in
the sidebar, and that is deliberate: a jar from the hub showing a readout should not also be
holding the event thread with a blank rectangle to do as it likes with. The cost is real - if
the list above has no word for what you want to show, you cannot show it. Ask for a new kind of
row rather than working around it.

**The icon is named, not supplied**: `"chart"`, `"list"`, `"wrench"`, `"download"`, `"refresh"`.
An unknown name gets the plain one. Plugins do not ship artwork into the client's own furniture,
and the rail stays one set of icons rather than a row of everybody's.

**It is read on the game thread** and re-read while it is open, so a readout is never frozen at
what it said when you opened it. Write `size()` and the rest as plain reads of your own state;
nothing needs locking.

**Guard it if you publish to the hub.** `addPanel` arrived in a client newer than some players
are running, and calling a method their client does not have throws `NoSuchMethodError` - which
the manager answers by switching your whole plugin off. See the note in the hub repo's README;
`XpTrackerPlugin` does it.

The applet has no window to put a panel in, so there F8 opens a simpler list drawn inside the
game viewport instead. It can toggle plugins and their on/off settings, but it has no text boxes,
so numbers and names there are edited in `plugins.dat` by hand.

## The examples

`plugins/src` has two, which between them use most of the API:

- **Coordinates** - the smallest useful plugin: a descriptor, two settings, one overlay.
- **XP tracker** - events, per-session state, a hotkey, a multi-line overlay.

Build them:

```sh
./gradlew examplePlugins
cp build/plugins/example-plugins.jar ~/.deathplateau/plugins/
```

They appear in the sidebar; flip their switches on.

## Writing one

A plugin needs the client on its compile classpath (the client jar, or `build/classes/java/main`)
and nothing else.

```java
package example;

import jagex2.client.plugin.*;
import jagex2.client.plugin.event.*;

@PluginDescriptor(name = "Tick counter", description = "Counts game ticks", key = "tickcounter")
public final class TickCounterPlugin extends Plugin {

    @ConfigItem(keyName = "showTotal", name = "Show the total")
    public boolean showTotal = true;

    private int ticks;

    @Override
    protected void startUp() {
        this.ticks = 0;
        this.addOverlay(new Overlay() {
            public void render(OverlayGraphics g) {
                if (showTotal && ctx.isLoggedIn()) {
                    g.text(6, 300, "ticks: " + ticks, 0xFFFF00);
                }
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        this.ticks++;
    }
}
```

Pack it with a `Plugin-Class` manifest attribute naming your plugin classes, comma separated:

```
Plugin-Class: example.TickCounterPlugin
```

Without it every class in the jar is checked instead, which also works and is just slower.

### Rules

- **Handlers run on the client thread, inside the game loop.** Whatever yours does, the frame
  waits for it. No IO, no sleeping, no long loops.
- **`shutDown()` undoes `startUp()`.** A player can toggle a plugin repeatedly in one session, and
  a plugin that is off must leave nothing behind. Overlays are removed for you.
- **Throwing is survivable but not free.** An exception is caught and logged; five in a row from
  the same handler or overlay and the client turns your plugin off and says so in chat.
- **Settings are fields.** Tag a `boolean`, `int` or `String` field with `@ConfigItem` and it is
  loaded on startup and saved when changed. Booleans are clickable in the panel; ints and Strings
  are text boxes in the sidebar. Never rename a `keyName` after release - it resets that setting
  for everyone.
- **Lists are not settings.** A `@ConfigItem` is one value the player chooses. For rows the
  plugin accumulates - the swaps you have set, the items you are hiding - add a `ConfigList` in
  `startUp()`. The config page lists them with a button to cycle a row and one to remove it.
  There is no "add" field, because those rows are made in game.

## The API

Everything is in `jagex2.client.plugin`.

| Class | What it is |
| --- | --- |
| `Plugin` | What you extend. `startUp`, `shutDown`, `addOverlay`, `ctx`, `config`. |
| `PluginContext` (`ctx`) | What you may read and do: position, skills, the right-click menu, world-to-screen projection, a chat message. |
| `Overlay` | What you draw. `render(OverlayGraphics)`, `priority()`. |
| `OverlayGraphics` | Text, rectangles, lines and a ready-made `panel(...)`, in viewport coordinates. `width()` and `height()` are the drawable area for this frame - ask each frame, since the display mode changes it. |
| `@PluginDescriptor` | Name, description, saved key and `apiLevel`. |
| `PluginApi` | `PluginApi.LEVEL` - what this client's API can do, as a number. See below. |
| `@ConfigItem` | A player-facing setting. |
| `@Subscribe` | Marks an event handler. |
| `ConfigList` | Rows the plugin produces, on its config page or on a page of its own. Read on the game thread, drawn on the UI thread. |
| `Plugin.addPanel` | Gives the plugin an icon on the sidebar's rail and a page behind it. See [A page of your own](#a-page-of-your-own). |
| `Sprite` | An image from the cache, such as `ctx.getSkillIcon(skill)`, drawn with `g.sprite(...)`. |

Events, in `jagex2.client.plugin.event`:

| Event | When |
| --- | --- |
| `GameTick` | A server cycle, about every 600ms. The clock for game logic. |
| `ClientTick` | A client frame, 50 a second. |
| `ChatMessage` | Any line arriving in the chatbox, whoever sent it. |
| `StatChanged` | A skill's level or experience changed. Carries the amount gained. |
| `MenuBuilt` | The right-click menu is built and can be read or reordered. |
| `MenuOptionClicked` | An option was clicked, before anything is sent. Can be consumed. |
| `KeyPressed` | A key, before the game sees it. Can be consumed. |
| `GameStateChanged` | Logged in, or back at the login screen. |

`PluginContext` is deliberately small - it is the promise the client keeps, and everything behind
it is free to change. If what you need is not there, add a method that answers the question you
are really asking rather than reaching past it into `Client`.

### Client API levels

A plugin is compiled against the client, so a jar built against a newer client than the one
running it is a jar calling methods that are not there. That does not fail politely: the first
call throws `NoSuchMethodError` mid-frame, and a plugin that keeps throwing gets turned off. The
player sees a plugin that installed cleanly and then quietly stopped working.

So the API has a level. `PluginApi.LEVEL` is what the running client provides, and a plugin says
what it needs:

```java
@PluginDescriptor(name = "My tool", apiLevel = 1)
public class MyToolPlugin extends Plugin {
```

| Level | What it includes |
| --- | --- |
| 0 | Does not say. The default, and never refused - every plugin written before levels existed. |
| 1 | Overlays, config items, config lists with a value and a progress bar, `addPanel`, and `PluginContext` as it was then. |
| 2 | `ctx.notify`, `ctx.playSound`, `ctx.hasSound`. |
| 3 | The cursor: `ctx.getMouseX`, `ctx.getMouseY`, `ctx.getHoverTileX`, `ctx.getHoverTileZ`, `ctx.sceneToWorldX`, `ctx.sceneToWorldZ`. |
| 4 | Who else is in the scene: `ctx.getNpcs`, `ctx.getPlayers`, and the `Actor` they hand back. |
| 5 | Richer editors for a String setting: `@ConfigItem(colour = true)` and `@ConfigItem(choices = {...})`, plus `PluginConfig.parseColour` / `toHex`. |
| 6 | Restyling the right-click menu: `ctx.setMenuColour`, `ctx.deprioritiseMenuEntry`, `ctx.isGroundItemTake`. |
| 7 | `ctx.isMenuOpen`, for an overlay near the cursor that should stand aside while a menu is open; `OverlayGraphics.fontFor` with `FONT_CHOICES`. |
| 8 | `ctx.getTrueTileX` / `getTrueTileZ`: the tile the server has the player on, which during a walk is ahead of the one they appear to stand on. |

Note what did **not** move the level: Alt-drag arrived between 1 and 2 and a plugin calls nothing
for it - overlays became movable underneath them. A level only goes up when there is something new
to **call**. It is a promise to a compiler, not a changelog.

The number only goes up, and only when something is **added**. A level is a promise that
everything up to it is present, so nothing in one may ever be removed or change meaning.

Say the level that added what you call, not the newest one going: `apiLevel = 2` on a plugin that
only needs level 1 features locks out clients that would have run it perfectly.

A client below the declared level refuses the plugin before constructing it, and the plugin panel
shows it greyed out with the reason. A jar that declares nothing still gets caught by a net - a
`LinkageError` out of the constructor or `startUp` is reported as "built for a different client"
rather than as a mystery - but the net only fires once the jar is already on the player's disk.
The index's `clientApi` is what keeps it from getting there.

## Settings a player can edit

A `@ConfigItem` field gets an editor chosen by its type:

| Field | Editor |
| --- | --- |
| `boolean` | a switch |
| `int` | a numeric box |
| `String` | a text box |
| `String` with `colour = true` | a swatch that opens a colour picker |
| `String` with `choices = {...}` | a drop-down of exactly those values |

```java
@ConfigItem(keyName = "colour", name = "Marker colour", colour = true)
public String colour = "00FF00";

@ConfigItem(keyName = "tagPosition", name = "Where the name sits",
    choices = { "Above", "At feet" })
public String tagPosition = "Above";
```

**A colour is still stored as hex**, `"00FF00"` and not a packed int. That is what makes
`colour = true` safe to add to a setting that already shipped: the file keeps its contents, every
`PluginConfig.parseColour` call keeps working, and a player who typed a colour by hand keeps what
they typed. Only the editor changes. Read it with `PluginConfig.parseColour`, which is the one
place that conversion lives - never write your own, because the swatch and your drawing have to
agree.

**Nothing in a settings file may make a render throw.** `parseColour` answers yellow for anything
that is not six hex characters - a typo, a pasted `#`, an empty string from a release where the
field did not exist. Yellow rather than black, because black on the scene layer reads as "the text
did not draw" while yellow reads as "something is set wrong", which it is.

**A drop-down keeps a value it does not recognise.** If a later release drops a choice, the panel
still offers what the player has alongside the real ones. Silently rewriting someone's settings
because they opened a panel is worse than showing them something odd.

Longer lists - rules per item, things a player adds and removes - are a `ConfigList` instead; see
*A page of your own*. The panel scrolls, so a list is not capped by the height of anything.

### Ground items, in detail

The most configured plugin here, and the one to copy from. 35 settings:

| | |
| --- | --- |
| Colours | Ordinary, highlighted, and hidden items - three swatches, plus one per rule |
| Value tiers | Four tiers, each a price you type and a colour you pick |
| Shown | How far away, hide under value, show hidden, only highlighted, outline tiles |
| Rows | Name only / Name and value / Name, value and each |
| Notify | On a highlighted drop, and from a tier up |
| Beams | Over highlighted items, from a tier up, the shape, how tall, how solid, a fade, a core, a ground glow and a pulse |
| Reading | Outlined text instead of a drop shadow |
| Input | A key that hides and shows the labels, and double-tap Alt to do the same |
| Menu | Colour the Take rows for highlighted and for hidden items, and move hidden ones to the bottom |

Plus the hide/highlight rules themselves, added by right-clicking an item rather than typed, up
to 128 of them - each of which can have **a colour of its own**, cycled through ten named ones in
the *Item colours* list. A rule's own colour wins over the plugin's, which is the point: three
highlighted clue steps in the same magenta tell you nothing.

That is a cycle rather than a picker because a config list row has one action button and no room
for a swatch, and giving rows two would change the `ConfigList` contract every plugin is written
against. The rule file stays readable by older clients: rule lines are untouched and a colour goes
on a line of its own, keyed by the rule's name, which such a client skips as a key it does not
know.

One trap worth knowing if you work on this: `colourFor` answers **0** for a row it is not drawing,
so a colour of `000000` would be an item that silently vanishes. A stored black is read as "no
colour of its own" for that reason, and the cycle never produces one.

**"Top tier" means the highest price, not the top box.** Tier thresholds are sorted before use,
because the tier walk takes the first threshold an item's worth clears - prices typed out of order
would otherwise give an item the wrong tier's colour silently. A consequence worth knowing: set
"Top tier from" to 0 and the 0 sorts to the bottom, so "Top tier" in the notify and beam
drop-downs becomes the next price down. Zeroing a price turns off that **colour** tier; turning a
notification off means choosing **Off**.

**A beam is light, not a translucent slab.** The first version was a stack of boxes at one flat
alpha, which reads as a block standing on a tile. Four things make the difference, and each is a
property of light rather than a number off anybody's palette:

- **It fades as it rises** — brightest at the item, faint at the top. This is the single biggest
  one. The ramp runs over `segments + 1` so the last segment is faint rather than *absent*: a beam
  whose top segment is invisible is a beam one segment shorter, and the height setting would quietly
  lose its last notch.
- **It has a brighter core** than its edges, because light is dense in the middle. Two passes per
  segment — a wide soft one and a narrow bright one — is the cheapest thing that reads that way.
- **It lights the ground under it**, drawn first and beneath everything, which roots the beam to the
  tile instead of leaving it hovering over one.
- **It can pulse**, off by default, because motion catches the eye hardest and a beam is already
  doing its job standing still.

Height and opacity are settings now; both were constants. **The opacity setting is the beam's
brightest**, and the pulse dims below it — the first version swung symmetrically about it and peaked
a third over, 129 where the player asked for 96, which is a setting that does not mean what its
label says. The taper is computed over the beam's own height, so a forty-segment beam is not a
needle halfway up and a four-segment one is not barely narrowed. A segment is 24 scene units, so
the default of 24 segments stands about four and a half tiles tall - the proportion the sprite has.
The setting used to say 14 units was "about one and a half tiles"; a tile is 128 units, so that was
wrong by a factor of thirteen and the beam stood at well under half the height it should have.

The pulse takes the time as an argument rather than reading the clock, so its curve can be checked
across two whole cycles. It is a triangle rather than a sine: `Math.sin` in a per-frame draw for a
35% wobble is not a trade anybody would make, and at this speed the two are indistinguishable.

**The shape is measured off Jagex's own sprite, not guessed** - and the first attempt measured the
wrong thing. The asset is 383x1586. Taking the OUTER EXTENT of each row gives 12% of full width at
the halfway mark, and the beam was built by cubing the distance below the tip to fit that one
figure. It shipped as a dotted hairline, because both steps were wrong.

The outer extent at halfway is 35.7%, not 12%, and nearly all of that is the pair of HELICAL
RIBBONS wound round the beam - which this does not draw. Measuring the widest *contiguous* run per
row instead, which is the body on its own, gives:

| below the tip | 0.05 | 0.10 | 0.20 | 0.30 | 0.40 | 0.50 | 0.60 | 0.70 | 0.80 | 0.90 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| body width | 1.1% | 2.2% | 4.4% | 6.9% | 9.2% | 11.4% | 13.9% | 15.8% | 23.9% | 39.7% |

which is **a straight line at 22.6% per unit** through the top seven tenths, and a flare over the
last three into the disc on the ground. A cube gives 0.1% a tenth below the tip where the sprite has
2.2%, so the whole top half came out one pixel wide. That is the `Loot beam` style, and it is the
default.

**Every width is a share of the tile the beam stands on, not a count of pixels.** The first version
used pixel constants - 22 across, 14 tall a segment, a 44x6 pool on the ground - and a beam drawn in
pixels over a scene measured in units is wrong at every distance but one. It stayed 22 across
whether the drop was underfoot or across the square. The segment height was the same mistake twice:
`BEAM_SEGMENT_H` is 24 *scene units*, and the draw loop used it as a count of *pixels*, so the
segments overlapped up close and left visible gaps further off, which is what made the beam look
dashed. Each segment now spans the gap its own two ends project to, so it cannot gap or overlap at
any distance, and the pool of light is simply the tile's own projected box.

**The flare is squared, not straight.** The last three tenths go 15.8%, 23.9%, 39.7% - the gaps
roughly double each step - so interpolating straight from the end of the line to the foot gives a
cone, which is the one shape the sprite is not. Squaring it gives the bell.

**Drawn a screen row at a time, in three passes.** One rectangle per segment is twenty-four visible
steps down each edge of a thing that is meant to be light, and it makes the height setting a
resolution as well as a height. A row at a time is a smooth outline for the same arithmetic. The
three passes are a soft halo at twice the body's width and a third of its alpha, the body, and a
bright narrow core - that layering, rather than the silhouette, is most of why the reference reads
as light and a single translucent wedge reads as a slab. The loop is clamped to `Pix2D`'s clip
bounds, because the tip of a nearby beam projects thousands of pixels above the viewport and
`fillAlpha` clips the drawing but not the loop around it.

**The pool of light is the tile's own quad, as nested rings.** It was the tile's BOUNDING BOX,
which is axis-aligned where a tile is a diamond, so it painted a hard-edged rectangle with corners
sticking out past the tile on four sides - a sticker on the floor. Filling the real quad once is
still a sharp lozenge, so it is filled five times, each ring pulled in toward the centre at a fifth
of the alpha: the middle is covered by every ring and the rim by one, which is a falloff for
nothing. The spans come from Tile indicators, where the scanline fill already lives.

**The helical ribbons are drawn after all.** They were left out on the grounds that they are a
separate element from the body - which is true, and is why the body is measured without them, and
was still the wrong call: they are most of what the eye picks out as "a loot beam" rather than "a
green cone". Two strands half a turn apart, wound twice round the lower 45%, swinging out to about
the body's own width. The sparkles near the base are still not drawn; they are animated, and at the
size a beam is actually seen they are a few pixels.

**The four colours Jagex ships, sampled from the assets**, for anyone setting their tiers to match:

| | Body | Bright core |
| --- | --- | --- |
| Green | `3EEE95` | `B9FDD5` |
| Red | `EE4D3E` | `FDBDB9` |
| Purple | `AF3EEE` | `E1B9FD` |
| Yellow | `EEBA3E` | `FDEAB9` |

The plugin takes the body colour and brightens its own core, so the body column is the one to type
in. The wiki is explicit that these four are what the game ships and that **which value gets which
colour is the player's**, which is how this plugin already works: four thresholds, four colours. The
defaults here are unchanged, because those same colours also draw the item labels and nobody asked
for those to move.

**A new drop is a difference between two ticks**, which no pile can tell you - a pile is what is
on a tile now. `GroundItemArrivals` remembers the last scan and answers the difference, keyed on
**world** coordinates rather than scene ones, because the scene slides under the player. The count
is deliberately not part of that key: taking one coin off a stack is not a drop, and neither is a
kill adding to a stack already there. The first scan after a login reports nothing, so walking up
to a loot pile is silent.

### Skills, Anti-drag and Left-click swaps, in detail

Six settings, four and three. The last of the eleven, and each gained the thing it was actually
missing rather than a set of options for the sake of a number.

**Skills** gets the skill filter, a switch for the combat row, a switch for the experience line,
and an order for the page: skill order, level, experience, or closest to a level. The combat row is
never sorted with the others — a page ordered by level with "Combat level" somewhere in the middle
reads as a skill. The sort is a **stable insertion sort** written out rather than handed to
`Collections.sort` with a comparator per order: twenty-three rows once a tick is nothing either way,
and "closest to a level" is very nearly not a consistent comparator, two skills both 0 away being a
real case. Stability matters because a page that reshuffles its ties every tick is unreadable. A
maxed skill has nothing left to reach, which a plain comparison on 0 puts *first* — it goes last.

**Anti-drag** gets shift, a suspend key, and a chat line. Hold shift and the client's own hold time
is back for as long as you hold it — shift already means "the quick way" here, since shift-click
drops and shift-right-click is the settings menu, so the hand is already on the key. The suspend
key is for the session, not the settings file: a key pressed once by accident should not be a change
to what a player saved. **The chat line is on by default**, unlike the Ground items hotkey, because
this feature has no visible state: hiding the ground labels is its own feedback, whereas a suspended
drag delay looks exactly like a live one until you try to drag something — by which point you have
already dropped it in the wrong slot.

**Left-click swaps** gets the one thing it could not do before level 6: **colour the row a swap
promoted**. A swap is invisible by design — the point is that the option is simply there under the
left button — and that is also what makes a wrong one hard to find, because the menu looks normal
and the click does the wrong thing. The colour is set *after* the promotion, or it lands on whatever
index the row came from. Its chat lines can be turned off, with one exception: "you can only have
128 swaps" always speaks, because that one is the answer to a row that did nothing, and a silent
failure is the one thing worse than a chatty success.

**One hotkey parse, shared.** Ground items and Anti-drag both read a key out of a settings box, and
two readings of `F3` would eventually differ by one — which is a hotkey that fires the wrong key,
indistinguishable from one that does nothing. It lives in `Hotkey` now, with no delegate left behind
in either plugin.

**Barrows doors has no settings, and that is the answer.** The green is the cache's own data that
377 threw away — the unlocked form of each door ships with a recolour and a little extra light — so
there is no drawn highlight to configure and no colour to expose. A setting there would be invented
rather than configurable.

### Boosts and the Idle notifier, in detail

Boosts has eight settings, the Idle notifier five, and they share one piece of code: **"only these
skills"**, a comma-separated list where part of a name is enough.

| Boosts | |
| --- | --- |
| Shown | When nothing is boosted, the difference instead of the levels, the heading |
| Looks | A colour for boosted and for drained, text size |
| Which | Only these skills |
| Notify | When a boost wears off |

| Idle notifier | |
| --- | --- |
| When | Seconds without experience, only counting these skills |
| Again | Repeat every so many seconds, or say it once |
| Anyway | Warn before you have trained at all |
| Sound | An alert sound id, with a Test row to find one |

**Matching is by prefix, and an empty list means every skill.** Prefix because `wood` for
Woodcutting and `att` for Attack are what people type — a substring rule would make `tack` match
Attack and a one-letter term match half the list. And an empty list meaning *everything* is the
opposite of the rule in Npc indicators, which is right in both places: there, an empty list is a
plugin nobody has set up yet and "everything" would outline the whole scene; here, the filter
*narrows* something already useful, so "no filter" has to mean "do not narrow it". A list of nothing
but commas is not a filter either — honouring it literally would show an empty panel with nothing
to explain it.

**The Idle notifier's repeat is a wind-back of its one counter**, not a second timer: after a
warning the tick count is set to the repeat interval short of the threshold, so the next warning is
due exactly then and nothing else is tracked. With no repeat the counter is left *at* the threshold,
so disarming is what stops it rather than the arithmetic.

**Hitpoints and prayer are not in this plugin at all** — not as a row, not as a notification. In
377 the server sends a skill's *current* level in `UPDATE_STAT`, so for hitpoints that is current
health and for prayer it is points left: a drained row read `Hitpoints 35/50`, which is a health
reading in a panel. The panel listed them until that was noticed and ruled on; it does not now.
Everything else wearing off is a potion to drink again, which is about what you are doing rather
than how close to death you are — and that distinction is the whole reason this half of the old
Status bars work survived when the rest was taken out.

One method, `isVital`, is what both the panel and the notice ask, by name rather than by index
because a `3` in a condition tells nobody why. Two separate exclusions could come to disagree about
which skills they covered, and the one that stayed would be the leak. The exclusion is on the
**skill**, not on the direction: a *boosted* hitpoints is left out too, or a hitpoints potion would
report a vital upward.

### Tile indicators, in detail

Eleven settings: three tiles, a colour and a fill each, outline thickness, and how solid a fill is.

**Three tiles, and two of them are not the same square while you move.** The cursor's tile is the
cursor's. "Your tile" is the *rendered* one: the client keeps a fine coordinate it interpolates
between tiles, so a marker drawn from it tracks your feet. **The true tile is what the server
sent** — `routeTileX[0]`, the newest entry in the step queue, which the renderer is still catching
up to. Mid-step it is the tile you are walking *onto*; standing still the two describe the same
square and the outlines sit on top of each other. Tick-perfect movement is read off the true one,
because that is where the server will act from, which is why RuneLite offers both and why they want
different colours.

The true tile is drawn **first**, so where the two coincide the one that tracks your feet is on top
— otherwise a stationary player sees the server's colour and concludes their own setting does
nothing.

**A tile is not a rectangle.** It is a quadrilateral whose four edges run at four different angles,
which is why two things here are built the way they are. The outline is drawn from four projected
corners rather than as a rect — the version that draws a rect looks fine under the player and wrong
everywhere else. And **thickness is concentric rings** pulled toward the tile's own centre, because
"thicken that edge" is a polygon-offset problem rather than a drawing one; rings cannot leave the
tile the way an outward offset could, and each ring's corners clamp at the centre so a distant tile
a few pixels across cannot grow a ring *bigger* than the one it was meant to sit inside.

**A fill is scanlines.** `OverlayGraphics` fills rectangles, so the honest version of a filled
quadrilateral is one one-pixel-high rect per screen row between the quad's edges — which is what a
polygon fill is, and is cheap at the size of a tile. It goes through `fillAlpha` so it can be seen
through, which is the only way a fill on the tile you are standing on does not hide you, and the
outline is drawn *after* it so it sits on top of its own fill.

The span arithmetic is pure and carries the most valuable checks in the plugin, because a wrong
span is not a slightly wrong tile — it is a bar of colour across the screen. Three cases matter: a
horizontal edge lying exactly on a row has to contribute **both** its ends, or the top and bottom
row of every tile is one pixel wide; a row the quad does not reach has to read as "draw nothing"
rather than as a span; and a tile seen exactly edge-on has every edge horizontal, which is a divide
by zero in the obvious implementation, inside a render loop.

### Mouse highlight, in detail

Eight settings: the text's colour and size, an outline instead of a box, the box and its colour,
opacity and border, and whether to show `Walk here`.

**`Walk here` was a rule and is now a setting, with the same default.** Everything that is not
something is walk-here, so showing it means a label following the cursor across every empty tile —
noise with a 100% duty cycle. It is offered because someone learning the interface may want it.

**While a right-click menu is open, the label stands aside.** That is not a setting. The label says
what a *left* click would do, and with a menu open in front of the player that is a click they are
not about to make — so it would be describing the wrong thing while sitting next to the list of
right ones. `ctx.isMenuOpen()` is what makes it askable, and is level 7.

### Npc indicators, in detail

Twelve settings, plus the two ways of filling the list.

| | |
| --- | --- |
| Which | A comma-separated list of names, part of a name being enough |
| Marks | Outline their tiles, name them, include the combat level, where the name sits, outline the text |
| Outline | Which tiles of a big npc, and how thick the border is |
| Menu | Colour their right-click options |
| Bounds | Most marked at once |
| Notify | When one you named appears |

**Two ways to add one, and they are the same store.** Typing the list is the escape hatch;
shift-right-click is the path. A `Tag` row appears on the settings menu over any npc, and choosing
it writes the same comma-separated setting the config box holds — through the plugin's own
`PluginConfig`, so a tag survives a restart rather than living in a field. A second store keyed
differently would be two things to keep in step.

**Matching is substring; tagging is exact.** `goblin` finds a Goblin and a Goblin Guard, because
the alternative reads as "nothing happened" the first time someone guesses a name slightly wrong.
But `Untag Goblin` must not take `Goblin Guard` with it, and a Goblin Guard merely *matched* by
`Goblin` must still offer to **Tag** rather than to Untag — or the menu would offer to remove
something it never added. Those are two different questions about the same list and the plugin
answers them with two different methods on purpose.

**A term may carry its own colour**, written `Goblin=FF0000`. Three monsters marked in the same
green tell you which three are interesting and nothing else — the same argument the per-item
colours in Ground items are built on. The first matching term wins, which is the order they are
written in, so a narrower rule goes above a broader one. A term coloured `000000` is read as having
no colour of its own, because 0 is already what the match walk answers for "no match": a marker
drawn in the colour that means "not drawn" is an npc that silently stops being marked.

**One walk answers both questions.** "Does this npc match" and "in what colour" are the same
search, so `matches` is `termColour` with a non-zero fallback and 0 means no. Two methods walking
the list separately could disagree about which term won.

**Border thickness is concentric rings, not a thick line.** A tile seen in perspective is a
quadrilateral whose four edges run at four angles, so "thicken that edge" is a polygon-offset
problem rather than a drawing one. Rings pulled in toward the tile's own centre are the honest
cheap version, and they cannot leave the tile the way an outward offset could. Each ring's corners
clamp at the centre, because a distant tile is a few pixels across and an inset that overshot would
draw a ring *bigger* than the one it was meant to sit inside.

**Appearances are by name, not by npc.** `Actor` carries the config id, which every Goblin shares,
so there is nothing here that could tell one Goblin from another — and a notice fired every time
one of six wandered in and out of the scene would be noise. A *name* going from absent to present
is the question a player actually has: the boss has spawned. The first tick after turning it on
reports nothing, and "nothing was here last tick" is tracked separately from "there was no last
tick" — conflating them made the first arrival after a scene emptied go unreported, which is the
exact case the feature exists for.

**Menu colouring uses only the colour.** Level 6 offers a row's order too, and this takes none of
it: moving an npc's `Attack` row is a change to what a click does, which is the client's business.
A row is identified by its kind tag (`@yel@` is an npc), because that is the only thing separating
`Attack @yel@Goblin` from `Take @lre@Bones` — without the check, a term like `bones` would colour
rows about items.

### XP drops, in detail

Fifteen settings, and the thing to know before changing any of them is the rule the feature was
tuned around:

**A row's fade is set when the row is created and never refreshed.** Every row leaves on its own
schedule no matter what gains experience after it, which is what makes the column read as drops
rather than as a list. The numbers came out of that tuning - 3000ms was too slow, 600ms too fast,
1500ms right - and they are the defaults the boxes start at.

| | |
| --- | --- |
| Looks | Drop colour, text size, outline instead of a shadow, the skill icon, the skill's name on the row |
| Motion | Which way the column runs, how fast rows settle, how long one stays, how many at once |
| Grouping | Add up gains in the same skill instead of a row per gain |
| The panel | Show it, how long it stays, the progress bar's colour, experience per hour |
| Notify | On a level up |

**Grouping had to be built inside that fade rule rather than around it.** Adding a gain into the
row that is already there does not extend it: a running total that refreshed its own fade would be
a row that never leaves while you train, which is a different feature and one the drops were
explicitly tuned away from. It also only ever grows the *newest* row - a row further down has
already eased into place and had others arrive after it, and growing it would make a number change
in the middle of the column with nothing arriving to explain it.

**Experience per hour stays at 0 for the first second.** Dividing a gain by a few milliseconds
gives a figure in the hundreds of millions, which measures the denominator rather than the player.
The figure is a `long` the whole way through for the same reason: a short session of a maxed
account leaves `int` behind long before it stops being a number worth printing.

**A 99 never announces a level up.** `getExperienceForLevel` has nothing past level 100 and answers
level 99's own figure for it, so a maxed skill's "next level" threshold is experience the player
already has. Without the guard, every single drop at 99 would announce a level up for the rest of
that account's life. The level-up check also reads the level from *before* the change, because the
packet handler posts `StatChanged` before it recomputes `skillBaseLevel` - which is what lets the
plugin compare without keeping a copy of the experience curve.

## Restyling the right-click menu

Level 6. From a `MenuBuilt` handler, a plugin can draw one row in another colour and move one row
to the bottom:

```java
@Subscribe
public void onMenuBuilt(MenuBuilt event) {
    for (int i = 1; i < event.size; i++) {
        if (this.ctx.isGroundItemTake(i)) {
            this.ctx.setMenuColour(i, 0x707070);
            this.ctx.deprioritiseMenuEntry(i);
        }
    }
}
```

**There is no way to change what a row says or what it does**, and there will not be. A plugin that
could relabel a row could put "Bank" where "Attack" is; one that could remove a row could take an
option away without the player ever knowing it was offered. The line the whole API is drawn on is
that a plugin draws and reads while the client owns input, and the menu is the sharpest place that
line matters. This is why RuneLite's *Collapse ground item menu* has no equivalent here.

Three things about it are easy to get wrong:

**The array is upside down.** Index 0 is `Cancel` at the bottom of the menu and the HIGHEST index
is the top row - the one a left click performs, which `ctx.getLeftClickIndex()` will tell you. So
"deprioritise" moves a row *down* toward index 1, which is the opposite of what the indices look
like.

**Colours are cleared every frame.** They are stored by index, and an index means nothing once the
list behind it has been rebuilt - so the same rebuild that writes `Cancel` into index 0 wipes every
override. Set them again from each `MenuBuilt`, which is also why a handler for it should do as
little as possible: it fires on every frame the cursor is over anything, menu open or not.

**Hovering still wins.** A row under the cursor is drawn in the hover colour whatever override it
carries. The hover is the only thing on screen that says which row a click is about to take, and a
coloured row that stopped answering the cursor would have traded feedback for decoration.

`swapMenuEntries` carries a row's action, its parameters **and** its colour, so a row you recolour
and then move keeps its colour rather than leaving it on whatever takes its place.

Moving several rows to the bottom is not a loop over them: each move shifts everything below it, so
the indices you read before the first move are stale by the second, and the row moved **last** ends
up lowest. `GroundItemsPlugin.deprioritiseOrder` is the worked version of that arithmetic if you
need it.

## Telling the player something

```java
this.ctx.notify("Your prayer is low.");              // chat, and the desktop if unfocused
this.ctx.notify("Death Plateau", "Your prayer is low.");
this.ctx.playSound(id);                              // one of the game's own sound effects
this.ctx.hasSound(id);                               // whether this cache has that one
```

`notify` always puts a line in the chatbox, and additionally shows a desktop notification **only
when the game window is not focused**. Popping a tray balloon at someone who is already looking at
the screen is how a useful feature becomes an irritating one, and it is also why desktop
notifications default on: the only time one appears is a time it is worth appearing. Players can
turn that half off in the F9 panel.

**Both are rate limited, silently.** One notification per 1.5 seconds and one sound per 0.25
seconds, shared across every plugin - there is a single `PluginContext`, so there is nothing to
attribute a call to. A plugin that calls these every tick gets the limit and no error: throwing
out of a game handler for something that is only enthusiasm would be worse than dropping it.

**Sound ids are cache-specific and there is no sensible default.** Nothing in the client hardcodes
one - every sound the game plays is an id the server sent - so which ids exist depends on the cache
this server ships. `hasSound` is there so a plugin can check rather than guess, and the Idle
Idle notifier's page has a Test row for finding one by ear. An id with no sound behind it is dropped and
logged: the client's audio loop reports a playback failure **to the server**, so an unchecked id
would be a plugin causing a packet to be sent, which is the one thing this API does not do.

## The cursor

```java
int x = this.ctx.getMouseX();        // viewport coordinates, or -1 when off the game view
int y = this.ctx.getMouseY();
int tx = this.ctx.getHoverTileX();   // the scene tile under it, or -1
int tz = this.ctx.getHoverTileZ();
int worldX = this.ctx.sceneToWorldX(tx);   // for anything that must outlive the loaded area
```

**The hovered tile is one frame behind, and costs nothing until you read it.** The scene answers
"what is at this screen point" while it draws, so the question has to be asked before a frame and
read after it - twenty milliseconds at fifty frames a second. Answering it is a hit test per tile,
which the client only ever paid on a click, so **reading it is what asks for the next answer**.
There is nothing to subscribe to: read it every frame you want it, stop reading and the cost goes
away within about a fifth of a second.

**Save world coordinates, not scene ones.** Scene coordinates are relative to whichever chunk of
the map is loaded, so the same tile is a different pair of numbers after you walk far enough for
the client to reload around you. A marker saved in scene coordinates comes back pointing somewhere
else.

### Why the tile is deferred rather than asked for directly

The scene has exactly **one** "what is at this screen point" slot, and the client already uses it
for walk-here: whatever lands in it is read a frame later by the walk code. A plugin borrowing it
naively would walk the player to wherever the cursor happened to be. So the slot is shared with a
flag saying whose answer is coming - a plugin's request is only armed when nothing else has armed
one, the answer is taken before the walk code can see it, and the click path disowns the flag the
moment it arms its own pick. Without that last part, clicking the ground while a plugin was
hovering would have its walk swallowed.

None of that is a plugin's problem, and none of it is reachable from one. It is written down
because it is the reason this reads as a value rather than as a method you call with a point.

## Who else is in the scene

`ctx.getNpcs()` and `ctx.getPlayers()` hand back `Actor`s, nearest first. An Actor is a name, a
combat level, an npc id, a position, a size, and whether it is you:

```java
for (Actor npc : ctx.getNpcs()) {
    if (!npc.name.equalsIgnoreCase("Goblin")) {
        continue;
    }
    if (ctx.project(npc.centreX(), npc.centreZ(), 230)) {
        g.textCentred(ctx.getProjectedX(), ctx.getProjectedY(), npc.name, 0x00FF00);
    }
}
```

**Two positions, and the difference matters.** `sceneX`/`sceneZ` are fine coordinates, 128 to a
tile, and are where the actor is *mid-step* - `ctx.project` takes these, and a name tag drawn from
them follows a walking npc smoothly. `sceneTileX`/`sceneTileZ` are the tile it stands on, for
`ctx.projectTile`; a label drawn from those snaps tile to tile, which is right for an outline and
wrong for a tag.

**Use `centreX()`/`centreZ()` for anything bigger than one tile.** A large npc is anchored at its
south-west tile, so drawing at `sceneX` puts the label on that corner rather than over the thing.
That is the bug every first boss overlay has.

**It is a snapshot, not a handle.** Everything is copied on the game thread, and an Actor holds no
reference to the entity behind it - so there is nothing on it to click, follow or attack. That is
the same line everything else here is drawn on: a plugin draws and reads, the client owns input
and the socket. It is also a correctness matter rather than only a design one: the client's npc
config is a 20-entry round-robin cache that recycles once more than 20 npc types are on screen, so
a plugin holding a live reference would eventually be reading a different npc than the one it
asked about.

`ctx.getPlayers()` includes you, with `self` set, because the client keeps the local player apart
from the others and a plugin drawing a marker under its own feet would otherwise have to rebuild
half an Actor by hand. Filter on the flag when you want everyone else.

Both allocate and walk the scene, so call them once a frame and walk the result. Sort a list you
filtered back into order with `Actor.compareByDistance`.

## Moving overlays

**Hold Alt and drag any overlay anywhere in the viewport.** The one under the cursor is outlined
so you can see what you are about to pick up, it turns white while you hold it, and it stays
where you drop it. Drop one within a few pixels of where it started and it snaps exactly back.

The move button beside Reload in the plugin panel puts every overlay back where its plugin draws
it, which is also the way back for one dragged somewhere unreachable.

**No plugin had to change for this, and none can opt out.** An overlay works out where it wants
to be and `OverlayGraphics` adds the player's offset on the way to the screen, so the overlay
never learns it moved - which is why the plugins already published are draggable without being
rebuilt, and why a plugin cannot put itself somewhere the player did not. The client owns it, the
way it owns the rest of input.

What this means if you are writing one:

- **Do not add x and y settings.** Position is the player's, through Alt-drag. Keep settings for
  things that are actually yours - what to show, how big, which colour. Boosts had an x and a y
  for the few hours before this existed and lost them: two ways to position one overlay means
  its real position is a sum of both, which nobody can read off either number.
- **Draw where you mean to.** Everything you draw is what you occupy, and what you occupy is what
  a player can grab. An overlay that draws a stray pixel in the far corner has a grab area the
  size of the viewport.
- **Scene-layer overlays do not move.** They are drawn over a tile in the world, so an offset on
  one is a label pointing at the wrong thing. `layer()` decides, and `LAYER_SCENE` opts out by
  being what it is.
- Positions are saved in `plugins.dat` under `overlay.<plugin key>#<n>.at`, where n is the
  overlay's index within your plugin. Reorder your `addOverlay` calls and the ones after the
  change go back to where you draw them.

Alt is also what Ground items reveals hidden piles with, and the two do not collide: that is a
key held with no button, and its own controls are on the right button, while dragging needs a
left press on something a screen overlay drew.

## What comes with the client

Eleven plugins ship built in. All of them were features of the client before they were plugins, or
are small enough that a jar of their own would be more ceremony than code:

| Plugin | On by default | What it does |
| --- | --- | --- |
| Anti-drag | yes | How long a click is held before an item drags, with shift and a key to suspend it. |
| Ground items | yes | Names and values over what is on the floor, with rules per item, value tiers you colour, notifications and beams. |
| Left-click swaps | yes | Which option a left click performs, optionally colouring the row it promoted. |
| XP drops | yes | Experience gained, in the top-right corner, with the colour, the motion, grouping, experience per hour and a level-up notification. |
| Barrows doors | yes | Highlights the door that opens. |
| Boosts | no | Which stats are boosted or drained, by how much, in your colours, with a notice when one wears off. |
| Skills | no | Levels, true levels past 99, combat level, experience to the next level, in the order you choose. |
| Idle notifier | no | Says when you stop gaining experience, optionally in one skill only, once or repeating. |
| Mouse highlight | no | What a left click would do, next to the cursor - its colour, size, outline and box, and it stands aside for a menu. |
| Tile indicators | no | Outlines and optionally fills the cursor's tile, your own, and the one the server has you on, at a thickness you choose. |
| Npc indicators | no | Marks the npcs you name - tiles, name tags, a colour per name, their menu options - and shift-right-click to tag one. |

The first five were client features and are on because turning them off would change what
existing players see. The other six are additions and start off: an addition that turns itself
on rewrites everyone's screen on their next launch.

### What is not here, and why

Two things RuneLite has that this deliberately does not:

- **Animation-based idling.** RuneLite's idle notifier catches the moment a woodcutting swing
  stops; this one waits for the experience that would have followed. Animation state is not
  something `PluginContext` exposes, and a notifier that guessed at it would fire at the wrong
  moments. Waiting for the xp is later, but it is never wrong.
- **Anything reporting health, prayer or special attack.** Not a technical limit. A Status bars
  plugin shipped for one release and was taken back out, and the low-hitpoints and low-prayer
  warnings that were briefly part of the Idle notifier went with it. This server does not put a
  player's vitals in front of them - not as an orb, not as a bar, and not as a popup either,
  since a notification is only a quieter way of doing the same thing. The tests check for their
  absence, so none of it can quietly come back.

  **Boosts was leaking one and no longer does.** Hitpoints and prayer are skills as far as the
  client is concerned, and the panel listed a damaged Hitpoints like any other drained stat -
  which nobody had noticed because it reads as a boost display rather than as a health one. The
  test for it is driven through damage rather than asked of the exclusion directly, because the
  claim is that the exclusion is in the right place.
- **A regen meter.** RuneLite counts down to the next hitpoint. The regen schedule lives on the
  server and the client is never told it, so the only clock a plugin could use would be one it
  made up - and a countdown that is wrong is worse than none, because a player would trust it.
- **Virtual levels and combat level on the stats tab.** RuneLite writes over the tab itself. That
  needs drawing into the game's own interfaces, which `PluginContext` does not offer and should
  not offer lightly. The Skills page shows the same numbers without reaching into the interface.

## Where things are saved

| What | Where |
| --- | --- |
| Plugin jars | `~/.deathplateau/plugins` |
| Where overlays have been dragged to | `plugins.dat`, as `overlay.<key>#<n>.at` |
| What the hub installed, and at which version | `installed.txt`, in the plugins folder |
| Which plugins are on, and their settings | `plugins.dat` in the client's cache folder, beside `qol_settings.dat` |

Jars live in the home folder because a player has to put them there by hand and has to be able to
find it. Settings live with the client's other settings.

## Testing

```sh
python3 tools/clienttests/run_plugintest.py        # the system
python3 tools/clienttests/run_hubtest.py           # the hub, against a real HTTP server
python3 tools/clienttests/run_mousetest.py         # the cursor, Mouse highlight, Tile indicators
python3 tools/clienttests/run_notifytest.py        # notifications, sound and Idle notifier
python3 tools/clienttests/run_dragtest.py          # Alt-drag
python3 tools/clienttests/run_skilltest.py         # Boosts and Skills
python3 tools/clienttests/run_groundtest.py        # Ground items
python3 tools/clienttests/run_sidebarpreview.py    # the sidebar, rendered to build/preview/*.png
```

Each has a mutation suite beside it - `mutate_apilevel.py`, `mutate_dragtest.py`,
`mutate_mousetest.py`, `mutate_notifytest.py`, `mutate_skilltest.py`, `mutate_groundtest.py` - which breaks the code one plausible way at a time and fails unless every
break is caught **by a named check**. A test that only goes red because something threw is not
measuring the thing it claims to. Run one before trusting a test you have just written: the first
run of each of these found holes in its own tests.

The first covers event delivery and consumption, a handler that throws, settings surviving a
restart, and loading plugins out of real jars (manifest and scanned, including a corrupt one).
The hub test serves a real index and real jars over localhost and drives the real downloader
through them, mostly to check the refusals: an id that would climb out of the plugins folder, a
url that is not http, a jar that does not match its checksum, a download that is not a jar.

`run_skilltest` drives the two skill plugins through the real manager and checks what a player
would see - the text the overlays actually draw, the rows the sidebar page actually offers. The
part worth the most is the experience curve: the Skills page continues the client's experience
table past where it ends, and the test points that continuation at all 99 levels the client does
table, because a formula right at 2 and 99 and wrong at 73 is exactly the bug worth catching.

`run_dragtest` drags a test plugin around with Pix2D bound to an int[] it reads back, so "the
overlay moved" is a pixel in a new place rather than a field with a new number in it. That
distinction is the point: the whole mechanism is a translation applied on the way to the screen,
and an offset that changed without the pixels following would be exactly the bug.

`run_sidebarpreview` builds the real sidebar over a real plugin jar and paints it into png files
you can look at, failing if a page comes out blank or if the window does not paint the sidebar at
all.
