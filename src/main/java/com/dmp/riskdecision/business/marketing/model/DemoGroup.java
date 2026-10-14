package com.dmp.riskdecision.business.marketing.model;

/** 演示实验的稳定用户分组。 */
public enum DemoGroup {
    /** 不新增优惠，保留原有权益。 */ CONTROL(2),
    /** 低额固定券实验组。 */ LOW(3),
    /** 高额固定券实验组。 */ HIGH(4);

    private final int fixturePaidUsers;

    DemoGroup(int fixturePaidUsers) {
        this.fixturePaidUsers = fixturePaidUsers;
    }

    public int getFixturePaidUsers() {
        return fixturePaidUsers;
    }
}
