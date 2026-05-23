package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.world.datamodel.Aircrafts;

@Data
@AllArgsConstructor
public class AircraftMapDto {
    private String id;
    private String acReg;
    private float lat;
    private float lon;
    private int hdg;

    public static AircraftMapDto from(Aircrafts.Aircraft a) {
        return new AircraftMapDto(
                Id.encode(a.getId()),
                a.getRegNo(),
                a.getLocationLatitude(),
                a.getLocationLongitude(),
                a.getLocationHeading());
    }
}
