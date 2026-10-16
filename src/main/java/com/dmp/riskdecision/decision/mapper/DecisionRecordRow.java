package com.dmp.riskdecision.decision.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 决策执行快照的数据库行。 */
@Data
@TableName("tb_decision_execution_record")
public class DecisionRecordRow {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String executionId;
    private String requestId;
    private String sceneCode;
    private Long versionId;
    private String status;
    private String conclusion;
    private String errorCode;
    private String inputJson;
    private String resultJson;
    private LocalDateTime inserttime;
    private LocalDateTime updatetime;
    @TableLogic(value = "1", delval = "0")
    private Integer isactive;
}
