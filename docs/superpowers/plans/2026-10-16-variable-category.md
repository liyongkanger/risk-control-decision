# Variable Category Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one preset business category per variable with list filters and direct category editing across published versions.

**Architecture:** A category table keyed by variable code stores mutable lookup metadata; the version table remains immutable. Variable reads join current category, and the admin list filters in SQL with matching count query.

**Tech Stack:** Java 21, Spring Boot 4.1, MyBatis-Plus, Flyway, React, Ant Design, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-16-variable-category-design.md`

## Global Constraints

- Preserve all existing staged and unstaged work; do not commit, reset, clean, or delete local databases.
- Follow `docs/standards/project-conventions.md`, `mvc-layer-conventions.md`, `admin-list-pagination.md`, and `frontend-backend-collaboration.md`.
- Category edits must preserve variable version IDs and rules/flows.

---

### Task 1: Category persistence and API

**Files:** `src/main/resources/db/migration/V9__variable_categories.sql`; `component/variable/{model,mapper,service,controller,vo}`; MyBatis XML; variable API and migration tests.

**Interfaces:** Six category codes. `GET /api/variables/categories`; `GET /api/variables` accepts categoryCode/sourceType/valueType; `POST /api/variables` accepts categoryCode; `PUT /api/variables/{code}/category` changes metadata only. `VariableResp` includes categoryCode.

- [x] Write API and migration tests proving backfill, filtering, and version ID unchanged by category edit.
- [x] Run focused tests and observe expected failures.
- [x] Implement V9, SQL joins, service validation and controller contract.
- [x] Run focused and full backend tests.

### Task 2: Variable center UI

**Files:** `web/src/services/variableCenter.ts`, tests; `web/src/pages/variable-center/index.tsx`, form helper and tests.

**Interfaces:** Backend category option endpoint and direct update endpoint; category/source/valueType filters use server pagination; new variable form category; row action for category.

- [x] Write failing service/form behavior tests for new contracts.
- [x] Run focused tests and observe expected failures.
- [x] Implement UI and service calls, including loading and error states.
- [x] Run frontend tests, typecheck and build.

### Task 3: Handoff and local review

**Files:** `docs/api/variable-center.md`, `README.md`, category spec and plan.

- [x] Document category values, migration assignment, API and version semantics.
- [x] Run full backend and frontend verification.
- [x] Start an isolated local backend and use the browser to verify list filtering and category change on a published variable.
