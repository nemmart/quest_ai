# MV-32 PL/I Runtime System Analysis

## Reference for Quest/Quest_Server Decompilation

This document provides a complete analysis of the PL/I runtime system used by the Quest binaries, covering calling conventions, exception handling, utility routines, and their C++ translation patterns.

---

## 1. Calling Conventions

### 1.1 Frame Setup: WSAVS / WSAVR

Every function begins with `WSAVS N` (save static) or `WSAVR N` (save re-entrant):
- Allocates N×2 words of local variable space on the stack
- Saves registers at **negative offsets** from the new frame pointer (wfp = ac3)

**Frame layout after WSAVS N:**
```
[fp - 0x0C]  saved PSR (processor status register)
[fp - 0x0A]  saved AC0
[fp - 0x08]  saved AC1
[fp - 0x06]  saved AC2
[fp - 0x04]  saved WFP (previous frame pointer)
[fp - 0x02]  return address
[fp + 0x00]  first local variable (wide slot)
[fp + 0x02]  second local variable
  ...
[fp + 2*(N-1)]  Nth local variable
```

### 1.2 Argument Passing

Arguments are passed **by reference**. Before LCALL/XCALL, the caller pushes effective addresses using XPEF/LPEF:
```asm
XPEF [ac3+offset1]     ; push address of arg 3 (rightmost)
XPEF [ac3+offset2]     ; push address of arg 2
XPEF [ac3+offset3]     ; push address of arg 1 (leftmost)
LCALL [target],3        ; call with 3 arguments
```

Inside the callee, arguments are accessed via **negative offsets from fp** through indirection:
```
@[ac3+0xFFF4]  = *arg1  (first argument, dereferenced)
@[ac3+0xFFF2]  = *arg2  (second argument)
@[ac3+0xFFF0]  = *arg3  (third argument)
```

These offsets are: 0xFFF4 = fp-12, 0xFFF2 = fp-14, 0xFFF0 = fp-16, etc. The `@` prefix means indirect (dereference the pointer to get the actual value).

### 1.3 Function Return: WRTN

`WRTN` reverses the WSAVS/WSAVR frame: restores registers, frame pointer, stack pointer, and jumps to the saved return address.

### 1.4 SYSCALL Convention

```asm
SYSCALL 0NNN       ; system call number in octal
ADC r,r,SKP        ; skip next on success (carry set)
WSUB r,r           ; fall through on failure (carry clear) → error path
```

Special case: `SYSCALL 0310` = terminate process (never returns).

---

## 2. Exception Handling System

This is a PL/I ON-unit implementation using a linked handler chain rooted at `[stack_base + 0x7FC0]`.

### 2.1 Stack Base Reserved Area

The bottom of each task's stack has reserved words for runtime state:

| Offset from stack_base | Contents |
|---|---|
| `+0x7FC0` | Handler chain head pointer (linked list of active ON-unit frames) |
| `+0x7FC2` | Current signal info word 0 (set by O.SET) |
| `+0x7FC4` | Current signal info word 1 |
| `+0x7FC6` | Current signal info word 2 |
| `+0x7FCC` | Stack fault recovery pointer |
| `+0x7FCE` | Stack fault saved frame |
| `+0x7FD0` | Stack fault flag |
| `+0x7FD4` | Last error code (written by DERR.TRP) |
| `+0x7FD6` | Signal origin task area |
| `+0x7FD7` | Signal/error area (8 words from here = T?AREA) |

### 2.2 I.PROLOG — Install Handler Frame

**Address:** 0x7017E733 (quest), 0x7017E935 (quest_server)

**Calling pattern:**
```asm
WSAVS N                    ; function entry (sets up frame, ac3 = fp)
LJSR [I.PROLOG]            ; ac3 overwritten with return addr = address of JMP1
JMP  target_or_0           ; on-unit target 1 (word 0)
JMP  target_or_0           ; on-unit target 2 (word 1)
JMP  target_or_0           ; on-unit target 3 (word 2)
JMP  display_depth_or_0    ; word 3: display depth count
; ...execution continues here after I.PROLOG returns...
```

**Detailed walkthrough:**
```asm
7017e733 WSUB 0,0           ; ac0 = 0
7017e734 WPSH 0,1           ; push ac0(=0) and ac1 onto stack
7017e735 LDAFP 2            ; ac2 = frame pointer (caller's frame)
7017e736 LDASP 0            ; ac0 = current stack pointer
7017e737 XWSTA 0,[ac2+0x2]  ; frame[+0x2] = stack pointer
7017e739 WSBI 2,0           ; ac0 = ac0 - 2
7017e73a XWSTA 0,[ac2+0xA]  ; frame[+0xA] = sp-2
7017e73c XWLDA 0,[ac3+0x0]  ; ac0 = WIDE read of JMP words 1&2 (packed)
7017e73e XWSTA 0,[ac2+0x4]  ; frame[+0x4] = JMP targets 1&2
7017e740 XNLDA 0,[ac3+0x2]  ; ac0 = NARROW read of JMP word 3
7017e742 XWSTA 0,[ac2+0x6]  ; frame[+0x6] = JMP target 3 (sign-extended)
7017e744 XNLDA 1,[ac3+0x3]  ; ac1 = NARROW read of word 4 (display depth)
7017e746 XPEF [ac3+0x4]     ; push return_addr+4 = true return address
7017e748 LDASB 3            ; ac3 = stack base
7017e749 XWLDA 0,[ac3+0x7FC0] ; ac0 = current handler chain head
7017e74b XWSTA 2,[ac3+0x7FC0] ; chain head = ac2 (caller's frame pointer)
7017e74d XWSTA 0,[ac2+0x8]  ; frame[+0x8] = previous chain head
7017e74f WBR 31             ; branch to I.DISPLA (display pointer setup)
```

**Prolog record stored in caller's frame:**

| Frame Offset | Contents |
|---|---|
| `[fp+0x02]` | Saved stack pointer |
| `[fp+0x04]` | On-unit JMP targets 1 & 2 (packed as one wide word) |
| `[fp+0x06]` | On-unit JMP target 3 (sign-extended to wide) |
| `[fp+0x08]` | Previous handler chain link |
| `[fp+0x0A]` | SP-2 at install time |
| `[fp+0x0C+]` | Display pointers (for nested scope access) |

### 2.3 I.WPROLO — Wide Prolog Variant

**Address:** 0x7017E750

Same as I.PROLOG but reads the on-unit targets differently:
- Wide read at [return+0] → 2 words packed → `[fp+0x4]`
- Wide read at [return+2] → next 2 words packed → `[fp+0x6]`
- Narrow read at [return+4] → display depth (ac1)
- Effective return at [return+5]

This gives 4 independent on-unit target slots instead of 3+depth.

### 2.4 I.DISPLA — Display Pointer Setup

**Address:** 0x7017E766

```asm
7017e766 LDAFP 2            ; ac2 = frame pointer
7017e767 LDASP 0            ; ac0 = stack pointer
7017e768 XWSTA 0,[ac2+0x2]  ; update saved SP in frame
7017e76a XNLDA 1,[ac3+0x0]  ; ac1 = display depth from after LJSR
7017e76c XPEF [ac3+0x1]     ; push real return address
7017e76e XWLDA 3,[ac2+0x7FFA] ; ac3 = [fp-6] = saved AC2 = lexical parent fp
7017e770 XLEF 2,[ac2+0xC]   ; ac2 = &frame[+0xC] (display area start)
7017e772 WSBI 1,1           ; depth--
7017e773 WSGT 1,1           ; if depth > 0
7017e774 WBR 7              ; ...exit loop
7017e775 XWLDA 3,[ac3+0x7FFA] ; ac3 = parent's parent fp (walk chain)
7017e777 XWSTA 3,[ac2+0x0]  ; store ancestor's fp in display
7017e779 WADI 2,2           ; advance display pointer
7017e77a WBR -8             ; loop
7017e77b LDAFP 3            ; restore ac3 = frame pointer
7017e77c WPOPJ              ; return (pop return addr from stack)
```

Walks the static scope chain to set up display pointers for nested procedure access. For Quest, most functions appear to be at nesting level 0-1, so this rarely loops.

### 2.5 I.EPILOG — Remove Handler Frame (Normal Exit)

**Address:** 0x7017E77D (quest), 0x7017E97F (quest_server)

```asm
7017e77d LDAFP 3            ; ac3 = frame pointer
7017e77e XWLDA 0,[ac3+0x8]  ; ac0 = saved previous handler chain head
7017e780 LDASB 2            ; ac2 = stack base
7017e781 XWSTA 0,[ac2+0x7FC0] ; restore handler chain head
7017e783 WRTN               ; return from ENCLOSING function
```

**Critical:** I.EPILOG is called via `LJSR [I.EPILOG]`, but it does not return to its caller. Instead, it does WRTN which returns from the function that originally called I.PROLOG. This is the "no exception occurred, clean up and exit" path.

### 2.6 I.GOTO — Signal Dispatch / Non-local Transfer

**Address:** 0x7017EC7C (quest)

**Entry conditions:**
- `ac0` = target frame pointer (loaded from on-unit info, or 0)
- `ac2` = address to jump to (handler code or resume point)
- Called via `LJSR [I.GOTO]`

**R.GOTO** at 0x7017EC7B: One-instruction wrapper (`WBR 3`) that enters I.GOTO at the frame-comparison point, used for computed GOTOs that bypass the LJSR setup.

**Detailed walkthrough:**
```asm
7017ec7c WMOV 3,1           ; ac1 = ac3 (save return addr from LJSR)
7017ec7d LDAFP 3            ; ac3 = current frame pointer (wfp register)
7017ec7e WSNE 0,3           ; if target_frame (ac0) != current_frame (ac3)
7017ec7f WBR 33 (ECA0)      ;   branch to "direct jump" at [ac2+0x0]

; --- target == current frame: need to unwind handler chain ---
7017ec80 WPSH 1,2           ; save return addr and target addr
7017ec81 LDASB 2            ; ac2 = stack base
7017ec82 XWLDA 1,[ac2+0x7FC0] ; ac1 = handler chain head
7017ec84 WSEQ 3,1           ; if current_fp == chain_head
7017ec85 WBR 3              ;   skip (chain head IS this frame)
7017ec86 XWLDA 1,[ac3+0x8]  ; ac1 = this frame's previous handler link

; Walk frames looking for the target
7017ec88 WMOV 3,2           ; ac2 = current fp
7017ec89 XWLDA 3,[ac2+0x7FFE] ; ac3 = [fp-2] = return address of frame
7017ec8b WSLT 3,2           ; if return_addr < fp (cross-segment?)
7017ec8c WBR 22 (ECA2)      ;   branch to segment validation
7017ec8d WSGT 3,3           ; if return_addr > 0 (valid)
7017ec8e WBR 47 (ECBD)      ;   branch to "ON not found" error
7017ec8f WSEQ 3,0           ; if return_addr == target_frame
7017ec90 WBR -12 (EC84)     ;   keep walking
; Found target frame:
7017ec91 LDASB 3            ; ac3 = stack base
7017ec92 XWSTA 1,[ac3+0x7FC0] ; set chain head = previous handler
7017ec94 XLEF 0,[pc+0x8] (EC9D) ; ac0 = address of cleanup code
7017ec96 XWSTA 0,[ac2+0x0]  ; store cleanup addr in frame[+0x0]
7017ec98 WPOP 0,0           ; restore return addr
7017ec99 XWSTA 0,[ac2+0x7FFC] ; save in frame[+0x7FFC] = [fp-4]
7017ec9b STAFP 2            ; set frame pointer = target frame
7017ec9c WRTN               ; return (into target frame)
; Cleanup code (runs after WRTN transfers to target frame):
7017ec9d XWLDA 0,[ac3+0x2]  ; ac0 = saved SP from prolog record
7017ec9f STASP 0            ; restore stack pointer
7017eca0 XJMP [ac2+0x0]     ; jump to target address

; --- Segment validation path ---
7017eca2 WANDI 3,0x70000000  ; check segment bits
7017eca5 WSGT 3,2           ; compare
7017eca6 WBR 23 (ECBD)      ; error if mismatch
7017eca7 XWLDA 3,[ac3+0x124] ; load scope info from frame
; ... (additional validation for nested scopes) ...

; Error paths:
7017ecbd WLDAI 0x00011614   ; "ON handler not found" error
7017ecc0 WBR 4 (ECC4)
7017ecc1 WLDAI 0x00011635   ; "Frame not in handler chain" error
7017ecc4 WPOP 0,3           ; restore registers
7017ecc5 WSSVS 0x0000       ; save frame
7017ecc7 LCALL [O.SERROR],0 ; raise ERROR condition
```

### 2.7 O.ON — Register Condition Handler

**Address:** 0x7017ED9B

**Calling pattern:**
```asm
WSUB 1,1                   ; ac1 = 0 (handler type flags)
WADC 0,0                   ; ac0 = -1 (all conditions)
XLEF 2,[handler_addr]       ; ac2 = address of handler code
LJSR [O.ON]
WBR skip_handler            ; skip over inline handler code
; handler code here (WSAVS...; ...; WRTN)
skip:
```

**Detailed walkthrough:**
```asm
7017ed9b WSSVS 0x0004       ; allocate 4 local wide words
7017ed9d XWLDA 2,[ac3+0x7FFE] ; ac2 = return address (= calling frame context)
7017ed9f XJSR [0x7017EE7A]  ; call internal search routine
7017eda1 WBR 3               ; on return: branch based on result
7017eda2 WSNE 1,1            ; if ac1 != 0 (existing handler found)
7017eda3 WBR 12              ; branch to "replace existing" path

; --- No existing handler: create new handler entry ---
7017eda4 XLEF 2,[ac3+0x7FF8] ; ac2 = &local[save area]
7017eda6 WMOV 1,3            ; ac3 = handler record pointer
7017eda7 XLEF 3,[ac3+0x2]    ; ac3 = &handler_record[+0x2]
7017eda9 NLDAI 6,1           ; ac1 = 6 (copy 6 words)
7017edab WBLM                ; block move: save existing record
7017edac XLEF 3,[ac3+0x7FF8] ; restore ac3
7017edae WBR 23              ; jump to "finalize" at 0x7017EDC5

; --- Replace existing handler path ---
7017edaf NLDAI 0xFFF4,1      ; ac1 = -12 (offset for block copy)
7017edb1 WINC 3,2            ; ac2 = ac3 + 1 (adjust pointer)
7017edb2 XLEF 3,[ac2+0x8]    ; ac3 = &frame[+0x8]
7017edb4 WBLM                ; block move (create handler frame)
7017edb5 LDASP 3             ; ac3 = stack pointer
7017edb6 STAFP 3             ; set frame pointer = stack pointer
7017edb7 XWLDA 2,[ac3+0x7FFE] ; ac2 = return address
7017edb9 XLEF 3,[ac3+0x7FF4]  ; set up handler chain link
7017edbb XWSTA 3,[ac2+0x2]   ; store in record
7017edbd XLEF 3,[ac3+0x7FFA]  ; get chain head location
7017edbf XWLDA 1,@[ac2+0x800A] ; read current chain pointer
7017edc1 XWSTA 1,[ac3+0x0]   ; save in new record
7017edc3 XWSTA 3,@[ac2+0x800A] ; update chain to point to new record

; --- Finalize ---
7017edc5 WSLE 0,0            ; if ac0 <= 0 (condition-specific handler)
7017edc6 WBR 4               ; skip clearing
7017edc7 WSUB 1,1            ; ac1 = 0
7017edc8 XWSTA 1,[ac3+0x4]   ; handler[+0x4] = 0 (mark as "system default")
7017edca WRTN                ; return
```

### 2.8 O.REVERT — Deregister Condition Handler

**Address:** 0x7017EDCB

```asm
7017edcb WSSVR 0x0000       ; lightweight frame (no locals)
7017edcd XWLDA 3,[ac3+0x7FFE] ; ac3 = calling frame's return address
7017edcf LDASB 2             ; ac2 = stack base
7017edd0 XWLDA 2,[ac2+0x7FC0] ; ac2 = handler chain head
7017edd2 WSEQ 2,3            ; if chain_head == calling_frame
7017edd3 WBR 9 (EDDC)        ; branch to "not current" → just return
7017edd4 XJSR [0x7017EE7A]   ; call internal search
7017edd6 WBR 2                ; skip next on search result
7017edd7 WBR 5 (EDDC)        ; not found → return
7017edd8 WMOV 1,3            ; ac3 = handler record pointer
7017edd9 WSUB 0,0            ; ac0 = 0
7017edda XWSTA 0,[ac3+0x2]   ; clear handler action: handler[+0x2] = 0
7017eddc WRTN                ; return
```

### 2.9 Signal Raising Routines

#### O.SIGNAL (0x7017EDE7)
```asm
7017ede7 WSSVS 0x0000
7017ede9 WLDAI 0x00011601    ; signal ID for user SIGNAL condition
7017edec WBR 76 (0x7017EE38) ; fall into common dispatch
```

#### O?SIGNAL (0x7017EDED)
Internal signal with 3 arguments by reference:
```asm
7017eded WSAVS 0x0000
7017edef XNLDA 0,[ac3+0x7FF7] ; arg count check
7017edf1 LDASB 2
7017edf2 WSGTI 0,3           ; if > 3 args
7017edf4 WBR 4               ;   branch to "read 4th arg"
7017edf5 XNLDA 0,@[ac3+0xFFEE] ; read 4th arg (task area)
7017edf7 WBR 2
7017edf8 WSUB 0,0            ; default = 0
7017edf9 XWSTA 0,[ac2+0x7FD6] ; store in signal origin area
7017edfb XWLDA 0,@[ac3+0xFFF4] ; ac0 = arg1 (signal code)
7017edfd XWLDA 1,@[ac3+0xFFF2] ; ac1 = arg2 (condition info)
7017edff XWLDA 2,@[ac3+0xFFF0] ; ac2 = arg3 (handler addr)
7017ee01 WBR 55 (0x7017EE38)   ; fall into common dispatch
```

#### R?SIGNAL / ?ERROR (0x7017EF54)
Main signal-and-dispatch:
```asm
7017ef54 WSAVS 0x0000
7017ef56 XNLDA 2,[ac3+0x7FF7] ; ac2 = arg count
7017ef58 WSLE 2,2             ; if arg_count <= 0
7017ef59 WBR 7 (EF60)         ;   use caller's args
; Use signal info from O.SET area:
7017ef5a LDASB 2
7017ef5b XWLDA 0,[ac2+0x7FC6] ; ac0 = signal word 2
7017ef5d XWLDA 1,[ac2+0x7FC2] ; ac1 = signal word 0
7017ef5f WBR 10 (EF69)
; Use caller's args:
7017ef60 XWLDA 0,@[ac3+0xFFF4] ; ac0 = arg1
7017ef62 NLDAI 0xFFFF,1        ; ac1 = -1 (default mask)
7017ef64 WSBI 2,2              ; arg_count -= 2
7017ef65 WSEQ 2,2              ; if no more args
7017ef66 WBR 3 (EF69)          ;   skip
7017ef67 XWLDA 1,@[ac3+0xFFF2] ; ac1 = arg2

; --- Walk handler chain ---
7017ef69 WMOV 3,2              ; ac2 = fp (start frame)
7017ef6a XWLDA 3,[ac2+0x7FFE] ; ac3 = [fp-2] = return addr
7017ef6c WSNE 3,3              ; if return_addr != 0
7017ef6d WBR 32 (EF8D)         ;   no more frames → WRTN
7017ef6e WSGT 3,2              ; if return_addr > fp
7017ef6f WBR -6 (EF69)         ;   skip this frame, continue
; Found handler frame:
7017ef70 XWLDA 3,[ac2+0x0]    ; ac3 = frame[+0x0] (first local)
7017ef72 XWSTA 0,[ac2+0x7FFC] ; store signal code in frame[fp-4]
7017ef74 XWSTA 3,[ac2+0x7FFA] ; store frame[+0x0] in [fp-6]
7017ef76 XWSTA 1,[ac2+0x7FF8] ; store mask in [fp-8]
; Validate frame pointer segment:
7017ef78 WANDI 3,0x70000000   ; check segment bits
7017ef7b XWLDA 3,[ac3+0x124]  ; load scope validation
7017ef7d WSGT 3,3             ; check validity
7017ef7e WBR 11 (EF89)        ;   invalid → call ?FATAL
; Transfer to handler:
7017ef7f XWLDA 3,[ac3+0x0]   ; get handler code address
7017ef81 XWSTA 3,[ac2+0x0]   ; store in frame[+0x0]
7017ef83 LDASB 3              ; ac3 = stack base
7017ef84 WSUB 0,0             ; ac0 = 0
7017ef85 XWSTA 0,[ac3+0x7FC0] ; clear handler chain head (= 0)
7017ef87 STAFP 2              ; set frame pointer = handler frame
7017ef88 WRTN                 ; transfer control to handler
; Fatal case:
7017ef89 LCALL [?FATAL],0
7017ef8d WRTN
```

#### O.SERROR (0x7017EE33)
Raises ERROR condition:
```asm
7017ee33 WSAVS 0x0000
7017ee35 NLDAI 0xFFFF,0       ; ac0 = -1 (all handlers)
7017ee37 WSUB 1,1             ; ac1 = 0
7017ee38 XCALL 0,0,[O.SET]    ; store signal info
7017ee3b XPSHJ [0x7017EE62]   ; push internal dispatch address
7017ee3d XCALL 0,0,[ac2+0x0]  ; call the handler search/dispatch
7017ee40 LDASB 2              ; (returns here if handler ran)
7017ee41 XWLDA 2,[ac2+0x7FD6] ; check signal origin
7017ee43 WSEQ 2,2             ; if origin == 0
7017ee44 WRTN                 ;   handler succeeded, return
7017ee45 WSGT 0,0             ; if ac0 > 0
7017ee46 WBR 2                ;   skip
7017ee47 WRTN                 ;   return (handled)
; Re-raise as ERROR-on-ERROR:
7017ee48 WLDAI 0x00011618    ; "unhandled error" signal ID
7017ee4b INC.# 0,0,SZR       ; increment and check
7017ee4c WBR -23 (EE35)      ; retry with new error code
; Check for ERROR-on-ERROR loop:
7017ee4d LDASB 3
7017ee4e XWLDA 0,[ac3+0x7FC6] ; load current signal word 2
7017ee50 WSEQ 0,2             ; if matches origin
7017ee51 WBR -28 (EE35)       ; re-raise
7017ee52 LCALL [I.STOP],0     ; terminate (infinite loop protection)
```

#### O.SET (0x7017EE56)
```asm
7017ee56 WSAVS 0x0000
7017ee58 XJSR [0x7017EE9D]   ; call internal decoder
7017ee5a LDASB 3
7017ee5b XWSTA 0,[ac3+0x7FC2] ; [stackbase+0x7FC2] = signal info 0
7017ee5d XWSTA 1,[ac3+0x7FC4] ; [stackbase+0x7FC4] = signal info 1
7017ee5f XWSTA 2,[ac3+0x7FC6] ; [stackbase+0x7FC6] = signal info 2
7017ee61 WRTN
```

### 2.10 Condition-Specific Signal Stubs (O.Sxxx)

Each stub sets a condition selector (ac0) and signal identifier (ac2), then falls into O.SERROR:

| Routine | ac0 | Signal ID (ac2) | PL/I Condition |
|---|---|---|---|
| O.SUNDER (0x7017EE07) | -4 | 0x00011616 | UNDERFLOW |
| O.SOVERF (0x7017EE0F) | -3 | 0x00011607 | OVERFLOW |
| O.SZEROD (0x7017EE17) | -5 | 0x00011608 | ZERODIVIDE |
| O.SFIXED (0x7017EE1F) | -2 | 0x00011606 | FIXEDOVERFLOW |
| O.SSUBSC (0x7017EE27) | — | 0x00011612 | SUBSCRIPTRANGE |
| O.SCONVE (0x7017EE2D) | — | 0x00011611 | CONVERSION |
| O.SERROR (0x7017EE33) | -1 | (from caller) | ERROR |
| O.SIGNAL (0x7017EDE7) | — | 0x00011601 | SIGNAL (user) |

Common flow: ac0 selects handler slot → WSUB 1,1 → XCALL O.SET → search+dispatch.

### 2.11 Hardware Fault Handlers

**I.SFALT** (0x7017EBC0): Stack overflow handler. Attempts recovery via R?SIGNAL; if unhandled, calls ?FATAL.

**I.SFCON** (0x7017EC39): Stack fault continuation. Dispatches based on fault type:
- ac1=1: Resume (SYSCALL 014 to grow stack)
- ac1=3: Fatal (abort)
- Other: Inspect faulting instruction, compute needed stack growth

**I.FFALT** (0x7017ECCC): FPU fault handler. Examines FPU status flags:
- FSNM (not mantissa) → O.SUNDER
- FSNO (no overflow) → O.SOVERF  
- FSND (no divide-by-zero) → O.SZEROD
- Default → O.SFIXED

**DERR.TRP** (0x7017ED1C): Hardware trap handler. Uses dispatch table at 0x7017ED79 for trap-type decoding. Calls O.SERROR with appropriate error code. Stores error to C.ERRNO.

---

## 3. Complete Usage Pattern: PL/I Source ↔ Assembly ↔ C++

### 3.1 Example from ALLY_PLAYER (0x7015D053)

**Assembly:**
```asm
ALLY_PLAYER:
    WSAVS 0x0130                     ; 304 words of locals
    LJSR [I.PROLOG]                  ; install handler frame
    JMP / JMP / JMP / JMP            ; 4 on-unit target words
    ; ... setup ...
    WSUB 1,1                         ; ac1 = 0
    WADC 0,0                         ; ac0 = -1 (ERROR condition)
    XLEF 2,[error_handler_code]      ; ac2 = handler address
    LJSR [O.ON]                      ; register handler
    WBR skip_handler
error_handler_code:
    WSAVS 0x0009                     ; handler frame
    ; ... call ?WRITE_SCREEN ...     ; display error message
    XWLDA 0,[ac3+0x7FFA]            ; load resume frame info
    XLEF 2,[resume_addr]             ; where to resume
    LJSR [I.GOTO]                    ; non-local goto to resume
skip_handler:
    ; ... call ?OPEN_FILE ...        ; the risky operation
    WMOV 0,1                         ; copy condition
    WADC 0,0                         ; ac0 = -1
    LJSR [O.REVERT]                  ; deregister handler
    ; ... continue ...
    LJSR [I.EPILOG]                  ; cleanup + return
```

**Equivalent PL/I:**
```pli
ALLY_PLAYER: PROC;
    ON ERROR BEGIN;
        CALL WRITE_SCREEN('Error occurred');
        GOTO resume_point;
    END;
    
    CALL OPEN_FILE(...);
    
    REVERT ERROR;
resume_point:
    /* ... continue ... */
END ALLY_PLAYER;
```

**C++ Translation:**
```cpp
void ally_player() {
    try {
        open_file(...);
    } catch (const QuestError& e) {
        write_screen("Error occurred");
        // fall through to continue
    }
    // ... continue ...
}
```

---

## 4. Utility Runtime Routines

### 4.1 B.MOVE — Bit Move/Copy (0x7017E5CB)
Copies individual bits between memory. Takes 6 args: source addr, source bit offset, dest addr, dest bit offset, count, direction. Uses WBTO/WBTZ (bit test-and-set/clear).

**C++:** Bitfield copy operations for packed boolean arrays.

### 4.2 C.INDEX — String Search (0x7017E5F4)
PL/I `INDEX(string, substring)`. Uses WCST (string compare) and WCMP. Returns 1-based position or 0 if not found.

**C++:** `str.find(substr) + 1` (with 0 for not-found).

### 4.3 C.TRANS — Character Translate (0x7017E64A)
PL/I `TRANSLATE(string, to, from)`.

### 4.4 X.CB — Character to Bit (0x7017E708)
Uses WCLM for bounds checking. Calls O.SCONVE on out-of-range characters.

### 4.5 X.IC / X.AIC — Integer to Character (0x7017FC50 / 0x7017FC4D)
Converts binary integer to decimal character string. Uses repeated division by 10 (constant 0x000A at 0x7017FCAA). Handles negative numbers (inserts '-'), pads with spaces.

**C++:** `snprintf(buf, size, "%d", value)`.

### 4.6 D.MOD / F.MOD — Floating Modulo (0x7017E722)
`a MOD b` using FDD, FINT, FMD, FSD. **C++:** `fmod(a, b)`.

### 4.7 I.LOCK / I.UNLOCK — Shared Memory Locking

**I.LOCK** (0x7017E7D0):
1. Atomic bit test-and-set via WSZBO on lock word
2. If contention: SYSCALL 0525 (wait/yield) and retry
3. Success: clear lock data, decrement counter

**I.UNLOCK** (0x7017E7ED):
1. WMESS with mask 0xFFFF to release lock
2. If waiters: SYSCALL 0523 (wake)

**C++:** `std::mutex::lock()` / `unlock()` or POSIX file locks for cross-process.

---

## 5. Array Access Patterns

Quest does NOT use runtime bounds checking (O.SSUBSC is never called from game code). Arrays are accessed via computed offsets:

**Word-element array:** `XWLDA/XNLDA reg,[base + index*element_size]`

**Byte string access:** `XLDB/XSTB reg,[base + byte_offset]`

**Shared memory via pointer:** Load pointer from fixed location, then index into it.

**WCLM instruction** (bounds check): Only used in runtime library (X.CB, SWAT.REX), not in game code. Tests value against bounds, branches to one of 4 JMP targets.

---

## 6. Stack Frame Variable Reuse

The PL/I compiler reuses frame slots for variables with non-overlapping lifetimes. The same offset may hold different types in different code blocks. **Strategy:** Track usage per basic block, assign block-specific variable names.

---

## 7. Program Lifecycle

**Startup:** I.START → LANG?INIT (→ SWAT.NIN + language stubs) → ?SCOPE_INIT → DEF?ON → user main

**Shutdown:** I.STOP → LANG?STOP (→ language stubs) → SYSCALL 0310 (terminate)

---

## 8. C++ Translation Cheat Sheet

| Runtime Routine | C++ Translation |
|---|---|
| `WSAVS N` / `WRTN` | Function entry/return |
| `I.PROLOG + 4 JMPs` | `try {` |
| `I.EPILOG` | `}` + return |
| `O.ON` | Register `catch` handler |
| `O.REVERT` | Remove `catch` handler |
| `I.GOTO` | `throw` / non-local `goto` |
| `O.SERROR` | `throw QuestError(code)` |
| `O.SSUBSC` | `throw out_of_range()` |
| `O.SCONVE` | `throw invalid_argument()` |
| `I.STOP` | `exit(0)` |
| `I.LOCK` / `I.UNLOCK` | `mutex.lock()` / `unlock()` |
| `B.MOVE` | Bitfield copy |
| `C.INDEX` | `string::find()` |
| `X.IC` | `snprintf("%d")` |
| `?WRITE_SCREEN` | Terminal output |
| `?READ_SCREEN` | Terminal input |
| `?OPEN_FILE` | `fopen()` |
| `?READ` / `?WRITE` | `fread()` / `fwrite()` |
| `?CLOSE_FILE` | `fclose()` |
| `?DELAY` | `usleep()` |
| `?RANDOM_NUMBER` | `rand()` |
| `?FILL_WORDS` | `memset()` (word-aligned) |
| `?CURRENT_PID` | `getpid()` |

---

## 9. Key Addresses (Quest Binary)

| Address | Routine | Address | Routine |
|---|---|---|---|
| 0x7017E5CB | B.MOVE | 0x7017EE07 | O.SUNDER |
| 0x7017E5F4 | C.INDEX | 0x7017EE0F | O.SOVERF |
| 0x7017E64A | C.TRANS | 0x7017EE17 | O.SZEROD |
| 0x7017E708 | X.CB | 0x7017EE1F | O.SFIXED |
| 0x7017E722 | D.MOD | 0x7017EE27 | O.SSUBSC |
| 0x7017E733 | I.PROLOG | 0x7017EE2D | O.SCONVE |
| 0x7017E750 | I.WPROLO | 0x7017EE33 | O.SERROR |
| 0x7017E766 | I.DISPLA | 0x7017EE56 | O.SET |
| 0x7017E77D | I.EPILOG | 0x7017EF05 | DEF?ON |
| 0x7017E7D0 | I.LOCK | 0x7017EF51 | R.SIGNAL |
| 0x7017E7ED | I.UNLOCK | 0x7017EF54 | R?SIGNAL |
| 0x7017EBC0 | I.SFALT | 0x7017FC4D | X.AIC |
| 0x7017EC39 | I.SFCON | 0x7017FC50 | X.IC |
| 0x7017EC7B | R.GOTO | 0x7017FCAB | F.STOP |
| 0x7017EC7C | I.GOTO | 0x7017FCE8 | I.STOP |
| 0x7017ECCC | I.FFALT | 0x7017FD16 | LANG?INIT |
| 0x7017ED1C | DERR.TRP | 0x7017FDA0 | I.START |
| 0x7017ED9B | O.ON | 0x7017FDB9 | .DUMMY. |
| 0x7017EDCB | O.REVERT | 0x7017FDC0 | C.ERRNO |
| 0x7017EDE7 | O.SIGNAL | 0x7017FDC2 | DERR.USR |
| 0x7017EDED | O?SIGNAL | | |

---

## 10. Open Questions

1. **Exact JMP slot ↔ condition mapping:** The 4 JMP words after I.PROLOG correspond to condition types. The ac0 values from O.Sxxx stubs (-2, -3, -4, -5, -1) select which slot, but the exact mapping needs confirmation by tracing I.SFCON dispatch logic.

2. **Internal routines at 0x7017EE62-0x7017EF05:** Raw hex block containing handler search (called at 0x7017EE7A by O.ON/O.REVERT) and signal decoder (called at 0x7017EE9D by O.SET). Not disassembled by StartStop tool. Manual disassembly would complete the picture.

3. **Display pointer depth:** Most Quest functions appear at nesting level 0-1. Deep nesting (which would exercise the display mechanism) seems rare.
