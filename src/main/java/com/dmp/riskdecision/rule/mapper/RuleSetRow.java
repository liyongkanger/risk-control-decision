package com.dmp.riskdecision.rule.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 规则集元数据及可编辑草稿的数据库行。 */
@Data
@TableName("tb_rule_set")
public class RuleSetRow {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    private String ruleSetCode;
    private String ruleSetName;
    private Long draftRevision;
    private String draftJson;
    private Long publishedRevision;
    private Long currentVersionId;
    private LocalDateTime inserttime;
    private LocalDateTime updatetime;
    @TableLogic(value = "1", delval = "0") private Integer isactive;
}
