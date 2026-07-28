# Trust Me Bro

A small Minecraft plugin that asks players: *do you trust this person with your stuff?*

## What is it

Someone digs your base, places blocks on your build, or rummages through your chests.

This plugin notices that (via CoreProtect history), waits a bit so it doesn’t spam, then warns the people who seem related to that place — usually in Telegram, with two buttons:

- **Trust** — “it’s fine, don’t warn me about this player again”
- **Report** — “not fine, keep a note for admins”

Trust is one-way: you trust them for *your* stuff. They don’t automatically trust you back. Report doesn’t ban anyone by itself — it just records the complaint.

## Why

Grief and “oops I broke your house” look the same in the moment. Admins drown in noise if every block break is an alert.

This sits in the middle:

1. Catch weird interactions early
2. Let the actual player decide trust vs report
3. Only escalate what people care about

Less spam for chat. Less guesswork for mods.

## How

**Setup (short version)**

You need:

- CoreProtect (history of who built / broke / used what)
- tg-bridge / mctg-bridge (Telegram + linked Minecraft accounts)

Drop the plugin jar in `plugins/`, start once, edit `config.yml` if you want.

**Day to day**

1. Player A touches something that looks like it belongs to Player B (from CoreProtect).
2. If B doesn’t already trust A, the plugin counts it.
3. After a quiet period (`debounce-time-sec`), it may send one Telegram warning listing the affected people.
4. A linked owner presses **Trust** or **Report** in Telegram — or uses the same actions in-game:
   - `/ttrust <player>` — trust that player for *your* blocks
   - `/treport <player>` — report that player
5. Next time A messes with that owner’s stuff, trusted players stay quiet; reports stay on file.

Optional: warn the player in Minecraft when they join (`minecraft.notify-on-join`).

### In-game commands & permissions

| Command | Permission | Default |
| --- | --- | --- |
| `/ttrust <player>` | `trusts.command.trust` | everyone (`true`) |
| `/treport <player>` | `trusts.command.report` | everyone (`true`) |
| `/new_trust_region <radius> <name>` | `trusts.region.create` | ops only |

With the defaults above, `/ttrust` and `/treport` already work for all players — no LuckPerms setup required.

**LuckPerms** (only if you changed defaults, or want a specific group):

```bash
# everyone (default group)
lp group default permission set trusts.command.trust true
lp group default permission set trusts.command.report true

# or only a group, e.g. members
lp group member permission set trusts.command.trust true
lp group member permission set trusts.command.report true

# named regions stay staff-only unless you grant this
lp group admin permission set trusts.region.create true
```

To deny a group after leaving `default: true` in `plugin.yml`, set the permission to `false` for that group (or switch the plugin.yml defaults to `op` and grant only where you want).

**Useful config knobs**

| Setting | Meaning |
| --- | --- |
| `detection.debounce-time-sec` | Wait this long before warning (cuts spam) |
| `telegram.notify` | Send Telegram warnings at all |
| `telegram.only-for-linked-players` | Only warn when at least one owner is linked to Telegram |
| `telegram.max-owners-per-notification` | How many owners to list in one message |
| `detection.exclude-banned-players` | Ignore banned accounts as “owners” |
| `default-locale` | Message language (`ru` / `en`) |

Reload-friendly: the notification job re-reads config as it runs, so you can tweak debounce and related options without a full restart.
