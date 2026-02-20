# SHARED_DATA_FILE Memory Layout

Pointed to by `SD_PTR` at global address `0x70000210`.
Mapped as a shared memory partition accessible by both QUEST (client)
and QUEST_SERVER.

All offsets below are **narrow word offsets** from SD_PTR unless noted.

---

## Overview

The shared data file contains per-player records and global game state.
Up to 10 players are supported. Each player record is 686 (0x02AE)
narrow words.

**Player record formula:**
```
player_record_base = SD_PTR + (player_num - 1) * 686
```
where `player_num` is 1-based (1..10).

---

## 0x0000 – 0x02AD: Player Record 1

See [Player Record Layout](#player-record-layout) below.

## 0x02AE – 0x055B: Player Record 2

## 0x055C – 0x0809: Player Record 3

## 0x080A – 0x0AB7: Player Record 4

## 0x0AB8 – 0x0D65: Player Record 5

## 0x0D66 – 0x1013: Player Record 6

## 0x1014 – 0x12C1: Player Record 7

## 0x12C2 – 0x156F: Player Record 8

## 0x1570 – 0x181D: Player Record 9

## 0x181E – 0x1ACB: Player Record 10

---

## 0x1ACC – 0x7FFF: Global Shared State

Offsets here are absolute from SD_PTR (not relative to a player).

| Offset | Width | Name | Description |
|---|---|---|---|
| 0x002B | narrow | | Read in QUEST main; player count or max player? |
| 0x1EFBA | narrow | | Read in QUEST main (subtracted from 4) |
| 0x1F6CF | narrow | | Set to 4 in QUEST main |
| | | | |

> Large gap — most offsets unknown. TODO: populate as functions are
> decompiled.

### Known Offsets Accessed From QUEST Main

These offsets were accessed relative to SD_PTR in the QUEST main
function, applied to a specific player's record base:

| Player Offset | Width | Name | Description |
|---|---|---|---|
| +0x7DB3 | narrow | | Set to 16000 (0x3E80) at player init |
| +0x7DB4 | narrow | | Set to 16000 (0x3E80) at player init |
| +0x7D8B | narrow | | Set to 0 at player init |
| +0x7E85 | narrow | | Set to 10000 (0x2710) at player init |
| +0x7E86 | narrow | | Set to 10000 (0x2710) at player init |
| +0x7E89 | narrow | | Set to 4 at player init |
| +0x7E8A | narrow | | Set to 5 at player init |

> **Note:** These large offsets (0x7xxx) relative to a player base
> suggest they may actually be global offsets into the shared data,
> not per-player. Need to verify by checking whether the base register
> was SD_PTR or SD_PTR + player_offset at point of access.

---

## Player Record Layout

Each player record is 686 narrow words (1372 bytes). Offsets below are
relative to the start of the player record.

| Offset | Width | Name | Description |
|---|---|---|---|
| | | | |

> TODO: Populate field-by-field as player access patterns are identified
> during decompilation. Key functions to examine:
> - QUEST main (player initialization)
> - ALLY_PLAYER
> - Character creation routines
> - Combat routines (HP, stats, equipment)
> - Movement routines (position, room)

---

## Revision History

- **Session 3**: Initial skeleton. Player record stride (686) and
  a handful of offsets from QUEST main identified.
