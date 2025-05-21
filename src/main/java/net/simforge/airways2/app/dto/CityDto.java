package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CityDto {
    private int id;
    private int countryId;
    private String name;
    private int population;
    private float latitude;
    private float longitude;
}
