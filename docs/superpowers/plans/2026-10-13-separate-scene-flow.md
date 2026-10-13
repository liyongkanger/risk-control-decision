# Scene and Flow Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. This workspace contains user changes; do not create commits automatically.

**Goal:** Make flows independent assets while preserving existing scene draft, publish, and evaluate behavior.

**Architecture:** Add a `tb_flow` asset and make drafts and versions owned by `flow_id`. A scene binds an entry flow and keeps its current published version pointer for compatibility. Scene routes delegate to flow-owned operations; new flow routes allow a flow to be created, edited, tested, and published without a scene.

**Tech Stack:** Java 21, Spring Boot 4.1.1, MyBatis-Plus 3.5.17, Flyway, MySQL, H2 test mode, LiteFlow 2.16.3.

**Spec:** `prd/iterations/2026-10-v0.1-decision-subflow/prd.md` (this plan implements only the scene/flow ownership prerequisite).

## Global Constraints

- Preserve historical Flyway scripts and existing table IDs and published pointers.
- Keep old `/api/scenes/{sceneCode}/draft|validate|publish` and `/api/decisions/evaluate` behavior.
- New tables follow the `tb_`, `id`, `inserttime`, `updatetime`, `isactive` convention.
- Mapper SQL stays in XML or MyBatis-Plus; Java classes use Chinese Javadoc and Lombok for data holders.
- Leave Git history and staging untouched; the user will commit changes together.

---

### Task 1: Migrate ownership without losing existing scenes

**Files:**
- Create: `src/main/resources/db/migration/V4__separate_scene_flow.sql`
- Create: `src/test/java/com/dmp/riskdecision/policy/mapper/SceneFlowSeparationMigrationTest.java`

**Interfaces:** Existing `tb_scene`, `tb_flow_draft`, `tb_flow_version` data; new `tb_flow.id`, `tb_scene.flow_id`, `tb_flow_draft.flow_id`, `tb_flow_version.flow_id`.

- [x] Write a migration test that applies V1–V3, inserts two scenes with drafts and a published version, applies V4, then verifies one flow per scene, preserved version ID, draft revision, and scene pointer.
- [x] Run `mvn -Dtest=SceneFlowSeparationMigrationTest test` and observe the missing V4 assertion fail.
- [x] Write V4 to create `tb_flow`, backfill one flow per scene, add and backfill `flow_id`, remove legacy `scene_code` columns from draft/version, and add unique indexes on flow ownership.
- [x] Rerun the focused migration test and inspect the resulting schema.

### Task 2: Move persistence to flow identity

**Files:**
- Create: `policy/mapper/FlowRow.java`, `policy/mapper/FlowMapper.java`, `resources/mapper/policy/FlowMapper.xml`, `policy/model/FlowInfo.java`
- Modify: `SceneRow`, `SceneMapper`, `FlowDraftRow`, `FlowDraftMapper`, `FlowVersionRow`, `FlowVersionMapper`, `SceneFlowRepository`, `SceneServiceImpl`, their mapper XML files.
- Test: `FlowPublicationServiceTest`, `SceneApiTest`, plus a new flow ownership integration test.

**Interfaces:** `SceneFlowRepository` keeps its scene-code facade for old routes but resolves `scene.flow_id`; flow-owned methods accept flow code and use `flow_id`. Scene creation creates and binds an independent flow asset in one transaction.

- [x] Extend an integration test to prove a scene draft and version are stored by `flow_id`, with no `scene_code` column on new flow rows.
- [x] Run the focused test and observe the old scene-keyed write fail.
- [x] Add the flow mapper/model, flow-ID queries, and scene-to-flow resolution. Keep old scene routes as adapters.
- [x] Rerun the focused test and the existing scene, publication, and decision tests.

### Task 3: Expose independent flow operations and scene binding

**Files:**
- Create: flow administration service/controller and request/response classes under `policy/`.
- Modify: scene controller/service for entry-flow binding; flow publication service for flow-owned draft and publish operations.
- Test: `FlowAssetApiTest` and `SceneApiTest`.

**Interfaces:** Independent routes under `/api/flows`; a scene binding route selects an existing flow. Existing scene routes continue to operate on the bound flow.

- [x] Add an API test: create flow without scene, save/publish its graph, bind it to a scene, evaluate through the scene, then republish the flow and confirm old scene pointer remains fixed until explicitly updated.
- [x] Run the focused test and observe the missing flow endpoints fail.
- [x] Implement flow creation, draft, validation, publication, detail, and scene binding through domain services. Keep component framework calls behind existing `component.liteflow.service`.
- [x] Rerun the focused API tests, all backend tests, frontend tests/typecheck/build. Local MySQL login was unavailable; no live schema was changed.

### Completion check

Verify `mvn test`, `npm test`, `npm run typecheck`, `npm run build`, and `git diff --check`; inspect old scene API response shapes and new flow API behavior. Do not commit.

Verification: the full backend suite passed with 51 tests on 2026-10-13 using JDK 21. Front-end tests, typecheck and build also passed. V4 passed H2's MySQL compatibility mode. MySQL 5.7 verification remains outstanding because the local root account requires credentials unavailable in this terminal.
