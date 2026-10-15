package com.dmp.riskdecision.component.variable;

import com.dmp.riskdecision.component.variable.mapper.VariableVersionMapper;
import com.dmp.riskdecision.component.variable.mapper.VariableVersionRow;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.service.impl.VariableServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 数据库唯一约束冲突须映射为业务版本冲突，包括跨实例竞争。 */
class VariableVersionConflictTest {
    @Test
    void translatesConcurrentCreateAndUpdateInsertConflicts() {
        VariableVersionMapper mapper = mock(VariableVersionMapper.class);
        VariableServiceImpl service = new VariableServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "allowedHosts", "localhost,127.0.0.1");
        doThrow(new DuplicateKeyException("duplicate version")).when(mapper)
                .insert(any(VariableVersionRow.class));
        String url = "http://localhost:9199/value";
        assertThatThrownBy(() -> service.create("variable", "变量", VariableValueType.DECIMAL,
                "HTTP_GET", url, 1000, List.of(), "value"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("conflict");

        VariableVersionRow current = new VariableVersionRow();
        current.setId(1L);
        current.setVariableCode("variable");
        current.setVariableName("变量");
        current.setVersionNumber(1L);
        current.setValueType("DECIMAL");
        current.setSourceType("HTTP_GET");
        current.setUrl(url);
        current.setTimeoutMs(1000);
        current.setParameterNamesJson("[]");
        current.setResultPath("value");
        when(mapper.current("variable")).thenReturn(current);
        assertThatThrownBy(() -> service.update("variable", 1, "变量新版", VariableValueType.DECIMAL,
                "HTTP_GET", url, 1000, List.of(), "value"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("conflict");
    }
}
