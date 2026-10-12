package com.dmp.riskdecision.orchestrator.compile;

import java.util.Map;

public record CompiledFlow(String chainId, String el, Map<String, String> nodeBindings) {}
