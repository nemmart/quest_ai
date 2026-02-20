# WORLD_DATA_FILE Memory Layout

Pointed to by `OBJ_PTR` at global address `0x70000212`.
Mapped as a shared memory partition accessible by both QUEST and
QUEST_SERVER.

This file appears to contain world object data — rooms, items,
NPCs, and map topology.

All offsets are **narrow word offsets** from OBJ_PTR unless noted.

---

## Overall Structure

> TODO: Determine top-level organization. Likely candidates:
> - Room/location table
> - Object/item table
> - NPC/monster table
> - Map connectivity data

---

## Known Offsets

| Offset | Width | Name | Description |
|---|---|---|---|
| | | | |

> TODO: Populate as functions that access OBJ_PTR are decompiled.
> Key functions to examine:
> - Movement/navigation routines
> - Room description routines
> - Object interaction (get, drop, use)
> - Monster/NPC placement

---

## Revision History

- **Session 3**: Initial skeleton.
