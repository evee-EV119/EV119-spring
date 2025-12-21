package com.app.ev119.domain.dto.request.member;

import lombok.Data;

@Data
public class MemberStaffSignUpRequestDTO {

    private String memberEmail;
    private String memberName;
    private String memberPassword;
    private String memberPhone;

    private String licenseNumber;
    private String memberStaffIndustry;
}
