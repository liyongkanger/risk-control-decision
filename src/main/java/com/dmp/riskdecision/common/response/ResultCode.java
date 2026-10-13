package com.dmp.riskdecision.common.response;

/** HTTP 接口的通用结果码。 */
public enum ResultCode {
    /** 请求处理成功。 */
    SUCCESS("成功"),
    /** 请求参数无效。 */
    INVALID_REQUEST("请求参数无效"),
    /** 流程图校验失败。 */
    GRAPH_INVALID("流程图无效"),
    /** 草稿修订号或版本发生冲突。 */
    VERSION_CONFLICT("版本冲突"),
    /** 场景码已被使用。 */
    SCENE_CODE_CONFLICT("场景码已存在"),
    /** 流程码已被使用。 */
    FLOW_CODE_CONFLICT("流程码已存在"),
    /** 请求的资源不存在。 */
    NOT_FOUND("资源不存在"),
    /** 请求体超过大小限制。 */
    PAYLOAD_TOO_LARGE("请求体过大"),
    /** 未预期的服务端错误。 */
    INTERNAL_ERROR("服务内部错误");

    private final String message;

    ResultCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
