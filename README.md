# Quest Reverse Engineering Project

## Goal

Reverse-engineer the Quest game from Data General MV-32 (Eagle) binaries
back into C++ source code for Linux that compiles, runs, and is fully
**binary-compatible** with the original data files. Quest and Quest_Server
should run as separate processes using memory-mapped files to communicate,
exactly as they did on the original Eagle hardware.

---

## Background

### The Game

Quest is a multiplayer fantasy game that ran on a Data General MV-32 Eagle
minicomputer under the AOS/VS operating system. It consists of two
cooperating processes:

- **QUEST** (client) -- player-facing: handles input, display, character
  actions
- **QUEST_SERVER** -- game engine: manages world state, NPC/monster AI,
  turn processing

They communicate through three shared memory regions mapped into both
processes (see Memory Layout below).

### The Emulator

This repo contains a Java emulator for the MV-32 Eagle hardware and a
minimal set of AOS/VS operating system call emulations (mapped to
Windows/Java calls). The emulator can be found in the `Source/` directory:
- `Source/hw/` -- instruction set emulation (Eagle CPU)
- `Source/os/` -- AOS/VS system call emulation
- `Source/Debug/` -- tools for loading and dumping program information
- `Docs/` -- AOS/VS system call and instruction set documentation

### The Binaries

The original Quest source code has been lost. We have only the compiled
binaries:
- `quest/QUEST.PR` and `quest/QUEST_SERVER.PR` -- executable program files
- `quest/QUEST.ST` and `quest/QUEST_SERVER.ST` -- symbol tables

### CONCEPTS

The `CONCEPTS` file gives background on the game design and world.

---

## Disassembly & Symbol Files

The binaries have been disassembled using the Java tools. The main working
files are:

| File | Contents |
|---|---|
| `quest.symbols` | Symbol table dump for QUEST.PR |
| `quest.compact` | Disassembled code + initial memory state for QUEST |
| `quest_server.symbols` | Symbol table dump for QUEST_SERVER.PR |
| `quest_server.compact` | Disassembled code + initial memory state for QUEST_SERVER |

### How to Regenerate

```bash
# Compile all Java source and package into quest.jar
javac Source/**/*.java
jar cf quest.jar ...

# Quest client
java -cp quest.jar debug.SymbolTable quest/quest.st >quest.symbols
java -cp quest.jar StartStop quest quest "SQR31?3" "?UTSK" "SQR31?3" >quest.addrs
java -cp quest.jar Disassemble quest quest quest.addrs >quest.compact

# Quest server
java -cp quest.jar debug.SymbolTable quest/quest_server.st >quest_server.symbols
java -cp quest.jar StartStop quest quest_server "SQR31?3" "?UKIL" "SQR31?3" >quest_server.addrs
java -cp quest.jar Disassemble quest quest_server quest_server.addrs >quest_server.compact
```

### Compact File Structure

Inside `quest.compact` and `quest_server.compact`:
- **Initial memory state** -- hex dumps of program data, string constants,
  lookup tables
- **Disassembled program code** -- one instruction per line, with addresses

For **quest.compact**, game functions run from `INIT_SHARED_DATA` through
`WRITE_OBJECT`. For **quest_server.compact**, from `QUEST_SERVER` through
`WRITE_OBJECT`.

After `WRITE_OBJECT` are system/library routines:
- **System routines** have `?` in the name (e.g., `?RANDOM_NUMBER`,
  `?OPEN_FILE`)
- **Language runtime routines** have `.` in the name (e.g., `I.PROLOG`,
  `O.ON`)

---

## Source Language: PL/I

The original game was written in **PL/I** (not Fortran 77). This was
determined by analysis of the runtime system, which uses PL/I-specific
features:
- **ON-unit exception handling** (ON ERROR, REVERT, SIGNAL)
- **PL/I string built-ins** (INDEX, TRANSLATE, COLLATE)
- **PL/I character <-> bit conversion** routines (X.CB)
- The runtime stop/init chain calls `P?DEFON` (PL/I default ON-units)

---

## Architecture & Calling Conventions (MV-32 Eagle)

The MV-32 is a **16-bit word** machine with 32-bit wide operations.

### Registers
- **AC0-AC3**: 32-bit general-purpose accumulators
- **FPAC0-FPAC3**: floating-point accumulators
- **WFP**: wide frame pointer (= AC3 inside a function)
- **WSP**: wide stack pointer
- **WSB**: wide stack base

### Frame Setup
Every function begins with `WSAVS N` or `WSAVR N`, which allocates Nx2
words of locals and saves registers:
```
[fp - 0x0C]  saved PSR
[fp - 0x0A]  saved AC0
[fp - 0x08]  saved AC1
[fp - 0x06]  saved AC2
[fp - 0x04]  saved WFP (caller's frame pointer)
[fp - 0x02]  return address
[fp + 0x00]  first local variable
[fp + 0x02]  second local, etc.
```

### Argument Passing
All arguments passed **by reference**. Caller pushes effective addresses
via XPEF/LPEF before LCALL. Callee accesses them as:
```
@[ac3+0xFFF4]  = *arg1 (first argument)
@[ac3+0xFFF2]  = *arg2
@[ac3+0xFFF0]  = *arg3
```
The `@` prefix means indirect load (dereference the pointer).

### Function Return
`WRTN` restores the caller's frame and returns.

### Exception Handling
PL/I ON-units are implemented via a linked handler chain rooted at
`[stack_base + 0x7FC0]`. Key routines:
- `I.PROLOG` -- install handler frame (followed by 4 JMP words)
- `I.EPILOG` -- remove handler frame + return from enclosing function
- `O.ON` -- register a condition handler
- `O.REVERT` -- deregister a condition handler
- `I.GOTO` -- non-local transfer (signal dispatch / "throw")
- `O.SERROR` -- raise ERROR condition

See `docs/runtime_analysis.md` for detailed analysis of all runtime
routines.

---

## Memory Layout

### Global Pointers (fixed addresses)

| Address | Name | Points To |
|---|---|---|
| 0x70000210 | SD_PTR | SHARED_DATA_FILE (shared memory) |
| 0x70000212 | OBJ_PTR | WORLD_DATA_FILE (shared memory) |
| 0x70000214 | CAS_PTR | CASTLE_DATA_FILE (shared memory) |
| 0x70000216 | PLAYER_NUM | Current player number (1-based, max 10) |
| 0x70000260 | SCREEN_CHAN | Terminal I/O channel |
| 0x70000262 | FILE_CHAN_1 | File I/O channel |
| 0x70000264 | FILE_CHAN_2 | File I/O channel |

### Shared Memory Files

Three memory-mapped regions shared between QUEST and QUEST_SERVER:

| File | Pointer | Contents |
|---|---|---|
| SHARED_DATA_FILE | SD_PTR | Player records (10 x 686 words) + global game state |
| WORLD_DATA_FILE | OBJ_PTR | World objects, rooms, map data |
| CASTLE_DATA_FILE | CAS_PTR | Castle/dungeon data |

**Player record stride:** 686 narrow words (0x02AE). Player N's base
address = `SD_PTR + (N-1) * 686`.

### Disk File

**USER_DATA_FILE** -- persistent player save data, accessed via
`?READ`/`?WRITE` with 980-word records.

See `docs/SHARED_DATA_FILE.md`, `docs/WORLD_DATA_FILE.md`,
`docs/CASTLE_DATA_FILE.md`, `docs/USER_DATA_FILE.md`, and
`docs/QUEST_MEMORY.md` for detailed layout documentation (being
populated incrementally as functions are decompiled).

---

## Project Plan

### Completed
1. DONE MV-32 calling conventions documented
2. DONE Runtime routines analyzed (I.PROLOG, I.EPILOG, O.ON, O.REVERT,
   I.GOTO, O.SERROR, O.SET, all O.Sxxx signal stubs, I.SFALT, I.FFALT,
   DERR.TRP, I.LOCK/I.UNLOCK, B.MOVE, C.INDEX, X.IC, etc.)
3. DONE Array indexing patterns identified (no runtime bounds checking in
   game code; DERR instructions serve as compiler assertions)
4. DONE Project structure and coding standards established

### In Progress
4. WIP Decompile game functions to C++ (see `quest_artifacts/TODO.md`
   for function inventory and status -- 83 game functions identified)
5. WIP Build memory layout models from access patterns

### Future
6. Decompile quest_server functions
7. Build I/O abstraction layer (terminal, file)
8. Build shared memory / IPC layer
9. Compile and test

---

## Project Structure

```
docs/                           # Shared documentation
  CODING.md                     # C++ coding standards (living doc)
  RUNTIME_ANALYSIS.md           # MV-32 PL/I runtime system analysis
  QUEST_MEMORY.md               # Fixed global memory addresses
  SHARED_DATA_FILE.md           # SD_PTR region layout
  WORLD_DATA_FILE.md            # OBJ_PTR region layout
  CASTLE_DATA_FILE.md           # CAS_PTR region layout
  USER_DATA_FILE.md             # Disk-based player save file

quest_artifacts/                # Quest client decompilation
  TODO.md                       # Function inventory + status
  src/                          # C++ output (.hpp + .cpp per function)
  plans/                        # Per-function planning subdirs
    FUNCTION_NAME/
      control_flow.md           # Basic block diagram
      stack_layout.md           # MV-32 frame -> C++ variable mapping
      notes.md                  # Observations, open questions

quest_server_artifacts/         # Quest server decompilation (future)
  (same structure as above)

Source/                         # Java emulator source
  hw/                           # MV-32 instruction set emulation
  os/                           # AOS/VS system call emulation
  Debug/                        # Program loading/dumping tools

Docs/                           # Original AOS/VS documentation

quest/                          # Original binary files
  QUEST.PR, QUEST_SERVER.PR     # Executables
  QUEST.ST, QUEST_SERVER.ST     # Symbol tables
```

---

## Key Reference Files

For a new decompilation session, read these first:
1. **This README** -- project overview and architecture
2. **docs/runtime_analysis.md** -- calling conventions, exception handling,
   runtime routine details, C++ translation cheat sheet
3. **docs/CODING.md** -- naming, formatting, data type mappings
4. **quest_artifacts/TODO.md** -- what's done, what's next

The disassembly source files:
- **quest.compact** -- the primary working file for quest client
- **quest.symbols** -- symbol table (addresses <-> names)
