package com.dmp.riskdecision.rule.vo.req;

import lombok.Data;

/** 按预期草稿修订号发布规则的请求。 */
@Data
public class PublishRuleSetReq {
    private long expectedRevision;
}
