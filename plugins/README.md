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

1. Put the jar in the `plugins` folder of your client cache - `~/.deathplateau/plugins` on Linux
   and macOS, `%USERPROFILE%\.deathplateau\plugins` on Windows. Create the folder if it is not
   there.
2. Start the client and press **F8**.
3. Tick the plugin. It stays ticked next time.

**A new plugin starts switched off, and that is deliberate.** A plugin is ordinary Java running
inside your client, with your client's access to your filesystem and your connection. There is no
sandbox. Only install plugins whose source you can see and whose author you trust.

`Reload plugins` at the bottom of the panel re-reads the folder, so a plugin can be rebuilt and
picked up without restarting the client.

## The examples

`plugins/src` has two, which between them use most of the API:

- **Coordinates** - the smallest useful plugin: a descriptor, two settings, one overlay.
- **XP tracker** - events, per-session state, a hotkey, a multi-line overlay.

Build them:

```sh
./gradlew examplePlugins
cp build/plugins/example-plugins.jar ~/.deathplateau/plugins/
```

Then F8 in game and tick them.

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
  are shown there and edited in `plugins.dat` in the cache folder. Never rename a `keyName` after
  release - it resets that setting for everyone.

## The API

Everything is in `jagex2.client.plugin`.

| Class | What it is |
| --- | --- |
| `Plugin` | What you extend. `startUp`, `shutDown`, `addOverlay`, `ctx`, `config`. |
| `PluginContext` (`ctx`) | What you may read and do: position, skills, the right-click menu, world-to-screen projection, a chat message. |
| `Overlay` | What you draw. `render(OverlayGraphics)`, `priority()`. |
| `OverlayGraphics` | Text, rectangles, lines and a ready-made `panel(...)`, in viewport coordinates. |
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

## Testing

```sh
python3 tools/clienttests/run_plugintest.py
```

Covers event delivery and consumption, a handler that throws, settings surviving a restart, and
loading plugins out of real jars (manifest and scanned, including a corrupt one).
