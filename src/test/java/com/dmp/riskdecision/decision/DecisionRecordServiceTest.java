package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.decision.model.BusinessResult;
import com.dmp.riskdecision.decision.model.DecisionRecord;
import com.dmp.riskdecision.decision.model.DecisionRecordPage;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionRecordService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证同一请求标识的两次执行可独立追溯，列表只返回分页元数据。 */
@SpringBootTest
class DecisionRecordServiceTest {
    @Resource private DecisionRecordService records;

    @Test
    void savesDistinctExecutionsAndReadsTheirSnapshots() {
        String requestId = "record-test-" + System.nanoTime();
        NodeTrace trace = new NodeTrace(requestId, "recordTestScene", 1, "result",
                Instant.parse("2026-10-16T01:00:00Z"), Instant.parse("2026-10-16T01:00:01Z"),
                "SUCCEEDED", null);
        ExecutionResult first = new ExecutionResult(requestId, "recordTestScene", 17, "SUCCEEDED",
                Map.of("result", Map.of("value", 1)), List.of(trace), null,
                new BusinessResult("PASS", "QUALIFIED", "通过", Map.of(), null));
        ExecutionResult second = new ExecutionResult(requestId, "recordTestScene", 17, "FAILED",
                Map.of("result", Map.of("error", "missing")), List.of(), "FLOW_EXECUTION_FAILED");

        String firstId = records.save(first, Map.of("uid", "u1"));
        String secondId = records.save(second, Map.of("uid", "u2"));
        assertThat(firstId).isNotEqualTo(secondId);
        DecisionRecord detail = records.get(firstId);
        assertThat(detail.getInput()).containsEntry("uid", "u1");
        assertThat(detail.getResult()).containsEntry("executionId", firstId);
        assertThat(detail.getResult()).containsKey("trace");
        assertThat(((Map<?, ?>) detail.getResult().get("businessResult")).get("conclusion"))
                .isEqualTo("PASS");

        DecisionRecordPage page = records.search("recordTestScene", requestId, null, 1, 1);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().getFirst().getExecutionId()).isEqualTo(secondId);
        assertThat(page.getRecords().getFirst().getResult()).isNull();
        assertThat(records.search("recordTestScene", requestId, "SUCCEEDED", 1, 10)
                .getRecords()).extracting(DecisionRecord::getExecutionId).containsExactly(firstId);
    }
}
