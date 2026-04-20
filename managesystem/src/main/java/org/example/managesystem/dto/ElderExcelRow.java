package org.example.managesystem.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ElderExcelRow {
    @ExcelProperty("姓名")
    private String name;
    @ExcelProperty("性别")
    private String gender;
    @ExcelProperty("身份证号")
    private String idCard;
    @ExcelProperty("联系方式")
    private String phone;
    @ExcelProperty("家属联系方式")
    private String familyContact;
    @ExcelProperty("护理等级")
    private String careLevel;
    @ExcelProperty("房间号")
    private String roomNo;
    @ExcelProperty("床位号")
    private String bedNo;
}
