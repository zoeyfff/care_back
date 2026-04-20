-- 数据库结构优化建议脚本（在测试环境先执行）

-- 1) user 在 MySQL 中可能与系统表关键字冲突，建议统一改为 sys_user
-- RENAME TABLE `user` TO sys_user;

-- 2) 补充外键（可按业务容忍度决定是否开启）
ALTER TABLE user_role
    ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES `user`(id),
    ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id);

ALTER TABLE role_permission
    ADD CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role(id),
    ADD CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id);

ALTER TABLE care_task
    ADD CONSTRAINT fk_care_task_elder FOREIGN KEY (elder_id) REFERENCES elder(id);

ALTER TABLE health_record
    ADD CONSTRAINT fk_health_record_elder FOREIGN KEY (elder_id) REFERENCES elder(id);

ALTER TABLE medication_record
    ADD CONSTRAINT fk_medication_record_elder FOREIGN KEY (elder_id) REFERENCES elder(id);

ALTER TABLE checkin
    ADD CONSTRAINT fk_checkin_elder FOREIGN KEY (elder_id) REFERENCES elder(id);

ALTER TABLE bill
    ADD CONSTRAINT fk_bill_elder FOREIGN KEY (elder_id) REFERENCES elder(id);

ALTER TABLE bill_item
    ADD CONSTRAINT fk_bill_item_bill FOREIGN KEY (bill_id) REFERENCES bill(id);

ALTER TABLE feedback
    ADD CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES `user`(id);

ALTER TABLE file_info
    ADD CONSTRAINT fk_file_upload_user FOREIGN KEY (upload_user) REFERENCES `user`(id);

-- 3) 增加唯一约束（避免重复授权）
ALTER TABLE user_role ADD UNIQUE KEY uk_user_role(user_id, role_id);
ALTER TABLE role_permission ADD UNIQUE KEY uk_role_permission(role_id, permission_id);

-- 4) 关键索引
CREATE INDEX idx_elder_name ON elder(name);
CREATE INDEX idx_elder_care_level ON elder(care_level);
CREATE INDEX idx_care_task_elder_status ON care_task(elder_id, status);
CREATE INDEX idx_health_record_elder_time ON health_record(elder_id, record_time);
CREATE INDEX idx_medication_elder_time ON medication_record(elder_id, take_time);
CREATE INDEX idx_bill_elder_status ON bill(elder_id, status);
CREATE INDEX idx_notice_type ON notice(type);

-- 5) 建议新增字段（需业务确认）
-- alter table file_info add column md5 varchar(64) comment '文件md5';
-- alter table bill add column billing_month varchar(7) comment '账单月份，如2026-04';
-- alter table elder add column deleted tinyint default 0 comment '逻辑删除标志';
