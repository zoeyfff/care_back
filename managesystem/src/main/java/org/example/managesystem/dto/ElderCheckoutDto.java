package org.example.managesystem.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class ElderCheckoutDto {

    @NotNull(message = "出院日期不能为空")
    private LocalDate checkoutDate;

    @NotBlank(message = "出院原因不能为空")
    private String checkoutReason;

    private String checkoutRemark;
}
