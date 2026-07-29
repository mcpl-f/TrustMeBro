# Trust Me Bro

A Minecraft plugin that asks: *do you trust this person with your stuff?*

Someone digs your base, places blocks on your build, or rummages through your chests. The plugin notices (via CoreProtect), tells the people tied to that place — usually in Telegram — and lets **them** choose:

- **Trust** — fine, don’t warn me about this player again  
- **Report** — not fine, keep a note for admins  

Trust is one-way. Report does not ban anyone by itself.

## Why

Grief and “oops I broke your house” look the same. Admins drown if every block break is an alert. This sits in the middle: catch weird interactions, let the owner decide, escalate what people care about.

## Setup

Needs **CoreProtect** and **tg-bridge** / mctg-bridge (Telegram + linked Minecraft accounts).

Drop the jar in `plugins/`, start once, tweak `config.yml` if you want.

## How it feels (Telegram)

### Warning (with buttons)

```text
Player Griefer interacted with blocks associated with:

> region «Spawn»
    at Overworld x: 100, z: -20
      ± 50
- Alice: 3 removed, 1 container interactions
- Bob: 2 placed

Do you trust this player?

[ Trust ✅ ]  [ Report ❌ ]
```

Press a button → short confirmation (e.g. “Alice now trusts Griefer”).

### `/truststats`

```text
Top 3 trusted players:
- Steve, trusted by Alice, Bob
- Alex, trusted by 12

Top 3 suspicious players:
- Herozero, interactions 7582, owners 332
^ in regions «a» / «b» — and 5 more
- I_dead_to_lol, interactions 5584, owners 257

Top 3 most reported:
- Griefer, reported by Alice, Bob, Carol
- Scout, reported by 7
```

## Telegram commands

| Command | What it does |
| --- | --- |
| *(auto warning)* | Message + **Trust** / **Report** buttons when someone messes with linked owners’ stuff |
| `/truststats` | Top trusted, suspicious (with regions), and most reported players |
| `/mctrust` | Stub / linked account check (WIP) |

Same decisions in-game: `/ttrust <player>`, `/treport <player>`. Optional join reminder in Minecraft chat (`minecraft.notify-on-join`). Staff can name places with `/new_trust_region`.

## In-game commands

| Command | Permission | Default |
| --- | --- | --- |
| `/ttrust <player>` | `trusts.command.trust` | everyone |
| `/treport <player>` | `trusts.command.report` | everyone |
| `/new_trust_region <radius> <name>` | `trusts.region.create` | ops |

LuckPerms example if you lock things down:

```text
lp group default permission set trusts.command.trust true
lp group default permission set trusts.command.report true
lp group admin permission set trusts.region.create true
```
