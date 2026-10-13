-- 流程成为独立资产，场景只绑定入口流程和已选中的发布版本。
CREATE TABLE `tb_flow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `flow_code` VARCHAR(64) NOT NULL COMMENT '流程码',
    `flow_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
    `flow_type` VARCHAR(32) NOT NULL DEFAULT 'DECISION' COMMENT '流程类型',
    `published_version_id` BIGINT NULL COMMENT '流程当前发布版本 ID',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标记：1有效，0删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_flow_code` (`flow_code`),
    KEY `idx_flow_published_version` (`published_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立流程资产表';

INSERT INTO `tb_flow` (`flow_code`, `flow_name`, `published_version_id`, `inserttime`, `updatetime`, `isactive`)
SELECT `scene_code`, `scene_name`, `published_version_id`, `inserttime`, `updatetime`, `isactive`
FROM `tb_scene`;

-- 兼容历史上无场景记录但仍保留草稿或版本的流程数据。
INSERT INTO `tb_flow` (`flow_code`, `flow_name`)
SELECT DISTINCT v.`scene_code`, v.`scene_code`
FROM `tb_flow_version` v LEFT JOIN `tb_flow` f ON f.`flow_code` = v.`scene_code`
WHERE f.`id` IS NULL;

INSERT INTO `tb_flow` (`flow_code`, `flow_name`)
SELECT DISTINCT d.`scene_code`, d.`scene_code`
FROM `tb_flow_draft` d LEFT JOIN `tb_flow` f ON f.`flow_code` = d.`scene_code`
WHERE f.`id` IS NULL;

ALTER TABLE `tb_scene` ADD COLUMN `flow_id` BIGINT NULL COMMENT '入口流程资产 ID';
ALTER TABLE `tb_flow_draft` ADD COLUMN `flow_id` BIGINT NULL COMMENT '所属流程资产 ID';
ALTER TABLE `tb_flow_version` ADD COLUMN `flow_id` BIGINT NULL COMMENT '所属流程资产 ID';

UPDATE `tb_scene` SET `flow_id` = (SELECT f.`id` FROM `tb_flow` f WHERE f.`flow_code` = `tb_scene`.`scene_code`);
UPDATE `tb_flow_draft` SET `flow_id` = (SELECT f.`id` FROM `tb_flow` f WHERE f.`flow_code` = `tb_flow_draft`.`scene_code`);
UPDATE `tb_flow_version` SET `flow_id` = (SELECT f.`id` FROM `tb_flow` f WHERE f.`flow_code` = `tb_flow_version`.`scene_code`);

ALTER TABLE `tb_scene` MODIFY COLUMN `flow_id` BIGINT NOT NULL COMMENT '入口流程资产 ID';
ALTER TABLE `tb_flow_draft` MODIFY COLUMN `flow_id` BIGINT NOT NULL COMMENT '所属流程资产 ID';
ALTER TABLE `tb_flow_version` MODIFY COLUMN `flow_id` BIGINT NOT NULL COMMENT '所属流程资产 ID';

-- 历史场景码已转为流程资产，草稿和版本不再保存场景归属。
ALTER TABLE `tb_flow_draft` DROP INDEX `uniq_flow_draft_scene_code`;
ALTER TABLE `tb_flow_version` DROP INDEX `uniq_flow_version_scene_number`;
ALTER TABLE `tb_flow_draft` DROP COLUMN `scene_code`;
ALTER TABLE `tb_flow_version` DROP COLUMN `scene_code`;

ALTER TABLE `tb_scene` ADD KEY `idx_scene_flow` (`flow_id`);
ALTER TABLE `tb_flow_draft` ADD UNIQUE KEY `uniq_flow_draft_flow` (`flow_id`);
ALTER TABLE `tb_flow_version` ADD UNIQUE KEY `uniq_flow_version_flow_number` (`flow_id`, `version_number`);
