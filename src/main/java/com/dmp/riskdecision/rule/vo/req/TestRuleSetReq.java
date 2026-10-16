package com.dmp.riskdecision.rule.vo.req;

import java.util.Map;
import lombok.Data;

/** 使用模拟强类型输入试算规则草稿的请求。 */
@Data
public class TestRuleSetReq {
    private Map<String, Object> input;
    private Map<String, Object> contextValues;
}
