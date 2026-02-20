# CASTLE_DATA_FILE Memory Layout

Pointed to by `CAS_PTR` at global address `0x70000214`.
Mapped as a shared memory partition accessible by both QUEST and
QUEST_SERVER.

This file appears to contain castle/dungeon-specific data — possibly
room layouts, door states, treasure placement, or dungeon level
topology that is separate from the overworld.

All offsets are **narrow word offsets** from CAS_PTR unless noted.

---

## Overall Structure

> TODO: Determine top-level organization. Possible contents:
> - Castle room definitions
> - Door/lock states
> - Castle-specific object placement
> - Dungeon level maps

---

## Known Offsets

| Offset | Width | Name | Description |
|---|---|---|---|
| | | | |

> TODO: Populate as functions that access CAS_PTR are decompiled.
> Key functions to examine:
> - Castle entry/exit routines
> - Castle navigation
> - Castle-specific object interactions

---

## Revision History

- **Session 3**: Initial skeleton.
