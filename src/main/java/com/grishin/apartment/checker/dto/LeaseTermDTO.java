package com.grishin.apartment.checker.dto;

import lombok.Data;

@Data
public class LeaseTermDTO {
    private String date;
    private long dateTimeStamp;
    private int price;
    private int term;
}
