CREATE TABLE `tb_rpc_operation_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operation_code` VARCHAR(64) NOT NULL COMMENT '操作码',
    `operation_name` VARCHAR(128) NOT NULL COMMENT '操作名称',
    `version_number` BIGINT NOT NULL COMMENT '不可变版本号',
    `url` VARCHAR(512) NOT NULL COMMENT '只读 HTTP URL',
    `timeout_ms` INT NOT NULL COMMENT '请求超时毫秒',
    `parameter_names_json` TEXT NOT NULL COMMENT '允许的查询参数名称',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除：1有效，0删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_rpc_operation_version` (`operation_code`, `version_number`),
    KEY `idx_rpc_operation_code` (`operation_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='只读 HTTP 操作不可变版本';
