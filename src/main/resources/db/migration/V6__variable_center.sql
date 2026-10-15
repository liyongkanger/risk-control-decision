CREATE TABLE `tb_variable_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `variable_code` VARCHAR(64) NOT NULL COMMENT '变量码',
    `variable_name` VARCHAR(128) NOT NULL COMMENT '变量名称',
    `version_number` BIGINT NOT NULL COMMENT '不可变版本号',
    `value_type` VARCHAR(16) NOT NULL COMMENT 'STRING、BOOLEAN 或 DECIMAL',
    `source_type` VARCHAR(16) NOT NULL COMMENT '当前为 HTTP_GET',
    `url` VARCHAR(512) NOT NULL COMMENT '固定只读 HTTP URL',
    `timeout_ms` INT NOT NULL COMMENT '超时毫秒数',
    `parameter_names_json` TEXT NOT NULL COMMENT '查询参数名 JSON',
    `result_path` VARCHAR(128) NOT NULL COMMENT '响应 JSON 中的标量路径',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标识，1有效',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_variable_version` (`variable_code`, `version_number`),
    KEY `idx_variable_code` (`variable_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变量中心不可变版本';

DROP TABLE `tb_rpc_operation_version`;
