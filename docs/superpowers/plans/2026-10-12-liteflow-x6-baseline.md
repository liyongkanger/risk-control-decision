# LiteFlow and X6 Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal, independently verifiable LiteFlow backend chain and React/X6 frontend canvas without defining the platform's future scene or policy model.

**Architecture:** The backend owns three demo LiteFlow components and one fixed chain used only for integration verification. The frontend is a separate Vite app under `web/`; it renders an in-memory X6 graph and does not call the backend. No scene routing, persistence, graph-to-LiteFlow conversion, or business decision contract is introduced.

**Tech Stack:** Java 21, Spring Boot 4.1.1, LiteFlow Spring Boot 4 starter 2.16.3, JUnit 5, React, TypeScript, Vite, AntV X6 3.x.

**Spec:** Approved design in conversation on 2026-10-12 (基础接入).

## Global Constraints

- Keep the existing single Maven module and capability package layout.
- Use `com.yomahub:liteflow-spring-boot4-starter:2.16.3` for Spring Boot 4.
- Put demo LiteFlow components under `com.dmp.riskdecision.component`.
- Store the fixed chain in backend resources only for this baseline.
- Put the frontend under `web/` and keep graph state in browser memory.
- Do not add scene, policy, persistence, API, or graph conversion models.

---

### Task 1: Add and verify the LiteFlow backend baseline

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/flow.el.xml`
- Create: `src/main/java/com/dmp/riskdecision/component/PrepareComponent.java`
- Create: `src/main/java/com/dmp/riskdecision/component/ParallelLeftComponent.java`
- Create: `src/main/java/com/dmp/riskdecision/component/ParallelRightComponent.java`
- Create: `src/main/java/com/dmp/riskdecision/component/FlowResult.java`
- Create: `src/main/java/com/dmp/riskdecision/component/LiteFlowBaselineTest.java`

**Interfaces:** The test invokes LiteFlow with a `FlowResult` context and expects `prepare`, `parallel-left`, and `parallel-right` markers.

- [x] Add the LiteFlow Spring Boot 4 starter and configure `liteflow.rule-source: flow.el.xml`.
- [x] Define a fixed `THEN(prepare, WHEN(parallelLeft, parallelRight))` chain and three components that append stable markers to the context.
- [x] Write an integration test that boots the Spring context, executes the chain through `FlowExecutor`, and asserts success plus all markers.
- [x] Run `mvn test` with JDK 21 and a bounded Maven heap.

### Task 2: Add the React/X6 frontend baseline

**Files:**
- Create: `web/package.json`
- Create: `web/tsconfig.json`
- Create: `web/tsconfig.node.json`
- Create: `web/vite.config.ts`
- Create: `web/index.html`
- Create: `web/src/main.tsx`
- Create: `web/src/App.tsx`
- Create: `web/src/styles.css`
- Modify: `.gitignore`

**Interfaces:** The page renders an X6 graph with demo nodes and edges and exposes add-node and clear actions; state remains in memory.

- [x] Create a Vite React TypeScript app definition with `react`, `react-dom`, `@antv/x6`, and Vite build scripts.
- [x] Render a graph with `prepare`, `parallel-left`, and `parallel-right` nodes, two edges, grid, selection, and basic controls.
- [x] Add buttons for adding a demo node and clearing the graph; display node and edge counts.
- [x] Run `npm install` and `npm run build` in `web/`.

### Task 3: Final verification and commit

- [x] Run backend tests and frontend build again from the final tree.
- [x] Run `git diff --check` and inspect the changed file list.
- [x] Commit with `feat: add LiteFlow and X6 integration baseline`.
