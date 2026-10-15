CREATE TABLE `tb_rule_set` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_set_code` VARCHAR(64) NOT NULL COMMENT '规则集码',
    `rule_set_name` VARCHAR(128) NOT NULL COMMENT '规则集名称',
    `draft_revision` BIGINT NOT NULL COMMENT '草稿修订号',
    `draft_json` TEXT NOT NULL COMMENT '规则集草稿 JSON',
    `published_revision` BIGINT NULL COMMENT '最近发布的草稿修订号',
    `current_version_id` BIGINT NULL COMMENT '当前发布版本 ID',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标识，1有效',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_rule_set_code` (`rule_set_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则集及当前草稿';

CREATE TABLE `tb_rule_set_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_set_code` VARCHAR(64) NOT NULL COMMENT '规则集码',
    `version_number` BIGINT NOT NULL COMMENT '不可变版本号',
    `definition_json` TEXT NOT NULL COMMENT '发布时规则定义快照',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标识，1有效',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_rule_set_version` (`rule_set_code`, `version_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则集不可变发布版本';
