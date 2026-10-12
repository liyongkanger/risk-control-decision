package com.dmp.riskdecision.audit;

import java.time.Instant;

public record NodeTrace(String nodeId, Instant startedAt, Instant endedAt, String status, String errorCode) {}
