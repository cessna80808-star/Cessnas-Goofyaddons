# Cessnas GoofyAddons

Cessnas GoofyAddons is an improved fork of **GoofyAddons by cessna808**.

This client-side Fabric mod includes a Bazaar Flipper and an in-game control panel.

## Features

- Browser control panel for live status, active orders, profit charts, settings, and books.
- Start, pause, resume, and stop the flipper from the browser.
- Configure randomized breaks and an optional sleep window based on your computer's local time. Sleep windows can cross midnight.
- Track claimed Bazaar coins as realized profit and active sell-offer amounts as unrealized value.
- Optionally send profit and order updates to a Discord webhook.

## Quick start

1. Install the mod and start Minecraft.
2. Press **O** to open the in-game control panel, or open **http://127.0.0.1:8765** in a browser on the same computer.
3. Press **J** to start the Bazaar Flipper. Press **K** to stop it.

Minecraft must stay open while using the browser panel. The panel is served by the game and is only available on your local computer. Browser settings save automatically after you stop editing.

## Controls and safety

- **O** opens the in-game control panel.
- **J** starts or resumes the flipper; **K** stops it. You can change these keybinds in Minecraft's Controls menu.
- The safety failsafe pauses the flipper if an unexpected screen opens, you are teleported or change worlds, or your connection is lost. Check the game state and press **J** to resume. The flipper reruns its startup checks before acting.
- Speed mode uses its fixed delay instead of the randomized action-delay range.

## Browser settings

The browser panel has tabs for **Overview**, **Profit**, **Orders**, **Settings**, and **Books**. Use it to view status and active orders, manage configured books, sort orders, export profit history to CSV, or reset the profit tracker.

In **Settings**, you can change speed mode, action delays, storage page commands, and break timing. Minimum and maximum sliders set the random interval between breaks and how long breaks last. Enable sleep time to pause the flipper during a chosen local-time window; overnight windows are supported.

Profit history is stored in `config/goofyaddons-profit.json`. The other settings, including the Discord webhook URL, are stored in `config/goofyaddons.json`.

## Discord updates

Enable **Send Discord updates** in the flipper settings and enter a webhook URL. Choose an update interval (30 seconds, 1 minute, 5 minutes, 15 minutes, or 1 hour) and a chart range (30 seconds to 24 hours).

Each update includes peak coins committed to monitored buy orders, orders placed and filled since the previous update, realized and unrealized totals, and a chart of both profit series. Discord webhooks post new snapshots; they cannot update an existing chart in place. Keep the webhook URL private.

## Development setup

For Fabric development setup instructions, see the [Fabric project setup documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

This project is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
