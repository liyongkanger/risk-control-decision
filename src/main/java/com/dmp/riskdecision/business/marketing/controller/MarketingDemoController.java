package com.dmp.riskdecision.business.marketing.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoService;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoStatusService;
import com.dmp.riskdecision.business.marketing.vo.req.CheckoutDemoReq;
import com.dmp.riskdecision.business.marketing.vo.req.FeedbackDemoReq;
import com.dmp.riskdecision.business.marketing.vo.req.RunFixtureReq;
import com.dmp.riskdecision.business.marketing.vo.resp.DemoCheckoutResp;
import com.dmp.riskdecision.business.marketing.vo.resp.MarketingDemoReportResp;
import com.dmp.riskdecision.business.marketing.vo.resp.MarketingDemoSetupResp;
import com.dmp.riskdecision.business.marketing.vo.resp.MarketingDemoStatusResp;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 固定营销场景的本地模拟结算与效果观察接口。 */
@RestController
@RequestMapping("/api/demo/checkout-offer")
public class MarketingDemoController {
    @Resource private MarketingDemoService demo;
    @Resource private MarketingDemoStatusService status;

    @GetMapping("/status")
    public ApiResponse<MarketingDemoStatusResp> status() {
        return ApiResponse.success(new MarketingDemoStatusResp(status.get()));
    }

    @PostMapping("/setup")
    public ApiResponse<MarketingDemoSetupResp> setup() {
        return ApiResponse.success(new MarketingDemoSetupResp(demo.setup()));
    }

    @PostMapping("/checkout")
    public ApiResponse<DemoCheckoutResp> checkout(@RequestBody CheckoutDemoReq request) {
        return ApiResponse.success(new DemoCheckoutResp(demo.checkout(request.getExperimentId(),
                request.getCheckoutId(), request.getUserId(), request.getOrderAmount(),
                request.getPreDiscountMargin(), request.getExistingOfferAmount())));
    }

    @PostMapping("/run-fixture")
    public ApiResponse<MarketingDemoReportResp> runFixture(@RequestBody RunFixtureReq request) {
        return ApiResponse.success(new MarketingDemoReportResp(demo.runFixture(request.getExperimentId())));
    }

    @PostMapping("/feedback")
    public ApiResponse<DemoCheckoutResp> feedback(@RequestBody FeedbackDemoReq request) {
        return ApiResponse.success(new DemoCheckoutResp(demo.feedback(request.getExperimentId(),
                request.getCheckoutId(), request.getEventId(), request.getEventType(),
                request.getPaid(), request.getAppliedOfferCode())));
    }

    @GetMapping("/report")
    public ApiResponse<MarketingDemoReportResp> report(@RequestParam String experimentId) {
        return ApiResponse.success(new MarketingDemoReportResp(demo.report(experimentId)));
    }
}
