-- 先允许空值以迁移历史场景，再收紧新场景的名称约束。
ALTER TABLE `tb_scene`
    ADD COLUMN `scene_name` VARCHAR(128) NULL COMMENT '场景名称';

ALTER TABLE `tb_scene`
    ADD COLUMN `description` VARCHAR(512) NULL COMMENT '场景描述';

UPDATE `tb_scene`
SET `scene_name` = `scene_code`
WHERE `scene_name` IS NULL;

ALTER TABLE `tb_scene`
    MODIFY COLUMN `scene_name` VARCHAR(128) NOT NULL COMMENT '场景名称';
