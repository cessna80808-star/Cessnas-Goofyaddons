# GoofyAddons

GoofyAddons is a client-side Fabric mod with an in-game control panel for the Bazaar Flipper.

## Browser control panel

Start Minecraft with the mod installed, then open **http://127.0.0.1:8765** in a browser on the same computer. The animated, tabbed web panel separates Overview, Profit, Orders, Settings, and Books; it displays live macro status, monitored active Bazaar orders, claimed-coin and active-sell-offer graphs, and lets you sort orders, start or stop the flipper, edit settings and books, export profit history to CSV, and reset profit tracking. Animations respect the browser's reduced-motion setting. Claimed Bazaar coins count as realized; the total amount listed on active sell offers counts as unrealized. Profit history is stored in `config/goofyaddons-profit.json`. Changes save automatically one second after you stop editing. The panel is served by the Minecraft client and only bound to the local computer; Minecraft must remain running while you use it.

Enable **Send Discord updates** in the flipper settings and enter a Discord webhook URL to receive updates while the flipper is running. Choose how often snapshots are posted (30 seconds, 1 minute, 5 minutes, 15 minutes, or 1 hour) and the chart range (30 seconds through 24 hours). Each summary includes peak coins committed to monitored flipper buy orders, orders placed and filled since the previous update, current realized/unrealized totals, and a chart with both series overlaid. Discord webhooks post new snapshots; they cannot continuously update an existing chart in place. Unrealized value is the total amount listed on active sell offers. The webhook URL is stored in `config/goofyaddons.json`; treat it as a secret.

## Controls

- Press **O** to open the GoofyAddons control panel.
- Press **J** to start the Bazaar Flipper and **K** to stop it. These keybinds can be changed in Minecraft's Controls menu.
- The safety failsafe pauses the flipper if an unexpected screen opens, the player is teleported or changes worlds, or the connection is lost. Check the game state and press **J** to resume; the flipper restarts its startup checks before acting again.
- The panel shows the macro's live state, current feature and task count, active failsafes, configured books, and current keybinds.
- Edit speed mode, action delays, storage page commands, and Discord webhook options in the browser panel; changes save automatically to `config/goofyaddons.json`.
- Speed mode uses its fixed delay instead of the randomized action delay range.

## Setup

For development setup instructions, see the [Fabric project setup documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
