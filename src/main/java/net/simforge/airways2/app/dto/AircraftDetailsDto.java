package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AircraftDetailsDto {
    private final List<FlightMinDto> recentFlights;
}
