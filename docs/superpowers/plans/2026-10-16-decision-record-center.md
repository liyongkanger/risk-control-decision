# Decision Record Center Implementation Plan

> **For agentic workers:** This plan has been executed inline in the current session; the checklist records completed work.

**Goal:** Make every scene decision execution queryable by an independent execution ID and inspectable in one generic UI.

**Architecture:** Save an immutable execution snapshot after the engine returns. Query metadata with database pagination; load full JSON only in detail. Marketing stores and exposes the execution ID, then links to the generic page.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, React, Ant Design, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-16-decision-record-center-design.md`

## Global Constraints

- Preserve the current `ApiResponse<T>` contract and standard backend pagination shape.
- Only scene entry executions create top-level records; subflow traces remain nested.
- Keep marketing settlement and refund state in the marketing domain.

## Tasks

- [x] Add V10 table, mapper, record service and list/detail endpoints; verify pagination and duplicate request IDs.
- [x] Generate execution ID in outer decision service, persist success/failure snapshots, and expose it in decision and marketing responses.
- [x] Add frontend service, paginated decision record page and nested node trace viewer; link marketing attempts to exact execution records.
- [x] Update API documents, run backend/frontend tests and build, then verify a real decision and detail lookup.
