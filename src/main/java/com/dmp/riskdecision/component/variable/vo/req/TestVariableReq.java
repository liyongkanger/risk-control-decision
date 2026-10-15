package com.dmp.riskdecision.component.variable.vo.req;

import java.util.Map;
import lombok.Data;

/** 使用变量当前版本试取值的输入参数。 */
@Data
public class TestVariableReq {
    private Map<String, Object> input;
}
