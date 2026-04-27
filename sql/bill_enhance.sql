-- ============================================================
-- 账单管理功能扩展 SQL
-- 包含：账单表扩展、费用标准表、额外费用表、邮件日志表
-- ============================================================

-- 1. 扩展 bill 表：增加计费周期、邮箱发送状态
ALTER TABLE `bill`
  ADD COLUMN `billing_cycle` varchar(7) NULL DEFAULT NULL COMMENT '计费周期，格式 YYYY-MM，如 2025-04' AFTER `elder_id`,
  ADD COLUMN `email_sent` tinyint(1) NOT NULL DEFAULT 0 COMMENT '邮件是否已发送：0未发 1已发送' AFTER `status`,
  ADD COLUMN `email_sent_time` datetime NULL DEFAULT NULL COMMENT '邮件发送时间' AFTER `email_sent`,
  ADD COLUMN `paid_time` datetime NULL DEFAULT NULL COMMENT '支付时间' AFTER `email_sent_time`,
  ADD COLUMN `pay_method` varchar(30) NULL DEFAULT NULL COMMENT '支付方式：微信/支付宝/银行转账/现金' AFTER `paid_time`,
  ADD COLUMN `operator_id` bigint NULL DEFAULT NULL COMMENT '操作人ID（生成账单/确认支付）' AFTER `pay_method`,
  ADD COLUMN `operator_name` varchar(50) NULL DEFAULT NULL COMMENT '操作人姓名' AFTER `operator_id`;

-- 2. 费用标准表（每月更新一次，系统按此标准生成账单）
DROP TABLE IF EXISTS `billing_standard`;
CREATE TABLE `billing_standard` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `care_level` varchar(50) NOT NULL COMMENT '护理等级（一级/二级/三级/四级/五级），对应 elder.care_level',
  `care_fee` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '月度护理费（元）',
  `meal_fee` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '月度餐饮费（元）',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expire_date` date NULL DEFAULT NULL COMMENT '失效日期，为空表示当前生效',
  `remark` varchar(255) NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_billing_std_care_level`(`care_level`),
  INDEX `idx_billing_std_effective`(`effective_date`, `expire_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '费用标准表';

-- 3. 房间类型基础费率表（床位费按房间类型计算）
DROP TABLE IF EXISTS `room_fee_standard`;
CREATE TABLE `room_fee_standard` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_type` varchar(50) NOT NULL COMMENT '房型：单人间/双人间/四人间/多人间，对应 room.room_type',
  `bed_fee` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '月度床位费（元/人）',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expire_date` date NULL DEFAULT NULL COMMENT '失效日期',
  `remark` varchar(255) NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_room_fee_type`(`room_type`),
  INDEX `idx_room_fee_effective`(`effective_date`, `expire_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '房间类型基础费率表';

-- 4. 额外费用表（零星费用，如特殊护理、药品、材料等）
DROP TABLE IF EXISTS `extra_charge`;
CREATE TABLE `extra_charge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `elder_id` bigint NOT NULL COMMENT '长者ID',
  `charge_date` date NOT NULL COMMENT '费用发生日期',
  `charge_type` varchar(50) NOT NULL COMMENT '费用类型：医疗/药品/材料/餐费/其他',
  `item_name` varchar(200) NOT NULL COMMENT '费用项目名称',
  `amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '金额（元）',
  `remark` varchar(255) NULL DEFAULT NULL,
  `creator_id` bigint NULL DEFAULT NULL COMMENT '记录人ID',
  `creator_name` varchar(50) NULL DEFAULT NULL COMMENT '记录人姓名',
  `billing_cycle` varchar(7) NULL DEFAULT NULL COMMENT '所属计费周期 YYYY-MM，便于归账',
  `billed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已入账：0未入账 1已入账',
  `bill_id` bigint NULL DEFAULT NULL COMMENT '入账到的账单ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_extra_charge_elder`(`elder_id`),
  INDEX `idx_extra_charge_date`(`charge_date`),
  INDEX `idx_extra_charge_cycle`(`billing_cycle`),
  INDEX `idx_extra_charge_billed`(`billed`),
  CONSTRAINT `fk_extra_charge_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '额外费用表';

-- 5. 账单邮件发送日志表
DROP TABLE IF EXISTS `bill_email_log`;
CREATE TABLE `bill_email_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bill_id` bigint NOT NULL COMMENT '账单ID',
  `elder_id` bigint NOT NULL COMMENT '长者ID',
  `elder_name` varchar(50) NOT NULL COMMENT '长者姓名（冗余）',
  `family_user_id` bigint NOT NULL COMMENT '家属用户ID',
  `family_email` varchar(200) NOT NULL COMMENT '家属邮箱',
  `family_name` varchar(50) NOT NULL COMMENT '家属姓名（冗余）',
  `billing_cycle` varchar(7) NOT NULL COMMENT '计费周期',
  `total_amount` decimal(10, 2) NOT NULL COMMENT '账单金额（冗余）',
  `send_status` tinyint NOT NULL DEFAULT 0 COMMENT '发送状态：0待发送 1发送成功 2发送失败',
  `send_result` varchar(500) NULL DEFAULT NULL COMMENT '发送结果或错误信息',
  `send_time` datetime NULL DEFAULT NULL COMMENT '实际发送时间',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_email_log_bill`(`bill_id`),
  INDEX `idx_email_log_elder`(`elder_id`),
  INDEX `idx_email_log_family`(`family_user_id`),
  INDEX `idx_email_log_status`(`send_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '账单邮件发送日志表';

-- ============================================================
-- 初始化费用标准数据（请根据实际机构情况调整价格）
-- ============================================================

-- 护理费标准（按护理等级）
INSERT INTO `billing_standard` (`care_level`, `care_fee`, `meal_fee`, `effective_date`, `remark`) VALUES
  ('一级护理', 800.00, 600.00, '2025-01-01', '基础护理服务'),
  ('二级护理', 1200.00, 600.00, '2025-01-01', '标准护理服务'),
  ('三级护理', 1800.00, 600.00, '2025-01-01', '加强护理服务'),
  ('四级护理', 2500.00, 600.00, '2025-01-01', '特级护理服务'),
  ('五级护理', 3500.00, 600.00, '2025-01-01', '专护服务');

-- 床位费标准（按房间类型）
INSERT INTO `room_fee_standard` (`room_type`, `bed_fee`, `effective_date`, `remark`) VALUES
  ('单人间', 3000.00, '2025-01-01', '单人间床位费'),
  ('双人间', 1800.00, '2025-01-01', '双人间床位费'),
  ('四人间', 1200.00, '2025-01-01', '四人间床位费'),
  ('多人间', 800.00, '2025-01-01', '多人间床位费');
