package com.dmp.riskdecision.rule.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 规则集发布后的不可变版本数据库行。 */
@Data
@TableName("tb_rule_set_version")
public class RuleVersionRow {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    private String ruleSetCode;
    private Long versionNumber;
    private String definitionJson;
    private LocalDateTime inserttime;
    private LocalDateTime updatetime;
    @TableLogic(value = "1", delval = "0") private Integer isactive;
}
