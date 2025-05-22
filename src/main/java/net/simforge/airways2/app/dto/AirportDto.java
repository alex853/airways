package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AirportDto {
    private int id;
    private float latitude;
    private float longitude;
    private String iata;
    private String icao;
    private String name;
}
