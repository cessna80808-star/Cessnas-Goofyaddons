# GoofyAddons

GoofyAddons is a client-side Fabric mod with an in-game control panel for the Bazaar Flipper.

## Browser control panel

Start Minecraft with the mod installed, then open **http://127.0.0.1:8765** in a browser on the same computer. The local web panel displays live macro status, monitored active Bazaar orders, configured books, claimed-coin and active-sell-offer graphs; it lets you start or stop the flipper, edit settings and books, export profit history to CSV, and reset profit tracking. Claimed Bazaar coins count as realized; the total amount listed on active sell offers counts as unrealized. Profit history is stored in `config/goofyaddons-profit.json`. Changes save automatically one second after you stop editing. The panel is served by the Minecraft client and only bound to the local computer; Minecraft must remain running while you use it.

## Controls

- Press **O** to open the GoofyAddons control panel.
- Press **J** to start the Bazaar Flipper and **K** to stop it. These keybinds can be changed in Minecraft's Controls menu.
- The safety failsafe pauses the flipper if an unexpected screen opens, the player is teleported or changes worlds, or the connection is lost. Check the game state and press **J** to resume; the flipper restarts its startup checks before acting again.
- The panel shows the macro's live state, current feature and task count, active failsafes, configured books, and current keybinds.
- Edit speed mode, action delays, and the first/second storage page commands in the panel, then select **Save settings** to persist them to `config/goofyaddons.json`.
- Speed mode uses its fixed delay instead of the randomized action delay range.

## Setup

For development setup instructions, see the [Fabric project setup documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
