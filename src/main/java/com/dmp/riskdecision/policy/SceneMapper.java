package com.dmp.riskdecision.policy;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SceneMapper {
    @Insert("INSERT IGNORE INTO scene(scene_code, published_version_id, updated_at) VALUES(#{sceneCode}, NULL, CURRENT_TIMESTAMP)")
    int insertIfMissing(String sceneCode);

    @Select("SELECT published_version_id FROM scene WHERE scene_code=#{sceneCode}")
    Long publishedVersionId(String sceneCode);

    @Update("""
            UPDATE scene SET published_version_id=#{newId}, updated_at=CURRENT_TIMESTAMP
            WHERE scene_code=#{sceneCode}
              AND ((published_version_id IS NULL AND #{expectedId,jdbcType=BIGINT} IS NULL)
                   OR published_version_id=#{expectedId,jdbcType=BIGINT})
            """)
    int switchPublished(@Param("sceneCode") String sceneCode,
                        @Param("expectedId") Long expectedId, @Param("newId") long newId);
}
