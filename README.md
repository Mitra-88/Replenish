# Replenish 🌾

Tiny, blazing-fast auto-replant plugin for Bukkit/Spigot/Paper 26.2+.
Inspired by the **Replenish** enchant from Hypixel SkyBlock.

Break a fully-grown crop → it instantly replants itself.

![Preview](assets/output.webp)

---

## What it does

- 🌾 Auto-replants Wheat, Carrots, Potatoes, Nether Wart and Cocoa
- 🌱 Immature crops keep their current growth stage
- 🎒 Optional seed consumption
- 📦 Optional direct pickup into your inventory
- 🪓 Requires the correct tool (Hoes / Axes)
- 🧭 Correctly replants Cocoa with the proper facing
- 🍀 Fortune enchantments work normally
- ⚡ Extremely lightweight and designed for large farms

---

## Quick install

1. Drop the JAR into `plugins/`
2. Start the server
3. Edit `plugins/Replenish/config.yml` if desired
4. Done.

---

## Default behavior (no config edits)

* Breaking a **mature** crop:

    * Replants at age **0**
    * Consumes **1 seed** from your inventory (or off-hand) if `requirePlayerSeed` is `true`
    * Drops go straight to you if `directPickup` is `true`

* Breaking an **immature** crop:
    * Replants at the **same age** (no seed needed)

---

## Commands & permissions

| Command             | Description               |
|---------------------|---------------------------|
| `/replenish status` | Show current settings     |
| `/replenish toggle` | Enable/disable the plugin |
| `/replenish reload` | Reload the configuration  |

Permissions:

* `replenish.status` (default: **true**)
* `replenish.use` (default: **op**)
* `replenish.toggle` (default: **op**)
* `replenish.reload` (default: **op**)
* `replenish.*` (default: **op**)

---

## Config

```yaml
enabled: true
requirePlayerSeed: true     # Must have seeds in inventory
directPickup: true          # Drops go straight to you
replantDelayTicks: 1        # 1 tick = 50ms
maxReplantsPerTick: 4096    # replant jobs processed per server tick (bump if server is strong)
crops:
  wheat: true
  carrots: true
  potatoes: true
  nether_wart: true
  cocoa: true
```