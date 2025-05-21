package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CountryDto {
    private int id;
    private String code;
    private String name;
}
