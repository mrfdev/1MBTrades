# 1MB-Trades

Custom Paper plugin for 1MoreBlock.com that lets players exchange collected items for rewards such as kit unlock permissions, command-based rewards, and event progression.

The plugin is designed for Paper `1.21.11+`, Java `25+`, and a CMI-centered server stack. It uses an index GUI plus per-trade detail GUIs, supports MiniMessage text, and stores each trade in its own YAML file.

## Current Build Metadata

- Plugin version: `1.0.1`
- Next build number in `version.properties`: `029`
- Target Java: `25`
- Compile Paper API: `26.1.2.build.20-alpha`
- Declared `api-version` floor: `1.21.11`
- Artifact naming pattern: `1MB-Trades-v<pluginVersion>-<build>-j<java>-<minecraft>.jar`

The latest built local artifact in this workspace is:

- `libs/1MB-Trades-v1.0.1-028-j25-26.1.2.build.20-alpha.jar`

## Features

- Main trade index GUI and per-trade detail GUI
- Item-for-command trades
- Single-item, stacked-item, and multi-item collection trades
- Per-trade enable or disable toggle
- Per-trade permission and completion permission support
- UUID-based player usage tracking in `plugins/1MB-Trades/playerData/`
- Per-trade usage caps for one-time or repeatable trades
- Per-trade categories and category-specific trade indexes
- Per-trade allowed-world lists plus a global world blacklist
- Optional per-trade money and EXP costs
- Per-trade start and end dates
- Per-trade hide-when-completed support
- Per-trade info entry via CMI `ctext`
- Global command hooks for open, success, and fail flows
- Trade click cooldown and anti-spam protection
- GUI requirement progress lines for collected items
- Admin trade audit logs and player trade result logs
- Trade-file validation warnings on load and reload
- MiniMessage support in locale and GUI text
- MiniMessage-to-CMI conversion for text commands such as `cmi msg` and `cmi titlemsg`
- MiniMessage tag escaping for item-derived placeholder text
- PlaceholderAPI support where a player context exists
- Internal PlaceholderAPI expansion with `%onembtrades_*%` placeholders
- Admin commands to create, clone, delete, dry-run, and capture trades directly from in-game inventory
- Trade files stored individually in `plugins/1MB-Trades/Trades/`
- Player usage files stored individually in `plugins/1MB-Trades/playerData/`
- Log files stored in `plugins/1MB-Trades/logs/`
- Locale files stored in `plugins/1MB-Trades/Translations/`
- Automatic normalization of legacy `trades/` folder naming to `Trades/`

## Runtime Folder Layout

Inside your Paper server folder:

```text
plugins/
  1MB-Trades/
    config.yml
    logs/
      admin-actions.log
      player-trades.log
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

Note: the current codebase compiles against the official Paper API `26.1.2.build.20-alpha`, declares `api-version: 1.21.11` in `plugin.yml`, and integrates with CMI, PlaceholderAPI, LuckPerms, and Vault at runtime through commands and placeholder resolution. CMI-API is not required to compile or run the current version.

The plugin now also registers its own internal PlaceholderAPI expansion with the identifier:

- `onembtrades`

## Installation

1. Build the plugin with Java 25.
2. Copy the generated jar from `libs/` into your server `plugins/` folder.
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

- `build` creates a jar in `build/libs/` and copies the same jar into `libs/`
- after a successful build, the build number is automatically incremented for the next compile
- version bump tasks reset the build number to `001`
- `showVersionInfo` prints the Java target, the Paper API compile target, the exact Paper dependency notation, and the declared `api-version` floor
- Java compilation enables deprecation and removal lint so API drift shows up during builds before release

## Centralized Test Runner

This project now uses the centralized Paper runner at:

- `/Users/floris/Projects/Codex/servers/run-test-server`

Do not rely on or recreate a repo-local `/servers/` test setup for normal development. If a local `servers/` folder exists in this repo, it is ignored and considered deprecated.

Foreground examples:

```bash
/Users/floris/Projects/Codex/servers/run-test-server --paper 1.21.11 --plugin libs/1MB-Trades-v1.0.1-028-j25-26.1.2.build.20-alpha.jar --foreground
/Users/floris/Projects/Codex/servers/run-test-server --paper 26.1.2 --plugin libs/1MB-Trades-v1.0.1-028-j25-26.1.2.build.20-alpha.jar --foreground
```

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
- `/_trade open category <category> [player]`

Admin-only, guarded by `onembtrade.admin`:

- `/_trade reload`
- `/_trade debug [trade]`
- `/_trade debug player <player>`
- `/_trade debug <trade> reset <player|all>`
- `/_trade create <id>`
- `/_trade clone <source> <newId>`
- `/_trade delete <trade>`
- `/_trade capture requirements <trade>`
- `/_trade capture icon <trade>`
- `/_trade capture reward <trade>`
- `/_trade set display <trade> <value>`
- `/_trade set description <trade> <line1|line2|line3>`
- `/_trade set permission <trade> <value>`
- `/_trade set completion <trade> <value>`
- `/_trade set max <trade> <number|-1|unlimited>`
- `/_trade set hide <trade> <true|false>`
- `/_trade set worlds <trade> <global|world1,world2>`
- `/_trade set money <trade> <amount>`
- `/_trade set exp <trade> <levels>`
- `/_trade set start <trade> <MM-dd-yyyy|yyyy-MM-dd|none>`
- `/_trade set end <trade> <MM-dd-yyyy|yyyy-MM-dd|none>`
- `/_trade set category <trade> <value>`
- `/_trade set ctext <trade> <value>`
- `/_trade set sort <trade> <number>`
- `/_trade toggle <trade> <true|false>`
- `/_trade command add <trade> <open|info|success|fail> <command>`
- `/_trade command clear <trade> <open|info|success|fail>`
- `/_trade test <trade> [player]`

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

Set visibility, category, and world restrictions:

```bash
/_trade set hide summer_event true
/_trade set category summer_event summer
/_trade set worlds summer_event spawn,wild
/_trade open category summer
```

Set optional money and EXP costs:

```bash
/_trade set money summer_event 25000
/_trade set exp summer_event 5
```

Set date windows:

```bash
/_trade set start summer_event 06-01-2027
/_trade set end summer_event 08-31-2027
```

The command parser also accepts ISO-style dates such as `2027-06-01`.

Add a reward command:

```bash
/_trade command add summer_event success console:lp user %player% permission set onembtrade.kit.%id% true
```

Enable a disabled trade:

```bash
/_trade toggle example_vote_tokens true
```

Clone or delete a trade:

```bash
/_trade clone summer_event summer_event_weekend
/_trade delete summer_event_weekend
```

Dry-run a trade without consuming anything:

```bash
/_trade test summer_event
/_trade test summer_event mrfloris
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
- `%category%`
- `%trade_permission%`
- `%ctext_file%`
- `%allowed_worlds%`
- `%current_world%`
- `%required_items%`
- `%item_cost%`
- `%requirements_count%`
- `%money_cost%`
- `%exp_cost%`
- `%player_money%`
- `%player_level%`
- `%start_date%`
- `%end_date%`
- `%trade_uses%`
- `%max_trades%`
- `%max_uses%`
- `%remaining_trades%`
- `%remaining_uses%`
- `%missing_items%`
- `%missing_amount%`
- `%missing_money%`
- `%missing_exp%`
- `%missing_summary%`

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
- `%owned_amount%`
- `%required_amount%`
- `%item_missing_amount%`

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
- `category`
- `allowed_worlds`
- `money_cost`
- `exp_cost`
- `start_date`
- `end_date`
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
- `current_world`
- `player_money`
- `player_level`
- `can_access`
- `can_access_raw`
- `can_trade`
- `can_trade_raw`
- `completed`
- `completed_raw`
- `missing_items`
- `missing_amount`
- `missing_money`
- `missing_exp`
- `missing_summary`

Status output notes:

- `status` returns text such as `Ready`, `Collecting`, `Unlocked`, `Limit Reached`, `Scheduled`, `Expired`, `Locked`, `Disabled`, or `Enabled`
- `status_key` returns lowercase keys such as `ready`, `collecting`, `unlocked`, `limit_reached`, `scheduled`, `expired`, `locked`, `disabled`, or `enabled`

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

When `console:` or `player:` dispatches a CMI text command, the plugin converts MiniMessage tags to Minecraft legacy color codes before CMI receives the command. This currently applies to `cmi msg`, `cmi titlemsg`, `cmi actionbarmsg`, `cmi bossbarmsg`, `cmi broadcast`, and `cmi toast`.

## Configuration Overview

### `config.yml`

Global operational settings:

- admin permission
- default trade permission prefix
- optional player alias command
- target locale file
- player level and balance placeholders
- UUID-based playerData tracking
- global blacklisted worlds
- trade click cooldown in milliseconds
- optional hide-completed behavior for direct trade opens
- optional built-in result message toggle
- GUI materials
- audit log files in `logs/`
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
  blacklisted-worlds:
    - "spawn"
    - "spawn_nether"
  trade-click-cooldown-ms: 750
  hide-completed-on-direct-open: true
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
category: summer
display-name: "<gold>Summer Event Trade</gold>"
description:
  - "<gray>Collect the event items and trade them here.</gray>"
permission: "onembtrade.%id%"
completion-permission: ""
max-trades: -1
hide-when-completed: false
allowed-worlds:
  - global
money-cost: 0
exp-cost: 0
start-date: ""
end-date: ""
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
- `category` can be used with `/_trade open category <category>`
- `%id%` can be reused in `permission`, `completion-permission`, `ctext-file`, and command strings
- `max-trades: 1` is a one-time trade
- `max-trades: -1` makes the trade repeatable without a cap
- `allowed-worlds: [global]` means any world except those in the global blacklist
- `allowed-worlds: [spawn, wild]` restricts trading to those worlds, unless one is also globally blacklisted
- `money-cost` and `exp-cost` default to `0`
- `start-date` and `end-date` accept `MM-dd-yyyy` or `yyyy-MM-dd`
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
console:cmi msg %player% !<gold>[trade]</gold> <yellow>The trade has been successful, go to your favourite world and type /kit trade-%id% to collect it! (You can get this reward once)</yellow>
console:cmi msg %player% !<gold>[trade]</gold> <red>The trade has failed, you were missing %missing_amount% item(s): %missing_items%</red>
console:cmi titlemsg %player% <gold>Trade Complete</gold> \n <white>%trade_name% unlocked</white>
```

This lets CMI handle the exact chat style and prefix formatting.

If a trade already has a visible chat feedback command such as `message:` or `console:cmi msg ...`, the plugin now suppresses its own built-in result chat line automatically so players do not get duplicate success or fail messages.

The plugin also keeps separate audit logs in:

- `plugins/1MB-Trades/logs/admin-actions.log`
- `plugins/1MB-Trades/logs/player-trades.log`

## Recommended Workflow

1. Create the trade with `/_trade create <id>`.
2. Put required items in the main inventory, not the hotbar.
3. Run `/_trade capture requirements <id>`.
4. Hold the GUI icon in your main hand and run `/_trade capture icon <id>`.
5. Hold the reward preview item in your main hand and run `/_trade capture reward <id>`.
6. Set display, description, category, permission, completion permission, and ctext.
7. Set `max-trades`, `hide`, `worlds`, and optional `money` or `exp` costs.
8. Add optional `start` and `end` dates.
9. Add success and fail commands.
10. Run `/_trade test <id>` for a dry-run before going live.
11. Run `/_trade debug <id>`.
12. Use `/_trade debug <id> reset <player|all>` when you need to clear tracked usage data.
13. Run `/_trade reload` after manual YAML edits.

## Support

If you want to ask a question, report a bug, or suggest an improvement, please use the GitHub Issues page:

- [1MBTrades Issues](https://github.com/mrfdev/1MBTrades/issues)

Community links:

- [1MoreBlock Discord](https://discord.gg/floris)

## Publishing This Project To GitHub From CLI

This workspace is currently not initialized as a Git repository. To connect it to your empty GitHub repository:

```bash
cd ~/Projects/Codex/1MBTrades
git init
git branch -M main
git add .
git commit -m "Initial commit: 1MB-Trades v1.0.1"
git remote add origin https://github.com/mrfdev/1MBTrades.git
git push -u origin main
```

If GitHub asks for authentication, use your normal Git credential flow, SSH remote, or GitHub CLI.

SSH alternative:

```bash
git remote add origin git@github.com:mrfdev/1MBTrades.git
git push -u origin main
```

## Credits

- Idea and server use-case direction by Floris for [1MoreBlock.com](https://1moreblock.com/).
- Built for the 1MoreBlock Minecraft server network and its event, vote, and kit-trade workflows.
- Development was mainly realized with the help of [OpenAI](https://openai.com/).
- Community and project home: [discord.gg/floris](https://discord.gg/floris)

## TODO

Deferred items that are intentionally shelved for a later milestone:

- Add automated smoke and regression coverage that drives the centralized `/Users/floris/Projects/Codex/servers/run-test-server` flow.
- Explore optional hidden item fingerprinting with PersistentDataContainer for stronger anti-counterfeit item matching.
- Consider cooldown-based repeat-trade controls that are separate from `max-trades`.
- Consider reusable reward presets or grouped reward packages for multiple trades.
- Evaluate whether more `%onembtrades_*%` statistics should be exposed, especially around resets and long-term usage reporting.

## Notes For Future Maintenance

- Keep `README.md` updated whenever commands, placeholders, permissions, folder names, dependencies, or versioning behavior change.
- Keep `LICENSE` as MIT unless you intentionally decide to relicense later.
- Do not commit test servers or macOS `.DS_Store` files.
