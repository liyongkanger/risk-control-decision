CREATE TABLE `tb_variable_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `variable_code` VARCHAR(64) NOT NULL COMMENT '逻辑变量码',
    `category_code` VARCHAR(16) NOT NULL COMMENT '变量主分类码',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标识，1有效',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_variable_code` (`variable_code`),
    KEY `idx_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逻辑变量当前分类';

INSERT INTO `tb_variable_category` (`variable_code`, `category_code`)
SELECT DISTINCT `variable_code`, CASE
    WHEN `variable_code` = 'uid' THEN 'USER'
    WHEN `variable_code` IN ('checkoutId', 'demoOrderAmount', 'demoMarginAfterMaxOffer') THEN 'TRANSACTION'
    WHEN `variable_code` IN ('experimentId', 'lowGroupUsers', 'lowGroupUsersUnified') THEN 'CAMPAIGN'
    WHEN `variable_code` = 'demoRemainingBudget' THEN 'BUDGET'
    ELSE 'OTHER'
END
FROM `tb_variable_version`;
