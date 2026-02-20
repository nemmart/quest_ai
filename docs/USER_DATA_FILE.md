# USER_DATA_FILE Layout

A **disk-based** file (not shared memory) named `user_data_file` on the
original DG system. Contains persistent per-player data that is read
and written via the `UPDATE_USER_DATA_FILE` routine (0x7017D735).

Unlike the shared memory files (SHARED_DATA_FILE, WORLD_DATA_FILE,
CASTLE_DATA_FILE), this is accessed through `?READ` / `?WRITE` system
calls, not memory-mapped pointers.

---

## File Characteristics

| Property | Value |
|---|---|
| Filename | `user_data_file` |
| Record size | 980 (0x03D4) narrow words = 1960 bytes |
| Access | Sequential read/write via ?READ / ?WRITE |
| String constant | "USER_DATA_FILE" at 0x7016E235 |

---

## UPDATE_USER_DATA_FILE (0x7017D735)

Takes 3 arguments:
- arg1 (`@[fp+0xFFF4]`): file channel
- arg2 (`@[fp+0xFFF2]`): buffer pointer (data to read/write)
- arg3 (`@[fp+0xFFF0]`): operation mode (1 = read, other = write?)

Uses a 980-word record with read parameters:
- Transfer mode: 0x8000 (binary)
- Max bytes: 0x6000 (24576)

> TODO: Verify operation mode values and exact read/write semantics.

---

## Record Layout

Each record is 980 narrow words. Contents unknown.

| Offset | Width | Name | Description |
|---|---|---|---|
| | | | |

> TODO: Populate by examining what data is read into / written from
> the buffer at the call sites:
> - ALLY_PLAYER (0x7015D053) — calls at 0x7015D286
> - Function at ~0x7016E509 — TODO: identify
> - Function at ~0x7016E629 — TODO: identify
>
> Compare with SHARED_DATA_FILE player record (686 words) — the
> user data record is larger (980 words), so it likely contains
> additional persistent state not kept in shared memory.

---

## Revision History

- **Session 3**: Initial skeleton. Record size and access pattern
  identified from UPDATE_USER_DATA_FILE disassembly.
