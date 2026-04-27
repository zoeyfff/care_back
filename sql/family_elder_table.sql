-- 家属-长者关联表（新增）
-- 用于记录每位家属账号关联了哪些长者，支持一个家属关联多个长者
DROP TABLE IF EXISTS `family_elder`;
CREATE TABLE `family_elder` (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) NOT NULL COMMENT '家属用户ID（关联user表）',
  `elder_id` bigint(0) NOT NULL COMMENT '长者ID（关联elder表）',
  `relation` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关系：子女/配偶/其他',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_elder`(`user_id`, `elder_id`) USING BTREE,
  INDEX `idx_family_elder_user`(`user_id`) USING BTREE,
  INDEX `idx_family_elder_elder`(`elder_id`) USING BTREE,
  CONSTRAINT `fk_fe_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_fe_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 示例数据：user_id=5 为测试家属，关联长者id=1和id=2
INSERT INTO `family_elder` (`user_id`, `elder_id`, `relation`) VALUES
  (5, 1, '子女'),
  (5, 2, '子女');
