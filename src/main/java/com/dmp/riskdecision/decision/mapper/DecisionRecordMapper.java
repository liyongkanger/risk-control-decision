package com.dmp.riskdecision.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 决策执行快照的数据访问。 */
@Mapper
public interface DecisionRecordMapper extends BaseMapper<DecisionRecordRow> {
    DecisionRecordRow findByExecutionId(String executionId);

    List<DecisionRecordRow> search(@Param("sceneCode") String sceneCode,
                                   @Param("requestId") String requestId,
                                   @Param("status") String status,
                                   @Param("offset") long offset,
                                   @Param("pageSize") int pageSize);

    long count(@Param("sceneCode") String sceneCode,
               @Param("requestId") String requestId,
               @Param("status") String status);
}
