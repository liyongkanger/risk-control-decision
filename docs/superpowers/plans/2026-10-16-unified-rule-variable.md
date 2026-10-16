# Unified Rule Variable Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every rule input through a versioned variable definition, including request and current-flow context values.

**Architecture:** Variable center owns source definitions and recursive dependency resolution. Published flow graphs own one producer binding per context variable. Rule sets pin variable versions and resolve through a flow-scoped runtime context.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, LiteFlow, React, TypeScript, Ant Design.

**Spec:** `docs/superpowers/specs/2026-10-16-unified-rule-variable-design.md`

## Global Constraints

- Preserve existing uncommitted and staged work; do not commit, reset, or delete local data.
- Follow `docs/standards/project-conventions.md`, `mvc-layer-conventions.md`, and `frontend-backend-collaboration.md`.
- Rule thresholds remain in rule definitions; only values come from variable center.
- Request variable code equals flow input field name. Context values stay in one flow invocation.

---

### Task 1: Variable source contract

**Files:** `component/variable/model/VariableVersion.java`, `service/impl/VariableServiceImpl.java`, `service/VariableService.java`, variable center request/response and tests.

**Interfaces:** Add `REQUEST_INPUT` and `FLOW_CONTEXT` source types. Expose a resolver that accepts fixed variable ID, request values, flow node results, and flow bindings. Keep HTTP/marketing source adapters; resolve their parameter names as variable dependencies.

- [x] Write failing tests for request values, missing context values, external dependencies, type mismatches and dependency cycles.
- [x] Run focused tests; confirm expected failures.
- [x] Implement source validation and flow-scoped recursive resolution.
- [x] Run focused tests; confirm success.

### Task 2: Rule and flow execution contract

**Files:** `rule/model/RuleInput.java`, `RuleVariableBinding.java`, `rule/service/RuleVariableBindingService.java`, `RuleSetServiceImpl.java`, `policy/service/impl/FlowComponentValidationService.java`, `orchestrator/graph/GraphDefinition.java`, LiteFlow execution/context/node adapters, related tests.

**Interfaces:** Every `RuleInput` requires a fixed variable binding with no parameter mappings. Flow graphs carry `contextBindings`; rule nodes have no `inputMappings`. Runtime passes flow request, node results and bindings to variable resolver. Standalone test inputs supply request and temporary context values.

- [x] Write failing tests for unbound rule rejection, single context producer, missing producer, nested path, and flow-local isolation.
- [x] Run focused tests; confirm expected failures.
- [x] Implement contract, validation, execution, and test handling.
- [x] Run focused tests; confirm success.

### Task 3: Frontend editors

**Files:** `web/src/services/variableCenter.ts`, `ruleCenter.ts`, `web/src/pages/variable-center/index.tsx`, `rule-center/index.tsx`, `web/src/flow/RuleSetNodeInspector.tsx`, `FlowEditor.tsx`, `graphAdapter.ts`, `types.ts`, focused tests.

**Interfaces:** Variable form supports request/context/external source configuration. Rule inputs select variables only; rule test marks temporary context samples. Flow editor configures context bindings; serialized graph preserves them.

- [x] Write failing frontend behavior tests for source forms and serialized context bindings.
- [x] Run focused tests; confirm expected failures.
- [x] Implement editor and service contract changes.
- [x] Run frontend tests and typecheck.

### Task 4: Marketing sample and API handoff

**Files:** `business/marketing/MarketingDemoFlowService.java`, `MarketingDemoFlows.java`, related tests, `docs/api/*.md`, `README.md`.

**Interfaces:** Register locator request variables; marketing external variables depend on those definitions; rule bindings contain only code/version. Initialization upgrades existing local sample only when drafts are untouched. No database deletion.

- [x] Write failing sample integration test for full init, fixture and value resolution.
- [x] Run focused tests; confirm expected failure.
- [x] Update sample and API documentation.
- [x] Run Maven tests, frontend tests/typecheck, and a local API smoke check.
