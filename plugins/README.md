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

Two tabs: the plugin list, with a search box, a switch per plugin and a cog on any plugin that
has settings; and the hub above.

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

## The API

Everything is in `jagex2.client.plugin`.

| Class | What it is |
| --- | --- |
| `Plugin` | What you extend. `startUp`, `shutDown`, `addOverlay`, `ctx`, `config`. |
| `PluginContext` (`ctx`) | What you may read and do: position, skills, the right-click menu, world-to-screen projection, a chat message. |
| `Overlay` | What you draw. `render(OverlayGraphics)`, `priority()`. |
| `OverlayGraphics` | Text, rectangles, lines and a ready-made `panel(...)`, in viewport coordinates. `width()` and `height()` are the drawable area for this frame - ask each frame, since the display mode changes it. |
| `@PluginDescriptor` | Name, description and saved key. |
| `@ConfigItem` | A player-facing setting. |
| `@Subscribe` | Marks an event handler. |

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

## Where things are saved

| What | Where |
| --- | --- |
| Plugin jars | `~/.deathplateau/plugins` |
| What the hub installed, and at which version | `installed.txt`, in the plugins folder |
| Which plugins are on, and their settings | `plugins.dat` in the client's cache folder, beside `qol_settings.dat` |

Jars live in the home folder because a player has to put them there by hand and has to be able to
find it. Settings live with the client's other settings.

## Testing

```sh
python3 tools/clienttests/run_plugintest.py        # the system
python3 tools/clienttests/run_hubtest.py           # the hub, against a real HTTP server
python3 tools/clienttests/run_sidebarpreview.py    # the sidebar, rendered to build/preview/*.png
```

The first covers event delivery and consumption, a handler that throws, settings surviving a
restart, and loading plugins out of real jars (manifest and scanned, including a corrupt one).
The hub test serves a real index and real jars over localhost and drives the real downloader
through them, mostly to check the refusals: an id that would climb out of the plugins folder, a
url that is not http, a jar that does not match its checksum, a download that is not a jar.

The last builds the real sidebar over a real plugin jar and paints it into png files you can look
at, failing if a page comes out blank or if the window does not paint the sidebar at all.
