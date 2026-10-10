# Risk Decision Package Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the Java 21 Maven project descriptor and the nine capability packages defined in the approved design.

**Architecture:** Keep one Maven module. Use capability packages at the top level; each package contains only a `package-info.java` that defines its responsibility. No runtime classes, APIs, persistence mappings, or strategy models are introduced.

**Tech Stack:** Java 21, Maven, Spring Boot 4.1.1 parent for version management.

**Spec:** `docs/superpowers/specs/2026-10-10-risk-decision-package-skeleton-design.md`

## Global Constraints

- Maven coordinates: `com.dmp:risk-control-decision`.
- Java source root package: `com.dmp.riskdecision`.
- One Maven module; no runtime dependencies or Spring Boot startup class.
- Create only `pom.xml` and nine `package-info.java` files in this task.
- Do not define APIs, interfaces, entities, strategy formats, database tables, Redis keys, or execution logic.

---

## File Map

| File | Responsibility |
|---|---|
| `pom.xml` | Maven coordinates, Java 21 level, Spring Boot 4.1.1 version management |
| `src/main/java/com/dmp/riskdecision/decision/package-info.java` | One decision's request, result, and entry boundary |
| `src/main/java/com/dmp/riskdecision/policy/package-info.java` | Scene, policy set, version, and publication concepts |
| `src/main/java/com/dmp/riskdecision/orchestrator/package-info.java` | Step ordering, concurrency, and result aggregation |
| `src/main/java/com/dmp/riskdecision/rule/package-info.java` | Rules and expression evaluation |
| `src/main/java/com/dmp/riskdecision/feature/package-info.java` | Feature retrieval and representation |
| `src/main/java/com/dmp/riskdecision/list/package-info.java` | List lookup capability |
| `src/main/java/com/dmp/riskdecision/model/package-info.java` | Model scoring capability |
| `src/main/java/com/dmp/riskdecision/audit/package-info.java` | Decision trace and audit records |
| `src/main/java/com/dmp/riskdecision/shared/package-info.java` | Stable foundational types genuinely shared by capabilities |

### Task 1: Create the package skeleton

**Files:** Create exactly the ten files in the file map.

**Interfaces:** Consumes the approved package structure. Produces no Java methods or types.

- [x] **Step 1: Create `pom.xml`** with this exact Maven configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>com.dmp</groupId>
    <artifactId>risk-control-decision</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <properties>
        <java.version>21</java.version>
    </properties>
</project>
```

- [x] **Step 2: Create the nine package descriptions.** Each file contains a one-sentence Javadoc describing the responsibility from the file map and its exact package declaration. For example, `decision/package-info.java` contains:

```java
/** Defines the boundary for one decision request, execution, and result. */
package com.dmp.riskdecision.decision;
```

For the other eight packages, use these exact descriptions, followed by `package com.dmp.riskdecision.<name>;`:

| Package | Javadoc sentence |
|---|---|
| `policy` | Defines scenes, policy sets, versions, and publication concepts. |
| `orchestrator` | Organizes decision steps, concurrent execution, and result aggregation. |
| `rule` | Owns rule definitions and expression evaluation. |
| `feature` | Owns feature retrieval and feature values used by decisions. |
| `list` | Owns list lookup capabilities used by decisions. |
| `model` | Owns model scoring capabilities used by decisions. |
| `audit` | Owns decision traces and audit records. |
| `shared` | Holds stable foundational types shared by multiple capabilities. |

- [x] **Step 3: Verify the artifact.** Run `mvn -q validate` from the project root and confirm exit code 0. Inspect `rg --files src/main/java` and confirm exactly nine `package-info.java` files. Inspect `git diff --check` for whitespace errors. There are no behavior tests because this task introduces no executable behavior.

- [x] **Step 4: Commit** `pom.xml`, the nine package descriptions, and this completed plan with message `chore: add Java decision package skeleton`.
