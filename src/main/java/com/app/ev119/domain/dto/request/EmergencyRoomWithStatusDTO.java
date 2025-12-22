package com.app.ev119.domain.dto.request;

import lombok.Data;

@Data
public class EmergencyRoomWithStatusDTO {

    private String hpid;
    private String dutyName;
    private String dutyAddr;
    private String dutyTel3;
    private Double wgs84Lat;
    private Double wgs84Lon;
    private Double distance;

    private Integer hvec;  // 응급실 가용병상
    private Integer hvgc;  // 총병상
}
