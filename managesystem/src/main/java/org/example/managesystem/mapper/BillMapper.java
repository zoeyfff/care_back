package org.example.managesystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.managesystem.entity.Bill;

@Mapper
public interface BillMapper extends BaseMapper<Bill> {
}
