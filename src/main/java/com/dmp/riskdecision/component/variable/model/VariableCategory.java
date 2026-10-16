package com.dmp.riskdecision.component.variable.model;

/** 逻辑变量可修改的预设业务主分类。 */
public enum VariableCategory {
    /** 用户。 */ USER("用户"),
    /** 交易或订单。 */ TRANSACTION("交易/订单"),
    /** 活动或实验。 */ CAMPAIGN("活动/实验"),
    /** 额度或预算。 */ BUDGET("额度/预算"),
    /** 风险指标。 */ RISK("风险指标"),
    /** 其他。 */ OTHER("其他");

    private final String displayName;

    VariableCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getCode() { return name(); }

    public String getDisplayName() { return displayName; }

    /** 未提供时归入其他；未知代码属于请求错误。 */
    public static VariableCategory parse(String code) {
        if (code == null) return OTHER;
        try { return valueOf(code); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid variable category: " + code); }
    }
}
