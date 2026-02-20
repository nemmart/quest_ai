# Quest Client — Decompilation TODO

Status key:
- *(blank)* — no progress yet
- **PLANNING** — control flow / stack layout analysis in `plans/`
- **COMPLETE** — final C++ code in `src/`
- **LIBRARY** — runtime/library routine; documented in `runtime_analysis.md`, no C++ needed

---

## Game Functions

Sorted by size (words) to help prioritize. Smallest functions are
easiest wins; largest need planning first.

### Trivial (< 50 words)

| Status | Function | Address | Size | Description |
|---|---|---|---|---|
| | THIEF | 0x7017B42F | 3 | Stub? |
| | TRANSPORT_TERRAK | 0x7017D48F | 3 | Stub? |
| | CREATE_MAP | 0x7016509F | 13 | Tiny — probably a wrapper |
| | C_A_LISTENER | 0x70165713 | 24 | Castle attack listener? |
| | REPOSITION | 0x70176FC1 | 27 | |
| | DIST | 0x70168717 | 28 | Integer distance |
| | RANDOM | 0x7017664D | 37 | Random number wrapper |
| | HIT_ANY_CHAR | 0x7016DE91 | 43 | "Press any key" prompt |
| | READ_IN | 0x701766ED | 48 | Read input from terminal |
| | DISTANCE_TO_PLAYER | 0x701687AD | 49 | |

### Small (50–200 words)

| Status | Function | Address | Size | Description |
|---|---|---|---|---|
| | RETURN_MESSAGE | 0x70176FDD | 53 | Display message to player |
| | GET_INPUT | 0x7016AA35 | 57 | Get player input |
| | UNLOCK_FILE | 0x70169B86 | 57 | |
| LIBRARY | SQR31?3 | 0x7015BD20 | 83 | sqrt() — use sqrtf() |
| | UPDATE_SCREENS | 0x7017D635 | 116 | |
| | LOCK_FILE | 0x70169B0F | 119 | |
| | BARGAIN | 0x7016044F | 134 | |
| | PICK_X_Y | 0x701761E7 | 144 | |
| | OP_HELP | 0x70175BA9 | 155 | |
| | UPDATE_USER_DATA_FILE | 0x7017D735 | 169 | Read/write player save |
| | REFRESH_SCREEN | 0x70176A93 | 187 | |

### Medium (200–700 words)

| Status | Function | Address | Size | Description |
|---|---|---|---|---|
| | INIT_SCREEN | 0x7016E103 | 218 | |
| | OWNS | 0x70175CBF | 234 | |
| | DISPLAY_FLASK | 0x701685AB | 242 | |
| | INIT_SHARED_DATA | 0x7015BE23 | 245 | Set up shared memory ptrs |
| | LOOK | 0x7016F65B | 252 | |
| | FIND_OBJECT | 0x7016A88F | 297 | |
| | TRANSPORT_SUNDAR | 0x7017D492 | 297 | |
| | WRITE_OBJECT | 0x7017D859 | 309 | |
| | STORMS_AT_SEA | 0x7017A695 | 320 | |
| | INIT_OBJ_TBL | 0x7016DF39 | 336 | |
| | FAKE_OCEAN | 0x701699B5 | 346 | |
| | GET_OBJECT_INDEX | 0x7016BDB3 | 359 | |
| | AUTO_MOVE | 0x7015FA73 | 371 | |
| | DISPLAY_MAGIC | 0x70166487 | 399 | |
| | FAKE_LAND_MASS | 0x701697A1 | 409 | |
| | TERRAIN_HELP | 0x7017CB3D | 419 | |
| | TERRAIN | 0x7017C877 | 469 | |
| | SPYGLASS | 0x701797F3 | 471 | |
| | REGEN_SPELLS | 0x70176797 | 639 | |
| | TERRITORY | 0x7017CD71 | 665 | |
| | CLONE_SUNDAR | 0x70164D5B | 681 | |
| | ALLY_PLAYER | 0x7015D053 | 686 | |

### Large (700–2000 words)

| Status | Function | Address | Size | Description |
|---|---|---|---|---|
| | LOGON | 0x70175E9F | 718 | |
| | PLACE_PLAYER | 0x701762F3 | 735 | |
| | CATAPULT | 0x70161CBF | 752 | |
| | REPORT | 0x70176C0B | 828 | |
| | SIGNAL_TURN | 0x70177BED | 837 | |
| | KNIGHT_ATTACK | 0x7016E6BD | 854 | |
| | DIED | 0x7016603D | 894 | |
| | KILL_PLAYER | 0x7016E2C7 | 890 | |
| | DISPLAY_CAVE | 0x701666A3 | 968 | |
| | MOVE | 0x7016F7D1 | 1009 | |
| | BACKPACK | 0x7015FD51 | 1084 | |
| | MOVE_FAMILIAR | 0x7016FC67 | 1182 | |
| | CASTLE_INVENTORY | 0x7016389D | 1227 | |
| | TAKE_OVER_CASTLE | 0x7017C27D | 1404 | |
| | BOAT | 0x70161693 | 1428 | |
| | DISPLAY_MAP | 0x701650AC | 1516 | |
| | QUEST | 0x7015C005 | 1624 | Main entry point |
| | ALCHEMIST_HOME | 0x7015C823 | 1834 | |
| | DEFEND | 0x70165863 | 1836 | |

### Very Large (2000+ words — need plans/ first)

| Status | Function | Address | Size | Description |
|---|---|---|---|---|
| | GET_QUEST | 0x7016B549 | 2031 | |
| | DISPLAY_SCREEN | 0x70166AF9 | 2095 | |
| | OBSERVE | 0x70172CE1 | 2251 | |
| | HELP | 0x7016D55F | 2213 | |
| | MOVE_IN_CAVE | 0x701702DB | 2390 | |
| | TAKE | 0x7017A963 | 2410 | |
| | SEIGE | 0x701771CF | 2452 | |
| | STORE | 0x70179BE3 | 2582 | |
| | LIST_PLAYERS | 0x7016EB87 | 2601 | |
| | BEING_ATTACK | 0x70160B95 | 2619 | |
| | TERRITORY_MAP | 0x7017B799 | 2626 | |
| | FIRE | 0x70169D69 | 2732 | |
| | DROP | 0x70168A27 | 3327 | |
| | CAVE_ATTACK | 0x70163FD3 | 3342 | |
| | DISPLAY_INVENTORY | 0x701674B5 | 4194 | |
| | TOWER_ATTACK | 0x7017D0ED | 799 | |
| | CAST | 0x70162327 | 5247 | |
| | START_TURN | 0x7017821F | 5391 | |
| | MOVE_PLAYER | 0x7017101D | 6723 | |
| | ATTACK | 0x7015D7A7 | 8696 | |
| | OP_EDIT | 0x7017381B | 8557 | Operator/admin editor |

---

## Summary

| Category | Count | Total words |
|---|---|---|
| Trivial (< 50) | 10 | 272 |
| Small (50–200) | 11 | 1,278 |
| Medium (200–700) | 22 | 9,259 |
| Large (700–2000) | 19 | 20,839 |
| Very Large (2000+) | 21 | 71,488 |
| **Total game functions** | **83** | **103,136** |

---

## Suggested Order

1. **Trivial stubs** — quick wins, establish patterns
2. **I/O functions** — GET_INPUT, READ_IN, RETURN_MESSAGE, HIT_ANY_CHAR
3. **Utility functions** — RANDOM, DIST, DISTANCE_TO_PLAYER, FIND_OBJECT
4. **Init functions** — INIT_SHARED_DATA, INIT_SCREEN, INIT_OBJ_TBL
5. **Display functions** — DISPLAY_MAP, DISPLAY_SCREEN, etc.
6. **Core game loop** — QUEST, START_TURN, SIGNAL_TURN, MOVE_PLAYER
7. **Combat** — ATTACK, DEFEND, BEING_ATTACK, etc.
8. **Everything else**

---

## Revision History

- **Session 3**: Initial function inventory from quest.compact.
