package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;

import java.util.Optional;

@Data
@AllArgsConstructor
public class AircraftMapDto {
    private int id;
    private String acReg;
    private float lat;
    private float lon;
    private int hdg;

    public static AircraftMapDto from(World world, Aircrafts.Aircraft a) {
        final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(a.getFlightMissionId());
        return new AircraftMapDto(
                a.getId(),
                a.getRegNo(),
                a.getLocationLatitude(),
                a.getLocationLongitude(),
                mission.map(m -> (int) FlightMissionHelper.calculateHeading(world, a.getFlightMissionId())).orElse(0)); // todo ak0 heading
    }
}
