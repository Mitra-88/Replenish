# Replenish

A quality-of-life farming plugin for Minecraft servers, inspired by Hypixel Skyblock's “Replenish” enchant.  
When enabled, it **automatically replants crops** after harvesting, so players don’t have to.

---

## ✨ Features
- **Automatic replanting** for:
    - Wheat, Potatoes, Carrots, Nether Wart (requires hoe)
    - Cocoa Beans (requires axe)
- **Fortune I/II/III respected** via vanilla/Bukkit drop calc.
- **Global toggle** (permission-gated) to enable/disable server-wide.
- **Configurable per-crop toggles** in `config.yml` (for the five supported crops).
- **Replant delay**: defaults to **1 tick (~15ms)** for a smoother feel.
- **Partial crops handled** — breaking an immature crop replants it at the same age.
- **Seed requirement (configurable)** — by default, **mature** crops only replant if the player has the correct seed/item in inventory.
- **Lightweight**: minimal overhead, clean console output.
- **Robust replanting** using scheduled tasks to avoid race/overwrite issues.
- **Tested on modern Bukkit/Spigot/Paper-family servers (1.21.x)**.

---

## 📦 Installation
1. Drop the `.jar` into your server’s `plugins` folder.
2. Restart the server.
3. Grant permissions as needed (see below).

---

## ⚙️ Configuration
Located in `config.yml`:

```yaml
enabled: true
requirePlayerSeed: true
restrictToHoesAndAxes: true
directPickup: true

replantDelayMs: 15

maxReplantsPerTick: 4096

crops:
  wheat: true
  carrots: true
  potatoes: true
  nether_wart: true
  cocoa: true
```