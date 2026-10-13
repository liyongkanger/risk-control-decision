package com.dmp.riskdecision.policy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 草稿查询和修订号条件更新接口，自定义语句位于同名 XML。 */
@Mapper
public interface FlowDraftMapper {
    FlowDraftRow find(long flowId);

    int insert(@Param("flowId") long flowId, @Param("graphJson") String graphJson);

    int update(@Param("flowId") long flowId, @Param("graphJson") String graphJson,
               @Param("expectedRevision") long expectedRevision);
}
