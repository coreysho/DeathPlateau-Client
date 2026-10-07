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

Eight plugins ship built in. All of them were features of the client before they were plugins, or
are small enough that a jar of their own would be more ceremony than code:

| Plugin | On by default | What it does |
| --- | --- | --- |
| Anti-drag | yes | How long a click is held before an item starts dragging. |
| Ground items | yes | Names and values over what is on the floor, with rules per item. |
| Left-click swaps | yes | Which option a left click performs. |
| XP drops | yes | Experience gained, in the top-right corner. |
| Barrows doors | yes | Highlights the door that opens. |
| Boosts | no | Which stats are boosted or drained, and by how much. |
| Skills | no | Levels, true levels past 99, combat level, experience to the next level. |
| Idle notifier | no | Says when you stop gaining experience. |

The first five were client features and are on because turning them off would change what
existing players see. The last three are additions and start off: an addition that turns itself
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
python3 tools/clienttests/run_notifytest.py        # notifications, sound and Idle notifier
python3 tools/clienttests/run_dragtest.py          # Alt-drag
python3 tools/clienttests/run_skilltest.py         # Boosts and Skills
python3 tools/clienttests/run_groundtest.py        # Ground items
python3 tools/clienttests/run_sidebarpreview.py    # the sidebar, rendered to build/preview/*.png
```

Each has a mutation suite beside it - `mutate_apilevel.py`, `mutate_dragtest.py`,
`mutate_notifytest.py`, `mutate_skilltest.py`, `mutate_groundtest.py` - which breaks the code one plausible way at a time and fails unless every
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
