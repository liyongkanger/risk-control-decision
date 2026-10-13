package com.dmp.riskdecision.policy.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 独立流程资产对应的数据库行。 */
@Data
@TableName("tb_flow")
public class FlowRow {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String flowCode;
    private String flowName;
    private String flowType;
    private Long publishedVersionId;
    @TableField(exist = false)
    private Long publishedVersionNumber;
    private LocalDateTime updatetime;
    @TableLogic(value = "1", delval = "0")
    private Integer isactive;
}
