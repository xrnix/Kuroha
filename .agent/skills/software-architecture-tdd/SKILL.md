---
name: software-architecture-tdd
description: Disciplined software architecture, Test-Driven Development (TDD), and static site builder engineering. Use this skill when structuring static site generators, component hierarchies, state management, schema typing (Zod/TypeScript), or test suites to guarantee zero hydration mismatches, strict separation of concerns, and rock-solid typed contracts.
---

# Software Architecture & TDD (Superpowers / Ponytail)

Guidelines for high-discipline software architecture, typed contracts, and zero-hydration frontend engineering.

## Core Directives

### 1. Schema-First & Typed Contracts
- **Parse, Don't Validate**: Define strict schemas (e.g. Zod, ArkType, or TypeScript type assertions) at every boundary (API responses, file loaders, frontmatter parsers, URL query params).
- **No `any` or Loose Record Types**: Enforce explicit discriminated unions for state and component variants.
- **Fail Early**: Configuration errors, missing assets, and schema mismatches must throw informative runtime validation errors at build/init time, not fail silently in the browser.

### 2. Zero Hydration Mismatches & Clean Trees
- **Deterministic Server/Client State**: Never reference `window`, `document`, `localStorage`, or random timestamps/UUIDs during initial render pass.
- **State Partitioning**: Keep state as close to the leaves as possible. Avoid dumping everything into global context or stores.
- **Composition over Inheritance**: Build small, single-responsibility composable primitives rather than bloated multi-purpose monolith components.

### 3. Test-Driven Development (TDD)
- **Red $\rightarrow$ Green $\rightarrow$ Refactor**:
  1. Write failing unit tests defining expected output/behavior against the boundary.
  2. Implement minimum viable code to make tests pass.
  3. Refactor component and function structures while keeping test coverage green.
- **Test User Behavior, Not Implementation Details**: Target accessible roles, text outputs, and state transitions rather than internal component variables or class names.
- **Automated Regression Prevention**: Unit test parser edges, date calculations, search indexers, and schema transformations before assembling final UI pages.
