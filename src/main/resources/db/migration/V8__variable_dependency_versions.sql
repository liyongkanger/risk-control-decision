ALTER TABLE `tb_variable_version`
    ADD COLUMN `dependency_version_ids_json` TEXT NULL COMMENT '外部来源参数引用的固定变量版本 ID';

ALTER TABLE `tb_variable_version`
    MODIFY COLUMN `url` VARCHAR(512) NULL COMMENT '外部来源固定 URL；本地来源为空';

ALTER TABLE `tb_variable_version`
    MODIFY COLUMN `result_path` VARCHAR(128) NULL COMMENT '外部响应中的标量路径；本地来源为空';
