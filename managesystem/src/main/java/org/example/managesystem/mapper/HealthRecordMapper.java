package org.example.managesystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.managesystem.entity.HealthRecord;

@Mapper
public interface HealthRecordMapper extends BaseMapper<HealthRecord> {
}
