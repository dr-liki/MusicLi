# Assets (`app/src/main/assets`)

## Overview
Application asset directory containing the bundled JavaScript bytecode VM script `vm_new.js` (alongside non-code `licenses/` and `nlp/` asset folders).

## Interface
- [`vm_new.js`](vm_new.js): Self-executing IIFE (`[vm_new.js:(anonymous IIFE)]`) that decodes an embedded base64/varint bytecode payload via internal decoders (`r`, `i`, `o`) and executes a 69-opcode interpreter (`i`, `a`) starting at offset `6151` against an externally provided `oe` binding (`[vm_new.js:oe]`). No JS module exports are declared.

## Invariants
- Execution expects `oe` (`[vm_new.js:oe]`) to be defined in the host JS runtime scope before `vm_new.js` is evaluated.
- Bytecode decoding uses `Int8Array` when available and falls back to plain JavaScript arrays (`[vm_new.js:o]`).

## Side Effects
- Executes the decoded VM instructions immediately on script evaluation, interacting with the host-supplied `oe` object and throwing uncaught VM runtime errors if no handler `u` is provided (`[vm_new.js:(anonymous IIFE)]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
