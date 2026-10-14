package com.dmp.riskdecision.business.marketing.service;

import com.dmp.riskdecision.business.marketing.MarketingDemoSetup;
import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.model.MarketingDemoReport;
import java.math.BigDecimal;

/** 模拟结算、实际反馈和实验效果的应用用例。 */
public interface MarketingDemoService {
    /**
     * 初始化并绑定演示场景与三条已发布流程；重复调用复用已有发布版本。
     *
     * @return 演示场景、外层流程及两个子流程的当前版本标识
     */
    MarketingDemoSetup setup();

    /**
     * 对一次模拟结算执行真实决策，并保存可追溯的决策快照。
     * 同一实验中的相同结算 ID 和输入重复调用返回原快照；输入不一致时拒绝覆盖。
     *
     * @param experimentId 实验标识，用于稳定分组和隔离样本
     * @param checkoutId 实验内唯一的结算单标识
     * @param userId 匿名用户标识，同一实验内稳定分组
     * @param orderAmount 订单金额，单位元，非负且最多两位小数
     * @param preDiscountMargin 优惠前贡献毛利，单位元，非负且最多两位小数
     * @param existingOfferAmount 已有优惠金额，单位元，非负且最多两位小数
     * @return 包含决策状态、结论、推荐优惠和执行版本的结算快照
     * @throws IllegalArgumentException 标识或金额不符合演示输入约束
     * @throws IllegalStateException 相同结算 ID 已使用另一组输入
     */
    CheckoutAttempt checkout(String experimentId, String checkoutId, String userId,
                             BigDecimal orderAmount, BigDecimal preDiscountMargin,
                             BigDecimal existingOfferAmount);

    /**
     * 回传实际支付结算或全额退款，并按结算单与事件 ID 去重。
     * 推荐优惠与实际使用优惠分别记录；退款将该单贡献利润冲回为零。
     *
     * @param experimentId 原结算所属实验
     * @param checkoutId 原结算单标识
     * @param eventId 结算单内唯一的反馈事件标识
     * @param eventType {@code SETTLED} 或 {@code REFUNDED}
     * @param paid 结算事件必填；退款事件必须为空
     * @param appliedOfferCode 结算事件必填的实际优惠码；退款事件必须为空
     * @return 应用反馈后的结算快照；相同事件重放返回当前快照
     * @throws IllegalArgumentException 事件类型、支付状态或实际优惠组合无效
     * @throws IllegalStateException 事件 ID 冲突或结算状态不允许该反馈
     * @throws java.util.NoSuchElementException 结算单不存在
     */
    CheckoutAttempt feedback(String experimentId, String checkoutId, String eventId,
                             String eventType, Boolean paid, String appliedOfferCode);

    /**
     * 为三组各运行十位合格用户的固定模拟样本，并产生预设支付反馈。
     * 同一实验重复运行不会增加固定样本订单或重复计入反馈。
     *
     * @param experimentId 用于稳定分组和隔离样本的实验标识
     * @return 包含固定样本及当前手动订单的实验报告
     * @throws IllegalArgumentException 实验标识不符合演示输入约束
     */
    MarketingDemoReport runFixture(String experimentId);

    /**
     * 汇总当前进程内该实验的逐笔反馈和分组效果；尚无订单时返回空样本指标。
     *
     * @param experimentId 待查询的实验标识
     * @return 三组指标和逐笔结算记录
     * @throws IllegalArgumentException 实验标识不符合演示输入约束
     */
    MarketingDemoReport report(String experimentId);
}
