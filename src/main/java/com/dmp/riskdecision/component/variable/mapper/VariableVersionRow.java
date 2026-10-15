package com.dmp.riskdecision.component.variable.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 变量不可变版本对应的数据库行。 */
@Data
@TableName("tb_variable_version")
public class VariableVersionRow {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    private String variableCode;
    private String variableName;
    private Long versionNumber;
    private String valueType;
    private String sourceType;
    private String url;
    private Integer timeoutMs;
    private String parameterNamesJson;
    private String resultPath;
    private LocalDateTime inserttime;
    private LocalDateTime updatetime;
    @TableLogic(value = "1", delval = "0") private Integer isactive;
}
