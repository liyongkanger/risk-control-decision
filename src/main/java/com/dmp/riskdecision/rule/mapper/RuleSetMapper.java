package com.dmp.riskdecision.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 规则集草稿与发布指针的数据库操作。 */
@Mapper
public interface RuleSetMapper extends BaseMapper<RuleSetRow> {
    /** 按业务码读取有效规则集。 */
    RuleSetRow byCode(@Param("code") String code);

    /** 按修订号条件保存草稿，返回更新行数。 */
    int saveDraft(@Param("code") String code, @Param("revision") long revision,
                  @Param("json") String json);

    /** 仅在草稿与旧发布指针未变时切换版本。 */
    int pointVersion(@Param("code") String code, @Param("revision") long revision,
                     @Param("oldVersionId") Long oldVersionId, @Param("newVersionId") long newVersionId);

    /** 查询后台列表当前页。 */
    List<RuleSetRow> search(@Param("pattern") String pattern, @Param("offset") long offset,
                            @Param("pageSize") int pageSize);

    /** 查询后台列表总数。 */
    long count(@Param("pattern") String pattern);
}
