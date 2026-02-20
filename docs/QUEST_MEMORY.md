# Quest Global Memory Map

Fixed addresses in the MV-32 address space used by both QUEST and
QUEST_SERVER. These are global variables, file channel numbers, and
pointers to shared memory regions.

---

## 0x70000200 – 0x7000020F: Process/System Area

| Address | Width | Name | Description |
|---|---|---|---|
| 0x700001F2 | wide | | Heap owner PID (read by I?HPOWNER) |
| 0x70000204 | wide | | Stack base stored by I?HPOWNER |

> TODO: Fill in as more globals in this range are identified.

---

## 0x70000210 – 0x7000021F: Shared Memory Pointers & Game State

| Address | Width | Name | Description |
|---|---|---|---|
| 0x70000210 | wide | SD_PTR | Pointer to SHARED_DATA_FILE mapped region |
| 0x70000212 | wide | OBJ_PTR | Pointer to WORLD_DATA_FILE mapped region |
| 0x70000214 | wide | CAS_PTR | Pointer to CASTLE_DATA_FILE mapped region |
| 0x70000216 | narrow | PLAYER_NUM | Current player number (1-based, max 10) |
| 0x70000218 | narrow | | (unknown — TODO) |
| 0x7000021A | narrow | | Set to 0 at QUEST startup; later set to 0x8000 |
| 0x7000021C | narrow | | Read by QUEST main after READ_IN; compared against 0, then range-checked 1..2 |
| 0x7000021D | narrow | | Copied to 0x70000CCC at startup |
| 0x7000021E | narrow | | (unknown — TODO) |

---

## 0x70000260 – 0x7000026F: I/O Channel Numbers

| Address | Width | Name | Description |
|---|---|---|---|
| 0x70000260 | narrow | SCREEN_CHAN | Screen/terminal I/O channel (used by ?WRITE_SCREEN, ?READ_SCREEN) |
| 0x70000262 | narrow | FILE_CHAN_1 | Opened in QUEST main with 7-char filename |
| 0x70000264 | narrow | FILE_CHAN_2 | Used by INIT_SHARED_DATA |

> Other channel numbers may exist in this range — TODO.

---

## 0x70000C00 – 0x70000CFF: Runtime/Scope Variables

| Address | Width | Name | Description |
|---|---|---|---|
| 0x70000C76 | wide | | Scope definition pointer (set by ?SCOPE_INIT) |
| 0x70000C7A | wide | | Scope pointer 2 (set by ?SCOPE_INIT) |
| 0x70000C7C | wide | | Scope pointer 3 (set by ?SCOPE_INIT) |
| 0x70000CCC | narrow | | Copied from [0x7000021D] at QUEST startup |

---

## 0x60000000 – 0x6000FFFF: Shared Partition Pointers

These appear to be linker-resolved addresses pointing into shared
libraries or shared data segments.

| Address | Width | Name | Description |
|---|---|---|---|
| 0x60001997 | wide | | Byte pointer — character data (command input buffer?) |
| 0x60001998 | wide | | Byte pointer — compared against 3-char string at startup |
| 0x6000043A | wide | | Byte pointer — compared against "password" string at startup |

> These are indirect references (`@[0x6000XXXX]`). The actual data is
> wherever these pointers point. TODO: determine what shared partition
> these map to.

---

## Stack Base Reserved Area (per-task)

Each task's stack has reserved words at high offsets from the stack base.
See `runtime_analysis.md` §2.1 for the full exception-handling layout.

| Offset | Width | Name | Description |
|---|---|---|---|
| +0x7FC0 | wide | HANDLER_CHAIN | ON-unit handler chain head |
| +0x7FC2 | wide | SIGNAL_W0 | Current signal info word 0 |
| +0x7FC4 | wide | SIGNAL_W1 | Current signal info word 1 |
| +0x7FC6 | wide | SIGNAL_W2 | Current signal info word 2 |
| +0x7FCC | wide | SF_RECOVERY | Stack fault recovery pointer |
| +0x7FCE | wide | SF_FRAME | Stack fault saved frame |
| +0x7FD0 | wide | SF_FLAG | Stack fault flag |
| +0x7FD4 | wide | LAST_ERROR | Last error code |
| +0x7FD6 | wide | SIG_ORIGIN | Signal origin task area |
| +0x7FD7 | 8 words | SIG_AREA | Signal/error area (T?AREA) |

---

## Revision History

- **Session 3**: Initial skeleton with known addresses from QUEST main
  and INIT_SHARED_DATA analysis.
