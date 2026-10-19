package com.dmp.riskdecision.business.marketing.service;

import com.dmp.riskdecision.business.marketing.model.MarketingDemoStatus;

/** 只读检查固定营销样板的当前资产及场景运行版本。 */
public interface MarketingDemoStatusService {
    /** 读取版本链；未初始化或部分资产缺失时返回可空字段，不创建资产。 */
    MarketingDemoStatus get();
}
