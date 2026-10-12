package com.dmp.riskdecision.policy;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FlowVersionMapper {
    @Select("SELECT COALESCE(MAX(version_number),0) FROM flow_version WHERE scene_code=#{sceneCode}")
    long latestVersionNumber(String sceneCode);

    @Insert("""
            INSERT INTO flow_version(scene_code,version_number,graph_json,chain_id,compiled_el,created_at)
            VALUES(#{sceneCode},#{versionNumber},#{graphJson},#{chainId},#{compiledEl},CURRENT_TIMESTAMP)
            """)
    int insert(@Param("sceneCode") String sceneCode, @Param("versionNumber") long versionNumber,
               @Param("graphJson") String graphJson, @Param("chainId") String chainId,
               @Param("compiledEl") String compiledEl);

    @Select("SELECT id,scene_code,version_number,graph_json,chain_id,compiled_el FROM flow_version WHERE id=#{id}")
    @Results(id="flowVersion", value={@Result(column="id", property="id"),
            @Result(column="scene_code", property="sceneCode"),
            @Result(column="version_number", property="versionNumber"),
            @Result(column="graph_json", property="graphJson"),
            @Result(column="chain_id", property="chainId"),
            @Result(column="compiled_el", property="compiledEl")})
    VersionRow find(long id);

    @Select("SELECT id,scene_code,version_number,graph_json,chain_id,compiled_el FROM flow_version WHERE scene_code=#{sceneCode} AND version_number=#{number}")
    @org.apache.ibatis.annotations.ResultMap("flowVersion")
    VersionRow findByNumber(@Param("sceneCode") String sceneCode, @Param("number") long number);

    @Select("SELECT v.id,v.scene_code,v.version_number,v.graph_json,v.chain_id,v.compiled_el FROM flow_version v JOIN scene s ON s.published_version_id=v.id")
    @org.apache.ibatis.annotations.ResultMap("flowVersion")
    List<VersionRow> findAllPublished();

    class VersionRow {
        private long id;
        private String sceneCode;
        private long versionNumber;
        private String graphJson;
        private String chainId;
        private String compiledEl;
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getSceneCode() { return sceneCode; }
        public void setSceneCode(String sceneCode) { this.sceneCode = sceneCode; }
        public long getVersionNumber() { return versionNumber; }
        public void setVersionNumber(long versionNumber) { this.versionNumber = versionNumber; }
        public String getGraphJson() { return graphJson; }
        public void setGraphJson(String graphJson) { this.graphJson = graphJson; }
        public String getChainId() { return chainId; }
        public void setChainId(String chainId) { this.chainId = chainId; }
        public String getCompiledEl() { return compiledEl; }
        public void setCompiledEl(String compiledEl) { this.compiledEl = compiledEl; }
    }
}
