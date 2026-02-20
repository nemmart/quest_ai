# Quest Decompilation — Coding Standards

This is a living document. Update as new patterns and issues are discovered.

---

## Project Goal

Reverse-engineer the Quest game (Data General MV-32, PL/I) into clean,
readable C++ that is **binary-compatible** with the original data files
(shared memory layouts, save files, world data).

---

## File Organization

```
quest_artifacts/
  src/
    FUNCTION_NAME.hpp    — declaration
    FUNCTION_NAME.cpp    — implementation
  plans/
    FUNCTION_NAME/
      control_flow.md    — basic block diagram, branch logic
      stack_layout.md    — original MV-32 frame layout, variable mapping
      notes.md           — observations, open questions
  CODING.md              — this file
```

- One `.hpp` / `.cpp` pair per original PL/I procedure.
- Helper/utility functions that don't correspond to a single original
  routine go in appropriately named files (e.g., `quest_types.hpp`).

---

## Formatting

- **Indentation:** 2 spaces. No tabs.
- **Braces:** K&R style (opening brace on same line).
- **Line length:** soft limit 100 columns.
- **Naming:**
  - Original routine names preserved as comments: `// MV-32: ALLY_PLAYER @ 0x7015D053`
  - C++ function names: `snake_case` (e.g., `ally_player`, `get_input`)
  - Constants/enums: `UPPER_SNAKE_CASE`
  - Struct/class members: `snake_case`
  - Types: `PascalCase` for structs/classes (e.g., `PlayerRecord`, `SharedData`)

---

## Data Types

The MV-32 is a **16-bit word** machine with 32-bit wide operations.

| MV-32 Type | C++ Type | Notes |
|---|---|---|
| narrow word (16-bit) | `int16_t` / `uint16_t` | Used for most game values |
| wide word (32-bit) | `int32_t` / `uint32_t` | Pointers, large values |
| byte | `uint8_t` / `char` | Character data |
| single-precision float | `float` | DG 32-bit FP ≈ IEEE 754 single |
| double-precision float | `double` | DG 64-bit FP |
| PL/I CHAR(N) | `char[N]` or `std::array<char,N>` | Fixed-length, space-padded, NOT null-terminated |
| PL/I BIT(N) | Bitfield or `uint16_t` | Packed booleans |

### String Conventions

PL/I strings are **fixed-length, space-padded**, NOT null-terminated.
When interfacing with C++ standard library functions, convert explicitly.
For data-file compatibility, keep internal representation as fixed-length
char arrays in structs that map to shared memory.

---

## Shared Memory Compatibility

Structs that map to shared memory or data files **must** match the
original byte layout exactly:

```cpp
#pragma pack(push, 1)
struct PlayerRecord {
  int16_t field_at_0x00;
  int16_t field_at_0x02;
  // ...
};
#pragma pack(pop)
static_assert(sizeof(PlayerRecord) == EXPECTED_SIZE);
```

- Use `#pragma pack(push, 1)` for all shared-memory structs.
- Add `static_assert` on struct sizes.
- Document the original byte offset for every field.
- Use fixed-width integer types (`int16_t`, `int32_t`, etc.).

---

## Exception Handling

The original PL/I uses ON-units (see `runtime_analysis.md`). Translate to
C++ exceptions:

| PL/I Pattern | C++ Translation |
|---|---|
| `ON ERROR BEGIN; ...; END;` | `try { ... } catch (const QuestError& e) { ... }` |
| `REVERT ERROR;` | (implicit — handler scope ends) |
| `SIGNAL ERROR;` | `throw QuestError(code);` |

For simple cases where the ON-unit just prints a message and continues,
a `try/catch` around the risky call is sufficient.

---

## Runtime Library Calls

Replace MV-32 runtime calls with C++ equivalents:

| Runtime Call | C++ Replacement |
|---|---|
| `?WRITE_SCREEN` | `write_screen(text, len)` — wrapper TBD |
| `?READ_SCREEN` | `read_screen(buf, len)` — wrapper TBD |
| `?OPEN_FILE` | `fopen()` or C++ streams |
| `?READ` / `?WRITE` | `fread()` / `fwrite()` |
| `?CLOSE_FILE` | `fclose()` |
| `?DELAY` | `usleep()` / `std::this_thread::sleep_for` |
| `?RANDOM_NUMBER` | `rand()` or `<random>` |
| `I.LOCK` / `I.UNLOCK` | `std::mutex` or file locks |
| `SQR31?3` | `sqrtf()` |
| `X.IC` | `snprintf(buf, n, "%d", val)` |
| `C.INDEX` | `std::string::find() + 1` (1-based) |
| `I.STOP` | `exit(0)` |

---

## Control Flow Translation

- **DERR N**: hardware trap — in original code used as an assertion/
  unreachable marker. Translate as: `assert(condition)` or omit if
  the check is redundant in C++.
- **XNDO reg,count,[addr]**: narrow loop-decrement. Used for
  `DO I = start TO end` loops.
- **WBR / XJMP**: translate to structured `if`/`else`/`while`/`for`.
  Avoid `goto` unless the original control flow is genuinely irreducible.

---

## Comments

- Every function gets a header comment with:
  - Original name and address: `// MV-32: FUNCTION_NAME @ 0xADDRESS`
  - Brief description of what it does
  - Parameter mapping (which MV-32 args become which C++ params)
- Inline comments for non-obvious translations.
- Do NOT comment obvious code.

---

## Decompilation Workflow

1. **Read** the disassembly for the target routine.
2. **Plan** (for complex routines): create `plans/ROUTINE/` with
   `control_flow.md` and `stack_layout.md`.
3. **Write** the C++ in `src/ROUTINE.hpp` + `src/ROUTINE.cpp`.
4. **Cross-reference** calls to/from this routine to verify parameter
   passing and return values.

---

## Known Patterns

### Player Index Calculation
```
player_index (1-based) → offset = (player_index - 1) * 686
base = SD_PTR (SHARED_DATA_FILE @ 0x70000210)
player_record = base + offset
```
686 = 0x02AE = size of one player record in narrow words.

### Array Bounds Checking
The game does NOT use runtime bounds checking (O.SSUBSC never called).
DERR instructions serve as compiler-inserted assertions but are not
reachable in normal execution. We can translate them as `assert()` or
omit them.

### Variable Reuse
The PL/I compiler reuses stack slots for variables with non-overlapping
lifetimes. The same frame offset may hold different variables in
different code blocks. Track per-block and use distinct C++ variable
names.

---

## Open Issues

- [ ] Exact struct layouts for PlayerRecord, SharedData, WorldData,
      CastleData — need to map field by field from access patterns.
- [ ] Terminal I/O abstraction (?WRITE_SCREEN / ?READ_SCREEN) — need
      to decide on ncurses, raw ANSI, or abstraction layer.
- [ ] Shared memory IPC replacement strategy — mmap? sockets?
- [ ] DG floating-point format vs IEEE 754 — are there edge cases?

---

## Revision History

- **Session 3**: Initial version.
