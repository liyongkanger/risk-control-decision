# Scene and Visual Flow Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an operator edit a structured flow in X6, validate and publish an immutable version, then run that version synchronously through LiteFlow by scene code.

**Architecture:** X6 submits a platform-owned graph format. A Java validator and compiler accept structured serial/parallel/conditional graphs and produce LiteFlow EL. MySQL stores drafts, immutable versions and a scene pointer; publishing loads a versioned chain before changing the pointer. The first runtime uses safe built-in demo components and `FAIL_FAST` only.

**Tech Stack:** Java 21, Spring Boot 4.1.1, MyBatis 4, MySQL, LiteFlow 2.16.3, JUnit 5, React 19, TypeScript, Vite, AntV X6 3.1.7.

**Spec:** `docs/superpowers/specs/2026-10-12-scene-flow-model-design.md`

## Global Constraints

- Keep the existing single Maven module and `web/` frontend directory.
- A scene selects one published, immutable flow version; one request is pinned to that version.
- Published graphs support serial execution, all-branch parallel execution, true/false conditional execution, and explicit matching joins. Reject cycles, cross-branch edges, arbitrary shared subgraphs, and loops.
- First-cut component failure policy is exactly `FAIL_FAST`. Execution errors and business decisions are separate.
- X6 positions are presentation metadata and cannot affect execution order.
- A graph can be saved as a draft while incomplete. Publishing requires full validation.
- MySQL is the durable authority; Redis is not required for the first implementation.
- The first cut targets one application instance. On startup, load every currently published versioned chain before accepting decision requests; cross-instance publication propagation is a later design concern.
- Do not run side-effectful components while validating a publication. The first validation samples use pure demo components.
- Local environment has JDK 21 and Node 22. Docker is not installed; automated repository tests may use H2 in MySQL mode, but final MySQL compatibility must be checked against a real MySQL service before production use.

---

## File Map and Dependency Direction

| Unit | Files to create or change | Responsibility |
|---|---|---|
| Graph contract | `orchestrator/graph/GraphDefinition.java`, `GraphNode.java`, `GraphEdge.java`, `NodeKind.java`, `FailurePolicy.java`, `Point.java`, `GraphIssue.java` | Backend JSON contract, free of X6 types |
| Graph validation | `orchestrator/graph/GraphValidator.java`, `GraphValidationResult.java` | IDs, degrees, reachability, cycles, branch labels and join pairing |
| Compilation | `orchestrator/compile/FlowCompiler.java`, `CompiledFlow.java` | Turn validated structured graph into EL and a versioned chain ID |
| Component runtime | `component/ComponentRegistry.java`, `ComponentDefinition.java`, `ExecutionContext.java`, `LiteFlowComponentAdapter.java`, `LiteFlowConditionAdapter.java` | Resolve reusable component keys, node configuration and per-node results |
| Persistence | `src/main/resources/db/migration/V1__scene_flow.sql`, `policy/SceneMapper.java`, `policy/FlowDraftMapper.java`, `policy/FlowVersionMapper.java`, `policy/SceneFlowRepository.java` | MySQL storage and atomic scene pointer update |
| Publication and execution | `policy/FlowPublicationService.java`, `orchestrator/LiteFlowChainRegistry.java`, `decision/DecisionExecutionService.java`, `decision/ExecutionResult.java`, `audit/NodeTrace.java` | Load chain, publish version, pin it for synchronous execution |
| HTTP boundary | `policy/FlowAdminController.java`, `decision/DecisionController.java`, `policy/ApiErrorHandler.java` | Draft, validate, publish and evaluate endpoints |
| Frontend contract and editor | `web/src/flow/types.ts`, `web/src/flow/graphAdapter.ts`, `web/src/flow/api.ts`, `web/src/flow/FlowEditor.tsx`, `web/src/App.tsx` | X6 editing, backend transport and error location |

`orchestrator` depends on `component` contracts, `policy` depends on graph validation and compilation, and `decision` depends on published-version lookup plus the LiteFlow adapter. No domain class imports an X6 package.

## Task 1: Define the graph JSON contract and basic validation

**Files:** Create the graph contract and validator files from the file map; create `src/test/java/com/dmp/riskdecision/orchestrator/graph/GraphValidatorTest.java`.

**Interfaces:**

```java
public record GraphDefinition(int schemaVersion, List<GraphNode> nodes,
        List<GraphEdge> edges, Map<String, Point> layout) {}
public record GraphNode(String id, NodeKind kind, String componentKey,
        FailurePolicy failurePolicy, Map<String, Object> config) {}
public record GraphEdge(String id, String source, String target, String branch) {}
public record GraphIssue(String code, String nodeId, String edgeId, String message) {}
public record GraphValidationResult(List<GraphIssue> issues) {
    public boolean valid() { return issues.isEmpty(); }
}
public interface GraphValidator {
    GraphValidationResult validateDraft(GraphDefinition graph);
    GraphValidationResult validateForPublish(GraphDefinition graph);
}
```

`NodeKind` values: `START`, `END`, `COMPONENT`, `PARALLEL_SPLIT`, `PARALLEL_JOIN`, `CONDITION`, `CONDITION_JOIN`. `FailurePolicy` has only `FAIL_FAST` in this cut. `Point` contains numeric `x` and `y`.

- [ ] **Step 1: Add failing tests** for duplicate IDs, missing edge endpoints, a two-node cycle, unreachable node, invalid component degree, missing `FAIL_FAST`, and a draft with no `END` that is accepted by `validateDraft` but rejected by `validateForPublish`.

```java
assertThat(validator.validateForPublish(graphWithCycle()).issues())
    .extracting(GraphIssue::code).contains("GRAPH_CYCLE");
assertThat(validator.validateDraft(incompleteDraft()).valid()).isTrue();
```

- [ ] **Step 2: Run** `mvn -q -Dtest=GraphValidatorTest test`; confirm failure because contract/validator classes are absent.
- [ ] **Step 3: Implement** immutable records and a deterministic validator: build ID maps, reject duplicate IDs, check endpoints and degrees, run DFS cycle detection, then traverse from `START` and reverse from `END`. Return issues sorted by code, node ID and edge ID so API/UI order is stable. Draft validation checks shape, unique IDs, endpoint references and size limits only; publication applies all checks. Use limits of 200 nodes and 500 edges per graph.
- [ ] **Step 4: Run** the focused test and `mvn -q test`; confirm both pass, then commit `feat: define flow graph contract and validation`.

## Task 2: Validate structured branches and compile LiteFlow EL

**Files:** Extend `GraphValidator.java`; create `FlowCompiler.java`, `CompiledFlow.java`, `src/test/java/com/dmp/riskdecision/orchestrator/compile/FlowCompilerTest.java`.

**Interfaces:**

```java
public record CompiledFlow(String chainId, String el, Map<String, String> nodeBindings) {}
public interface FlowCompiler {
    CompiledFlow compile(String sceneCode, long version, GraphDefinition graph);
}
```

Use `chainId = "scene_" + sceneCode + "_v" + version` after validating `sceneCode` as `[A-Za-z][A-Za-z0-9_-]{0,63}`. `nodeBindings` maps each LiteFlow node alias to its graph node ID; its exact alias syntax must be verified by Task 3 against LiteFlow 2.16.3.

- [ ] **Step 1: Add failing tests** for `START → A → END`, `A → split → (B,C) → join → D`, `A → condition → (B,C) → conditionJoin → D`, a nested parallel/condition structure, mismatched join types, crossing edges, and a shared node with two incoming branches. Assert the success EL contains `THEN`, `WHEN`, and `IF` in the expected nesting and ends with a single reference to `D`.

```java
CompiledFlow compiled = compiler.compile("payment", 3, parallelGraph());
assertThat(compiled.chainId()).isEqualTo("scene_payment_v3");
assertThat(compiled.el()).contains("WHEN(");
assertThat(compiled.el().split("decide", -1)).hasSize(2);
```

- [ ] **Step 2: Run** `mvn -q -Dtest=FlowCompilerTest test`; confirm a failing red run.
- [ ] **Step 3: Implement** a recursive region parser. For each split, locate its matching join by intersecting branch post-dominators; require exactly one common join and forbid edges crossing branch regions. Compile component sequence with `THEN`, parallel regions with `WHEN`, conditions with `IF`, and omit join nodes from EL. Reject empty branches unless the actual LiteFlow syntax for an empty branch has been verified; the first cut requires at least one component per branch.
- [ ] **Step 4: Run** focused and full backend tests; commit `feat: compile structured graphs to LiteFlow EL`.

## Task 3: Prove node-instance binding and dynamic LiteFlow chains

**Files:** Create the component runtime and `LiteFlowChainRegistry.java`; create `src/test/java/com/dmp/riskdecision/orchestrator/LiteFlowDynamicChainTest.java`; update `pom.xml` only if LiteFlow's EL builder is required.

**Interfaces:**

```java
public interface ComponentRegistry {
    ComponentDefinition require(String componentKey);
}
public interface LiteFlowChainRegistry {
    void load(CompiledFlow flow);
    boolean contains(String chainId);
}
public record ExecutionContext(String requestId, String sceneCode, long version,
        Map<String, Object> input, ConcurrentMap<String, Object> nodeResults,
        Queue<NodeTrace> traces) {}
```

Register pure demo keys `emit-value` and `is-positive`; component adapters resolve the graph node ID and its configuration from the pinned `ExecutionContext`. Never store per-request data in singleton component fields. The condition adapter must use LiteFlow's boolean component contract.

- [ ] **Step 1: Add a failing integration test** that dynamically loads a compiled chain, invokes `emit-value` twice with distinct node IDs/config values, runs a positive and negative condition branch, and asserts distinct outputs and that the inactive branch did not execute. Use `LiteFlowChainELBuilder` and `FlowExecutor`, as documented by LiteFlow, for dynamic construction and execution.
- [ ] **Step 2: Run** `mvn -q -Dtest=LiteFlowDynamicChainTest test`; confirm the missing registry/adapter failure.
- [ ] **Step 3: Implement** dynamic chain load and the binding method proven by the test. If LiteFlow 2.16.3 cannot provide a stable instance ID/config to the adapter, stop this task and revise the compiler contract before implementing publication. Keep old chain IDs loaded while requests can still reference them.
- [ ] **Step 4: Run** focused and full backend tests; commit `feat: bind graph node instances to LiteFlow`.

## Task 4: Persist drafts and publish immutable versions

**Files:** Add Flyway core and MySQL module dependencies to `pom.xml`; create the migration, mappers, repository and publication service from the file map; create `src/test/java/com/dmp/riskdecision/policy/FlowPublicationServiceTest.java` and a test profile using H2 MySQL mode.

**Storage contract:**

```sql
CREATE TABLE scene (scene_code VARCHAR(64) PRIMARY KEY,
  published_version_id BIGINT NULL, updated_at TIMESTAMP NOT NULL);
CREATE TABLE flow_draft (scene_code VARCHAR(64) PRIMARY KEY,
  graph_json JSON NOT NULL, revision BIGINT NOT NULL, updated_at TIMESTAMP NOT NULL);
CREATE TABLE flow_version (id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scene_code VARCHAR(64) NOT NULL, version_number BIGINT NOT NULL,
  graph_json JSON NOT NULL, chain_id VARCHAR(128) NOT NULL,
  compiled_el TEXT NOT NULL, created_at TIMESTAMP NOT NULL,
  UNIQUE(scene_code, version_number), UNIQUE(chain_id));
```

`SceneFlowRepository` exposes `saveDraft(sceneCode, graph, expectedRevision)`, `findDraft(sceneCode)`, `findPublished(sceneCode)`, `createVersion(...)`, and `switchPublished(sceneCode, expectedVersionId, newVersionId)`. Revision and pointer updates use compare-and-swap SQL (`UPDATE ... WHERE revision = ?` / `WHERE published_version_id <=> ?`) and must report conflicts.

- [ ] **Step 1: Add failing tests** for draft revision conflicts, immutability of published version rows, invalid component configuration, failed chain load leaving the old pointer untouched, success loading a chain before pointer switch, and startup reloading published chains. Use a fake `LiteFlowChainRegistry` for failure injection and H2 MySQL mode for SQL tests; state explicitly that H2 does not prove production MySQL behavior.
- [ ] **Step 2: Run** `mvn -q -Dtest=FlowPublicationServiceTest test`; confirm failure.
- [ ] **Step 3: Implement** the migration, MyBatis mappers and `@Transactional` publication. Validate graph and each referenced component configuration, compile EL, load the new chain, execute only the pure demo sample, insert immutable version and switch pointer with expected old ID. On transaction failure, the new unused in-memory chain may remain loaded but must never become the published pointer. Reload published chains from MySQL at startup before readiness succeeds.
- [ ] **Step 4: Run** focused and full backend tests; commit `feat: persist and publish scene flow versions`.

## Task 5: Add synchronous execution and HTTP boundaries

**Files:** Create the publication/execution controllers, API error handler, execution result and node trace files from the file map; create `src/test/java/com/dmp/riskdecision/decision/DecisionExecutionServiceTest.java` and `src/test/java/com/dmp/riskdecision/policy/FlowApiTest.java`.

**HTTP contract:**

```text
PUT  /api/scenes/{sceneCode}/draft          GraphDefinition → draft revision
POST /api/scenes/{sceneCode}/validate       GraphDefinition → {valid, issues, compiledEl?}
POST /api/scenes/{sceneCode}/publish        {expectedRevision} → {versionId, chainId}
POST /api/decisions/evaluate               {requestId?, sceneCode, input} →
                                           {requestId, sceneCode, versionId, status, nodeResults, trace}
```

`status` is `SUCCEEDED` or `FAILED`; no `PASS/REJECT` value is invented in this cut. Validation issues carry `code`, `nodeId`, `edgeId`, and `message`.

- [ ] **Step 1: Add failing tests** for unknown scene, successful version pinning during a concurrent pointer change, `FAIL_FAST` component error, and API validation returning a node ID. In the pinning test, block execution after selecting version 1, publish version 2, then release execution and assert version 1 was used throughout.
- [ ] **Step 2: Run** focused tests; confirm red results.
- [ ] **Step 3: Implement** a service that resolves the published immutable version once and checks/loads its versioned chain before execution. Convert LiteFlow errors to `FAILED`, append node traces without raw sensitive input, and expose the four endpoints. Map validation errors to HTTP 422, conflict to 409, missing scene to 404.
- [ ] **Step 4: Run** focused and full backend tests; commit `feat: execute published flows by scene`.

## Task 6: Connect X6 to the graph contract and backend

**Files:** Create the frontend contract/editor files from the file map; replace the demo-only `web/src/App.tsx`; update `web/vite.config.ts` for an `/api` development proxy; create `web/src/flow/graphAdapter.test.ts` only if the repo adds a frontend test runner, otherwise verify adapter through browser interaction and backend API tests.

**Frontend contract:**

```ts
export type NodeKind = 'START' | 'END' | 'COMPONENT' |
  'PARALLEL_SPLIT' | 'PARALLEL_JOIN' | 'CONDITION' | 'CONDITION_JOIN'
export type GraphIssue = { code: string; nodeId?: string; edgeId?: string; message: string }
export type GraphDefinition = { schemaVersion: 1; nodes: FlowNode[];
  edges: FlowEdge[]; layout: Record<string, { x: number; y: number }> }
export function toGraphDefinition(graph: Graph): GraphDefinition
```

- [ ] **Step 1: Create the TypeScript contract** and an adapter from X6 cell data to `GraphDefinition`. Store `kind`, `componentKey`, `failurePolicy`, `config`, and branch label as cell data; exclude X6 styling/geometry from execution fields.
- [ ] **Step 2: Implement the editor** with a palette for the seven node kinds, edge labels for true/false, node configuration, save draft, validate and publish actions. Highlight `nodeId` or `edgeId` from validation issues and show a readable issue list. A draft can remain incomplete; publish button requires a successful backend validation response.
- [ ] **Step 3: Run** `npm ci` and `npm run build` under `web/`. Start the Vite dev server and verify in a real browser: construct serial and parallel graphs, save draft, see an invalid crossing edge highlighted, validate a valid graph, publish, and confirm the published version ID. Use the Playwright skill for browser automation.
- [ ] **Step 4: Commit** `feat: connect X6 editor to scene flow APIs`.

## Task 7: End-to-end verification and documentation

**Files:** Update `README.md` with run commands, graph limits, supported node kinds and known first-cut boundaries; update this plan's checkboxes.

- [ ] **Step 1: Run** `mvn test` with `JAVA_HOME=D:\enviorment\jdk21` and bounded `MAVEN_OPTS`; run `npm run build` in `web/`; run `git diff --check`. Confirm exact counts and exit codes.
- [ ] **Step 2: Exercise** a local example with a real MySQL service when one is available: create schema, start Spring Boot, save/publish a flow and evaluate it; record the MySQL version and result. If no service is available, report this as an unverified integration boundary rather than claiming production readiness.
- [ ] **Step 3: Confirm** the published graph, chain ID, and execution trace reference the same immutable version; verify no raw sensitive input appears in the trace.
- [ ] **Step 4: Commit** documentation and plan status with `docs: document scene flow integration`.
