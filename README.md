# Replenish 🌾 – auto-replant QoL

Small, fast, crop QoL plugin for Bukkit/Spigot/Paper 26.1+.
Inspired by the **Replenish** enchant from Hypixel SkyBlock — break a mature crop, it replants itself. Keeps things snappy, respects tools.

---

## What it does

* ✅ **Auto-replants** Wheat, Carrots, Potatoes, Nether Wart, and Cocoa
* 🧠 **Age-aware**: if a crop isn’t fully grown, it goes back down at the **same age**
* 🎒 **Seed requirement (configurable)**: optionally consumes 1 seed from the player (main inv or off-hand)
* 🧲 **Direct pickup (configurable)**: drops go straight into the player inventory
* 🪓 **Tool checks**: Hoes for crops, Axes for cocoa. No cheese.
* 🧱 **Cocoa-aware**: finds jungle wood and keeps the correct facing on replant
* 💎 **Fortune-respecting**: uses Bukkit drops with your tool’s enchants
* ⚡️ **High-throughput**: time-wheel queue, rate-limited per tick so servers don’t melt
---

## Quick install

1. Drop the JAR into `plugins/`
2. Start the server once to generate config
3. Tweak `plugins/Replenish/config.yml` if you want
4. Done. Go farm.

---

## Default behavior (no config edits)

* Breaking a **mature** crop:

    * Replants at age **0**
    * Consumes **1 seed** from your inventory (or off-hand) if `requirePlayerSeed` is `true`
    * Drops go straight to you if `directPickup` is `true`

* Breaking an **immature** crop:
    * Replants at the **same age** (no seed needed)
    * No extra XP

---

## Commands & permissions

`/replenish status` – shows current settings
`/replenish toggle` – flips global enable/disable
`/replenish reload` – reloads config

Permissions:

* `replenish.status` (default: **true**)
* `replenish.use` (default: **op**)
* `replenish.toggle` (default: **op**)
* `replenish.reload` (default: **op**)
* `replenish.*` (default: **op**)

> The command node is declared as `replenish.use`, but each sub-permission gates its subcommand.

---

## Config

```yaml
enabled: true
requirePlayerSeed: true
directPickup: true

# ~15ms → 1 tick
replantDelayMs: 15

# replant jobs processed per server tick (bump if server is strong)
maxReplantsPerTick: 4096

crops:
  wheat: true
  carrots: true
  potatoes: true
  nether_wart: true
  cocoa: true
```