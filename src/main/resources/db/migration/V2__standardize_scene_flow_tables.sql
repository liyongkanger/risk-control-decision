-- 保留 V1 中已发布版本的 ID 和场景指针，再切换到统一业务表结构。
CREATE TABLE `tb_flow_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scene_code` VARCHAR(64) NOT NULL COMMENT '场景码',
    `version_number` BIGINT NOT NULL COMMENT '流程版本号',
    `graph_json` LONGTEXT NOT NULL COMMENT '流程图快照 JSON',
    `chain_id` VARCHAR(128) NOT NULL COMMENT 'LiteFlow 执行链标识',
    `compiled_el` TEXT NOT NULL COMMENT '编译后的 LiteFlow EL',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标记：1有效，0删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_flow_version_scene_number` (`scene_code`, `version_number`),
    UNIQUE KEY `uniq_flow_version_chain_id` (`chain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景流程版本表';

CREATE TABLE `tb_scene` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scene_code` VARCHAR(64) NOT NULL COMMENT '场景码',
    `published_version_id` BIGINT NULL COMMENT '当前已发布流程版本 ID',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标记：1有效，0删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_scene_code` (`scene_code`),
    KEY `idx_scene_published_version` (`published_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务场景表';

CREATE TABLE `tb_flow_draft` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scene_code` VARCHAR(64) NOT NULL COMMENT '场景码',
    `graph_json` LONGTEXT NOT NULL COMMENT '草稿流程图 JSON',
    `revision` BIGINT NOT NULL COMMENT '草稿修订号',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标记：1有效，0删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_flow_draft_scene_code` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景流程草稿表';

INSERT INTO `tb_flow_version`
    (`id`, `scene_code`, `version_number`, `graph_json`, `chain_id`, `compiled_el`, `inserttime`, `updatetime`)
SELECT `id`, `scene_code`, `version_number`, `graph_json`, `chain_id`, `compiled_el`, `created_at`, `created_at`
FROM `flow_version`;

INSERT INTO `tb_scene` (`scene_code`, `published_version_id`, `inserttime`, `updatetime`)
SELECT `scene_code`, `published_version_id`, `updated_at`, `updated_at`
FROM `scene`;

INSERT INTO `tb_flow_draft` (`scene_code`, `graph_json`, `revision`, `inserttime`, `updatetime`)
SELECT `scene_code`, `graph_json`, `revision`, `updated_at`, `updated_at`
FROM `flow_draft`;

DROP TABLE `flow_draft`;
DROP TABLE `scene`;
DROP TABLE `flow_version`;
