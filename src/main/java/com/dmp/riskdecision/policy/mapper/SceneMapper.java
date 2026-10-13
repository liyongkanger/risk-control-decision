package com.dmp.riskdecision.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 场景发布指针表的数据访问接口。 */
@Mapper
public interface SceneMapper extends BaseMapper<SceneRow> {
    SceneRow findByCode(String sceneCode);

    List<SceneRow> search(@Param("pattern") String pattern, @Param("offset") long offset,
                          @Param("pageSize") int pageSize);

    long count(@Param("pattern") String pattern);

    Long publishedVersionId(String sceneCode);

    int switchPublished(@Param("sceneCode") String sceneCode,
                        @Param("expectedId") Long expectedId, @Param("newId") long newId,
                        @Param("expectedRevision") long expectedRevision);
}
