package com.dmp.riskdecision.business.marketing.vo.req;

import java.math.BigDecimal;
import lombok.Data;

/** 模拟结算输入。 */
@Data
public class CheckoutDemoReq {
    private String experimentId;
    private String checkoutId;
    private String userId;
    private BigDecimal orderAmount;
    private BigDecimal preDiscountMargin;
    private BigDecimal existingOfferAmount;
}
