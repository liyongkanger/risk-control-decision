package com.dmp.riskdecision.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 规则集不可变版本的数据库操作。 */
@Mapper
public interface RuleVersionMapper extends BaseMapper<RuleVersionRow> {
    /** 返回规则集最大已用版本号，未发布时为零。 */
    long maxVersion(@Param("code") String code);
}
