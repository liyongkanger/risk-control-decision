package com.dmp.riskdecision.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 独立流程资产的数据访问接口，自定义语句位于同名 XML。 */
@Mapper
public interface FlowMapper extends BaseMapper<FlowRow> {
    FlowRow findByCode(String flowCode);

    FlowRow findById(long flowId);

    List<FlowRow> search(@Param("pattern") String pattern, @Param("offset") long offset,
                         @Param("pageSize") int pageSize);

    long count(@Param("pattern") String pattern);

    int switchPublished(@Param("flowId") long flowId, @Param("expectedId") Long expectedId,
                        @Param("newId") long newId, @Param("expectedRevision") long expectedRevision);
}
