# 1MB-Trades

Custom Paper plugin for 1MoreBlock.com that lets players exchange collected items for rewards such as kit unlock permissions, command-based rewards, and event progression.

The plugin is designed for Paper `1.21.11+`, Java `25+`, and a CMI-centered server stack. It uses an index GUI plus per-trade detail GUIs, supports MiniMessage text, and stores each trade in its own YAML file.

## Current Build Metadata

- Plugin version: `1.0.0`
- Next build number in `version.properties`: `021`
- Target Java: `25`
- Target Minecraft: `1.21.11`
- Artifact naming pattern: `1MB-Trades-v<pluginVersion>-<build>-j<java>-<minecraft>.jar`

The latest built local artifact in this workspace is:

- `build/libs/1MB-Trades-v1.0.0-020-j25-1.21.11.jar`

## Features

- Main trade index GUI and per-trade detail GUI
- Item-for-command trades
- Single-item, stacked-item, and multi-item collection trades
- Per-trade enable or disable toggle
- Per-trade permission and completion permission support
- UUID-based player usage tracking in `plugins/1MB-Trades/playerData/`
- Per-trade usage caps for one-time or repeatable trades
- Per-trade info entry via CMI `ctext`
- Global command hooks for open, success, and fail flows
- MiniMessage support in locale and GUI text
- PlaceholderAPI support where a player context exists
- Internal PlaceholderAPI expansion with `%onembtrades_*%` placeholders
- Admin commands to create trades and capture items directly from in-game inventory
- Trade files stored individually in `plugins/1MB-Trades/Trades/`
- Player usage files stored individually in `plugins/1MB-Trades/playerData/`
- Locale files stored in `plugins/1MB-Trades/Translations/`
- Automatic normalization of legacy `trades/` folder naming to `Trades/`

## Runtime Folder Layout

Inside your Paper server folder:

```text
plugins/
  1MB-Trades/
    config.yml
    playerData/
      <uuid>.yml
    Translations/
      Locale_EN.yml
    Trades/
      Example-Vote-Tokens.yml
      Summer-Event.yml
```

Fresh installs currently seed two bundled examples:

- `Example-Vote-Tokens.yml`
- `Summer-Event.yml`

## Server Requirements

Required:

- Paper `1.21.11+`
- Java `25+`

Recommended plugin stack:

- [CMI](https://www.zrips.net/cmi/commands/)
- [CMILib](https://github.com/Zrips/CMILib)
- [LuckPerms](https://luckperms.net/download)
- [Vault](https://www.spigotmc.org/resources/vault.34315/)
- [PlaceholderAPI](https://ci.extendedclip.com/job/PlaceholderAPI/)

Development references:

- [CMILib source](https://github.com/Zrips/CMILib)
- [CMI-API source](https://github.com/Zrips/CMI-API)

Note: the current codebase compiles only against the Paper API and integrates with CMI, PlaceholderAPI, LuckPerms, and Vault at runtime through commands and placeholder resolution. CMI-API is not required to compile or run the current version.

The plugin now also registers its own internal PlaceholderAPI expansion with the identifier:

- `onembtrades`

## Installation

1. Build the plugin with Java 25.
2. Copy the generated jar from `build/libs/` into your server `plugins/` folder.
3. Start the server once so `plugins/1MB-Trades/` is created.
4. Edit:
   - `plugins/1MB-Trades/config.yml`
   - `plugins/1MB-Trades/Translations/Locale_EN.yml`
   - `plugins/1MB-Trades/Trades/*.yml`
5. Run `/_trade reload` in console or in-game as an admin.

## Build and Versioning

Build metadata lives in `version.properties`.

Available Gradle tasks:

- `./gradlew build`
- `./gradlew showVersionInfo`
- `./gradlew bumpPatchVersion`
- `./gradlew bumpMinorVersion`
- `./gradlew bumpMajorVersion`
- `./gradlew setPluginVersion -PnewPluginVersion=x.y.z`

Behavior:

- `build` creates a jar using the current metadata
- after a successful build, the build number is automatically incremented for the next compile
- version bump tasks reset the build number to `001`

Example:

```bash
./gradlew showVersionInfo
./gradlew build
./gradlew bumpPatchVersion
./gradlew build
```

## Commands

Player-facing:

- `/_trade`
- `/_trade open [trade] [player]`

Admin-only, guarded by `onembtrade.admin`:

- `/_trade reload`
- `/_trade debug [trade]`
- `/_trade debug <trade> reset <player|all>`
- `/_trade create <id>`
- `/_trade capture requirements <trade>`
- `/_trade capture icon <trade>`
- `/_trade capture reward <trade>`
- `/_trade set display <trade> <value>`
- `/_trade set description <trade> <line1|line2|line3>`
- `/_trade set permission <trade> <value>`
- `/_trade set completion <trade> <value>`
- `/_trade set max <trade> <number|-1|unlimited>`
- `/_trade set ctext <trade> <value>`
- `/_trade set sort <trade> <number>`
- `/_trade toggle <trade> <true|false>`
- `/_trade command add <trade> <open|info|success|fail> <command>`
- `/_trade command clear <trade> <open|info|success|fail>`

## Command Examples

Create a trade:

```bash
/_trade create summer_event
```

Capture required items from the player inventory, excluding the hotbar:

```bash
/_trade capture requirements summer_event
```

Set a GUI icon from the item in the main hand:

```bash
/_trade capture icon summer_event
```

Set a reward preview item from the item in the main hand:

```bash
/_trade capture reward summer_event
```

Set MiniMessage display text:

```bash
/_trade set display summer_event <gradient:#F6D365:#FDA085>Summer Event Trade</gradient>
```

Set a multi-line description:

```bash
/_trade set description summer_event <gray>Collect all summer items.</gray>|<yellow>Trade them here for your unlock.</yellow>
```

Set templated permissions and ctext:

```bash
/_trade set permission summer_event onembtrade.%id%
/_trade set completion summer_event onembtrade.kit.%id%
/_trade set ctext summer_event onembtrade-%id%
```

Set the trade to one-time, limited, or unlimited:

```bash
/_trade set max summer_event 1
/_trade set max summer_event 5
/_trade set max summer_event unlimited
```

Add a reward command:

```bash
/_trade command add summer_event success console:lp user %player% permission set onembtrade.kit.%id% true
```

Enable a disabled trade:

```bash
/_trade toggle example_vote_tokens true
```

Reset tracked usage for one player or for all tracked players:

```bash
/_trade debug summer_event reset mrfloris
/_trade debug summer_event reset all
```

This reset only changes `playerData/*.yml`. It does not remove external LuckPerms permissions or undo reward commands that were already granted.

Add a CMI title message:

```bash
/_trade command add summer_event success console:cmi titlemsg %player% &6Trade Complete \n &fSummer kit unlocked
```

Add an info command:

```bash
/_trade command add summer_event info console:cmi ctext onembtrade-%id% %player%
```

If the `info` command list is empty, the plugin automatically falls back to:

```text
console:cmi ctext %ctext_file% %player%
```

## Permissions

- `onembtrade.admin`
  - Gives access to all admin commands
  - Lets admins see disabled trades in the index

Per-trade visibility and access:

- Default trade permission is `onembtrade.<tradeId>`
- This is controlled by `settings.trade-permission-prefix` in `config.yml`
- A trade can override `permission:` directly in its YAML
- `completion-permission:` can still be used for reward logic and one-time legacy sync
- `max-trades:` controls how many times each player may complete the trade
- `max-trades: 1` means one-time
- `max-trades: -1` means unlimited repeatable trades

This works well with LuckPerms contexts such as per-world kit availability.

## Placeholders

### Trade and GUI Placeholders

Core placeholders available in trade commands and trade text:

- `%player%`
- `%player_name%`
- `%player_uuid%`
- `%id%`
- `%trade_id%`
- `%trade_name%`
- `%trade_description%`
- `%trade_permission%`
- `%ctext_file%`
- `%required_items%`
- `%item_cost%`
- `%requirements_count%`
- `%trade_uses%`
- `%max_trades%`
- `%max_uses%`
- `%remaining_trades%`
- `%remaining_uses%`
- `%missing_items%`
- `%missing_amount%`

Alias placeholders for `settings.global-command`:

- `%player%`
- `%player_name%`
- `%args%`

GUI and locale placeholders:

- `%page%`
- `%max_page%`
- `%reward_name%`
- `%requirements_count%`
- `%requirements_suffix%`
- `%trade_status%`
- `%trade_file%`
- `%player_exp%`
- `%player_balance%`

When PlaceholderAPI is installed, any PlaceholderAPI placeholder can also be used where the plugin has a player context.

### 1MB-Trades PlaceholderAPI Expansion

The plugin registers an internal PlaceholderAPI expansion:

- Identifier: `onembtrades`

Important syntax note:

- PlaceholderAPI uses the format `%identifier_params%`
- because of that, the valid 1MB-Trades syntax starts with `%onembtrades_...%`
- a format like `%onembtrades.trade.summer_event%` is not valid PlaceholderAPI syntax
- the working equivalent is `%onembtrades_trade.summer_event%`

General placeholders:

- `%onembtrades_version%`
- `%onembtrades_build%`
- `%onembtrades_trades_loaded%`
- `%onembtrades_enabled_trades%`
- `%onembtrades_tracked_players%`
- `%onembtrades_visible_trades%`
- `%onembtrades_ready_trades%`
- `%onembtrades_completed_trades%`

Per-trade placeholder base:

- `%onembtrades_trade.<tradeId>%`

This returns the trade display name in plain text.

Recommended dotted field style:

- `%onembtrades_trade.summer_event%`
- `%onembtrades_trade.summer_event.enabled%`
- `%onembtrades_trade.summer_event.item_cost%`
- `%onembtrades_trade.summer_event.item_cost_summary%`
- `%onembtrades_trade.summer_event.status%`

Supported underscore-style aliases for common suffixes:

- `%onembtrades_trade.summer_event_enabled%`
- `%onembtrades_trade.summer_event_item_cost%`
- `%onembtrades_trade.summer_event_status%`

Per-trade fields currently available:

- `display_name`
- `display_formatted`
- `display_name_plain`
- `id`
- `enabled`
- `enabled_raw`
- `sort_order`
- `description`
- `description_formatted`
- `permission`
- `completion_permission`
- `ctext_file`
- `requirements_count`
- `item_cost`
- `item_cost_summary`
- `trade_uses`
- `max_trades`
- `max_uses`
- `remaining_trades`
- `remaining_uses`
- `reward_item`
- `reward_preview`
- `icon_item`
- `status`
- `status_key`
- `can_access`
- `can_access_raw`
- `can_trade`
- `can_trade_raw`
- `completed`
- `completed_raw`
- `missing_items`
- `missing_amount`

Status output notes:

- `status` returns text such as `Ready`, `Collecting`, `Unlocked`, `Limit Reached`, `Locked`, `Disabled`, or `Enabled`
- `status_key` returns lowercase keys such as `ready`, `collecting`, `unlocked`, `limit_reached`, `locked`, `disabled`, or `enabled`

Boolean output notes:

- fields like `enabled`, `can_trade`, and `completed` return `Yes` or `No`
- the `_raw` variants return `true` or `false`

Examples:

```text
%onembtrades_trade.summer_event%
%onembtrades_trade.summer_event.enabled%
%onembtrades_trade.summer_event.item_cost%
%onembtrades_trade.summer_event.item_cost_summary%
%onembtrades_trade.summer_event.status%
%onembtrades_trade.summer_event.can_trade%
%onembtrades_trade.summer_event.missing_items%
```

## Command Prefixes

Trade command entries support these prefixes:

- `console:` runs the command as console
- `player:` runs the command as the player
- `message:` sends a MiniMessage or legacy-formatted chat message
- `actionbar:` sends an action bar message

## Configuration Overview

### `config.yml`

Global operational settings:

- admin permission
- default trade permission prefix
- optional player alias command
- target locale file
- player level and balance placeholders
- UUID-based playerData tracking
- optional built-in result message toggle
- GUI materials
- global commands for:
  - `open-index`
  - `open-trade`
  - `info`
  - `success`
  - `fail`

Example alias configuration:

```yaml
settings:
  global-alias: "summerevent"
  global-command: "_trade open summer_event %player%"
  send-plugin-result-messages: false
```

When `global-alias` is set, 1MB-Trades now registers that alias as a real Paper command at startup and on reload, so it appears in command help and no longer shows as an unknown red client-side command.

Default result sounds are configured separately:

```yaml
global-commands:
  success:
    - "console:cmi sound ENTITY_PLAYER_LEVELUP -v:0.8 %player% -s"
  fail:
    - "console:cmi sound ENTITY_VILLAGER_NO -v:0.8 %player% -s"
```

### `Translations/Locale_EN.yml`

Stores:

- GUI titles
- button names and lore
- status text
- player-head text
- all plugin messages
- GUI helper lines

MiniMessage is supported here, including hex colors such as:

```yaml
messages:
  trade-success: "<#8BE28B>Trade complete.</#8BE28B>"
```

Legacy `&` color strings are still accepted as a fallback.

### `Trades/<TradeName>.yml`

Each trade has its own file.

Example:

```yaml
id: summer_event
enabled: true
sort-order: 0
display-name: "<gold>Summer Event Trade</gold>"
description:
  - "<gray>Collect the event items and trade them here.</gray>"
permission: "onembtrade.%id%"
completion-permission: ""
max-trades: -1
hide-when-completed: false
ctext-file: "onembtrade-%id%"
requirements: []
icon-item: null
reward-item: null
commands:
  open: []
  info: []
  success:
    - "console:lp user %player% permission set onembtrade.kit.%id% true"
    - "console:cmi sound ENTITY_PLAYER_LEVELUP -v:0.8 %player% -s"
  fail: []
```

Notes:

- `id` must be unique
- `id` should use lowercase letters, numbers, underscores, and hyphens only
- `%id%` can be reused in `permission`, `completion-permission`, `ctext-file`, and command strings
- `max-trades: 1` is a one-time trade
- `max-trades: -1` makes the trade repeatable without a cap
- if `max-trades` is missing in an older trade file, the plugin treats it as `1`
- successful trades are tracked per player UUID in `playerData/<uuid>.yml`
- existing one-time rewards can be synced once from `completion-permission:` into playerData for backward compatibility
- if `info` is empty and `ctext-file` is set, the plugin will use `console:cmi ctext <resolved ctext file> %player%`

## CMI Message Pattern

If you want full control over the success and fail output, a good pattern is to:

1. Set `settings.send-plugin-result-messages: false` in `config.yml`
2. Use `console:cmi msg %player% !...` in trade `success` and `fail` commands

Examples:

```text
console:cmi msg %player% ![trade] The trade has been successful, go to your favourite world and type /kit trade-%id% to collect it! (You can get this reward once)
console:cmi msg %player% ![trade] The trade has failed, you were missing %missing_amount% item(s): %missing_items%
```

This lets CMI handle the exact chat style and prefix formatting.

If a trade already has a visible chat feedback command such as `message:` or `console:cmi msg ...`, the plugin now suppresses its own built-in result chat line automatically so players do not get duplicate success or fail messages.

## Recommended Workflow

1. Create the trade with `/_trade create <id>`.
2. Put required items in the main inventory, not the hotbar.
3. Run `/_trade capture requirements <id>`.
4. Hold the GUI icon in your main hand and run `/_trade capture icon <id>`.
5. Hold the reward preview item in your main hand and run `/_trade capture reward <id>`.
6. Set display, description, permission, completion permission, and ctext.
7. Set `max-trades` to `1`, another number, or `-1` for repeatable trades.
8. Add success and fail commands.
9. Run `/_trade debug <id>`.
10. Use `/_trade debug <id> reset <player|all>` when you need to clear tracked usage data.
11. Run `/_trade reload` after manual YAML edits.

## Publishing This Project To GitHub From CLI

This workspace is currently not initialized as a Git repository. To connect it to your empty GitHub repository:

```bash
cd ~/Projects/Codex/1MBTrades
git init
git branch -M main
git add .
git commit -m "Initial commit: 1MB-Trades v1.0.0"
git remote add origin https://github.com/mrfdev/1MBTrades.git
git push -u origin main
```

If GitHub asks for authentication, use your normal Git credential flow, SSH remote, or GitHub CLI.

SSH alternative:

```bash
git remote add origin git@github.com:mrfdev/1MBTrades.git
git push -u origin main
```

## Notes For Future Maintenance

- Keep `README.md` updated whenever commands, placeholders, permissions, folder names, dependencies, or versioning behavior change.
- Keep `LICENSE` as MIT unless you intentionally decide to relicense later.
- Do not commit test servers or macOS `.DS_Store` files.
