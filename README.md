# Replenish 🌱 — Auto‑Replant that Actually Feels Good

A tiny, fast Bukkit/Spigot/Paper (1.21+) plugin that makes farming feel *buttery*. Harvest your crops, they pop right back in. Seeds get handled, drops land where they should, and your inventory isn’t chaos. Built to be friendly for players, admins, and the server TPS.

> TL;DR: break crops → they auto‑replant → items go to your inventory (or drop neatly) → everyone’s happy.

---

## ✨ Features

* **Auto‑Replant** for core crops (wheat, carrots, potatoes, nether wart, cocoa). Toggle per‑crop.
* **Direct Pickup** mode: send drops straight into the player’s inventory when possible.
* **Seed Requirement**: make players keep seeds on them to replant (or turn it off for chill servers).
* **Smart Drop Logic**: if the inventory’s full, items drop *cleanly* at a centered block location.
* **Gentle Feedback**: a subtle pickup sound confirms items actually got added.
* **Performance‑aware**: tune replant delay and per‑tick caps so big farms don’t lag the party.
* **Zero Command Setup**: enable, drop in, tweak config, done.

---

## 🚀 Quick Start

1. **Drop the jar** into your server’s `plugins/` folder.
2. **Restart** the server.
3. **Edit** the generated config at `plugins/Replenish/config.yml`.
4. **/reload** or restart again if you changed settings.

You’re now vibing with auto‑replant. 🌾

---

## ⚙️ Config (defaults)

Here’s the out‑of‑the‑box config so you can copy/paste and tweak:

```yml
enabled: true
requirePlayerSeed: true
directPickup: true

# ~15ms default → 1 tick
replantDelayMs: 15

# cap per-tick replant work; raise if you have headroom
maxReplantsPerTick: 4096

crops:
  wheat: true
  carrots: true
  potatoes: true
  nether_wart: true
  cocoa: true
```

### What the settings actually do

* **enabled**: Global on/off switch. Kill switch for troubleshooting.
* **requirePlayerSeed**: If `true`, players must have seeds in their inventory to replant.
* **directPickup**: If `true`, try to send drops straight into the player inventory.
* **replantDelayMs**: Small delay before the replant happens (helps with event ordering + spam control).
* **maxReplantsPerTick**: Safety cap for how many replant actions can run each tick.
* **crops.**\*: Toggle individual crop types. If it’s not here, it’s not auto‑replanted.

---

## 🧠 How it behaves (Player POV)

* Break a mature crop → it replants automatically.
* If you’ve got space, the harvest goes into your inventory. You’ll hear a soft pickup sound.
* No space? Items drop naturally at a neat, centered position so you’re not chasing pixels.

---

## 🛠️ Notes for Admins

* **Performance tuning**:

    * Start with the defaults. If you run mega farms, bump `maxReplantsPerTick` slowly until TPS stays butter‑smooth.
    * `replantDelayMs` can help smooth spikes from large synchronized harvests.
* **Player experience**:

    * `requirePlayerSeed: true` = survival‑friendly; players still engage with seed economy.
    * `requirePlayerSeed: false` = relaxed servers / minigames.
    * `directPickup: true` feels clean and reduces item clutter.
* **Per‑crop control**: flip off anything that doesn’t fit your meta.

---

## 💡 FAQ

**Q: Does it work on Spigot/Paper?**
Yep. It’s built on the Bukkit API, so Spigot & Paper are good.

**Q: What happens when inventories are full?**
Drops are placed naturally at a centered, slightly raised position on the block so they’re easy to grab.

**Q: Commands or permissions?**
None required for basic use. It’s hands‑off by design.

**Q: Will this lag my server?**
Not if you keep an eye on the caps. Defaults are safe; scale thoughtfully for huge farms.