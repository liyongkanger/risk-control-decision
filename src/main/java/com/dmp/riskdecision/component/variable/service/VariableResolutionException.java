package com.dmp.riskdecision.component.variable.service;

/** 变量来源调用或结果解析失败；决策运行必须失败，后台试取值返回来源错误。 */
public class VariableResolutionException extends RuntimeException {
    public VariableResolutionException(String message) { super(message); }
    public VariableResolutionException(String message, Throwable cause) { super(message, cause); }
}
