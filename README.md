# Replenish

A quality-of-life farming plugin for Minecraft servers, inspired by Hypixel Skyblock's "Replenish" enchant.  
When enabled, it **automatically replants crops** after harvesting, removing the need to manually replant.

---

## ✨ Features
- **Automatic replanting** for supported crops:
  - Wheat
  - Potatoes
  - Carrots
  - Nether Wart
  - Cocoa Beans (with axe support)
- **Fortune I/II/III support** for extra crop drops.
- **Global toggle command** for enabling/disabling the feature server-wide (OPs only).
- **Configurable crop list** (easily add/remove supported crops in `config.yml`).
- **Delay before replanting** (~15ms) for smoother, more natural gameplay feel.
- **Works even on partially grown crops** — always replants.
- **No seeds? No replant!** Requires the player to have the correct seed/item in their inventory.
- **Optimized for Bukkit/Spigot/Paper/Leaf/PufferFish/Purpur 1.21.8** with minimal overhead.
- **No unnecessary logging** — clean console output.
- **Bug-free** replant system using scheduled tasks to avoid Spigot overwrite issues.

---

## 🆚 Improvements Over the Original Version

### Original Issues:
- Still on 1.20.4.
- Only supported full-grown crops.
- Replanted even if the player **did not have seeds**.
- Only worked with hoes, no axe support for Cocoa Beans.
- No Fortune enchantment support.
- Used immediate block updates, causing replant to fail on break → replant loops.
- No global enable/disable toggle — always on.
- No partial crop handling — breaking partially grown crops didn’t replant.
- Required per-player setup rather than global.
- No configurable crops.

### Current Improvements:
- ✅ Supports 1.21.8.
- ✅ Supports **both fully grown and partially grown crops**.
- ✅ Requires seeds/items in inventory before replanting.
- ✅ Added **Fortune support** for realistic farming boosts.
- ✅ Added **axe support for Cocoa Beans**.
- ✅ Added **global OP-only toggle** to enable/disable.
- ✅ Fixed timing bug by adding a short replant delay.
- ✅ Fully global system — no per-player setup.
- ✅ Cleaned up unused code, removed debug logs, fixed API warnings.
- ✅ More maintainable structure using clear method separation.

---

## 📦 Installation
1. Drop the `.jar` file into your server’s `plugins` folder.
2. Restart the server.
3. Use `/replenish` (OP only) to toggle the feature on or off.

---

## ⚙️ Configuration
Located in `config.yml`:
```yaml
crops:
  WHEAT: true
  POTATOES: true
  CARROTS: true
  NETHER_WART: true
  COCOA: true
```
- `true` → crop is affected by Replenish.
- `false` → crop is ignored.

---

## 🛠 Commands
- `/replenish` — Toggles the plugin globally (OP only).
```
