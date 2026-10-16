package com.dmp.riskdecision.component.variable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 变量中心版本数据库访问接口，自定义查询位于同名 XML。 */
@Mapper
public interface VariableVersionMapper extends BaseMapper<VariableVersionRow> {
    VariableVersionRow current(String variableCode);
    VariableVersionRow version(long id);
    List<VariableVersionRow> search(@Param("pattern") String pattern,
                                    @Param("categoryCode") String categoryCode,
                                    @Param("sourceType") String sourceType,
                                    @Param("valueType") String valueType,
                                    @Param("offset") long offset, @Param("pageSize") int pageSize);
    long count(@Param("pattern") String pattern, @Param("categoryCode") String categoryCode,
               @Param("sourceType") String sourceType, @Param("valueType") String valueType);
    void insertCategory(@Param("variableCode") String variableCode, @Param("categoryCode") String categoryCode);
    int updateCategory(@Param("variableCode") String variableCode, @Param("categoryCode") String categoryCode);
}
