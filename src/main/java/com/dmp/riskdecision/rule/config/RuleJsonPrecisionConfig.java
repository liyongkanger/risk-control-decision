package com.dmp.riskdecision.rule.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/** 将 HTTP 请求中的小数直接解析为 BigDecimal，避免规则比较前发生二进制舍入。 */
@Configuration
public class RuleJsonPrecisionConfig {
    /** 为应用的 JSON 请求绑定启用精确小数解析。 */
    @Bean
    public JsonMapperBuilderCustomizer preciseRuleDecimals() {
        return builder -> builder.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
