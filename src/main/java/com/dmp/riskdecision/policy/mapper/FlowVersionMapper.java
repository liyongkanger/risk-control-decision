package com.dmp.riskdecision.policy.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 流程版本的持久化接口，自定义语句位于同名 XML。 */
@Mapper
public interface FlowVersionMapper {
    long latestVersionNumber(long flowId);

    int insert(@Param("flowId") long flowId, @Param("versionNumber") long versionNumber,
               @Param("graphJson") String graphJson, @Param("chainId") String chainId,
               @Param("compiledEl") String compiledEl);

    FlowVersionRow find(long id);

    FlowVersionRow findByNumber(@Param("flowId") long flowId, @Param("number") long number);

    List<FlowVersionRow> findAllPublished();
}
