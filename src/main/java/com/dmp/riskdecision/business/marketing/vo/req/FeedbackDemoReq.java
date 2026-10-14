package com.dmp.riskdecision.business.marketing.vo.req;

import lombok.Data;

/** 模拟支付或退款反馈。 */
@Data
public class FeedbackDemoReq {
    private String experimentId;
    private String checkoutId;
    private String eventId;
    private String eventType;
    private Boolean paid;
    private String appliedOfferCode;
}
