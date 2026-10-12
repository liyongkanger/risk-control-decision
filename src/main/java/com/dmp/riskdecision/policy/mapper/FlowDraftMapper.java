package com.dmp.riskdecision.policy.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 流程草稿表的数据访问接口。 */
@Mapper
public interface FlowDraftMapper {
    @Select("SELECT scene_code, graph_json, revision FROM flow_draft WHERE scene_code=#{sceneCode}")
    @Results({@Result(column="scene_code", property="sceneCode"),
            @Result(column="graph_json", property="graphJson"),
            @Result(column="revision", property="revision")})
    DraftRow find(String sceneCode);

    @Insert("INSERT INTO flow_draft(scene_code,graph_json,revision,updated_at) VALUES(#{sceneCode},#{graphJson},1,CURRENT_TIMESTAMP)")
    int insert(@Param("sceneCode") String sceneCode, @Param("graphJson") String graphJson);

    @Update("""
            UPDATE flow_draft SET graph_json=#{graphJson}, revision=revision+1, updated_at=CURRENT_TIMESTAMP
            WHERE scene_code=#{sceneCode} AND revision=#{expectedRevision}
            """)
    int update(@Param("sceneCode") String sceneCode, @Param("graphJson") String graphJson,
               @Param("expectedRevision") long expectedRevision);

    /** 流程草稿表的查询行。 */
    class DraftRow {
        private String sceneCode;
        private String graphJson;
        private long revision;

        public String getSceneCode() {
            return sceneCode;
        }

        public void setSceneCode(String sceneCode) {
            this.sceneCode = sceneCode;
        }

        public String getGraphJson() {
            return graphJson;
        }

        public void setGraphJson(String graphJson) {
            this.graphJson = graphJson;
        }

        public long getRevision() {
            return revision;
        }

        public void setRevision(long revision) {
            this.revision = revision;
        }
    }
}
