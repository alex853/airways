package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;

import java.util.Optional;

@Data
@AllArgsConstructor
public class AircraftWithFmDto {
    private int id;
    private String acType;
    private String acReg;
    private float lat;
    private float lon;
    private int hdg;
    private String fpDep;
    private String fpDest;
    private String pDep;
    private String pArr;
    private String aTkf;
    private String eLdg;

    public static AircraftWithFmDto from(World world, Aircrafts.Aircraft a) {
        final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(a.getFlightMissionId());
        return new AircraftWithFmDto(
                a.getId(),
                world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                a.getRegNo(),
                a.getLocationLatitude(),
                a.getLocationLongitude(),
                mission.map(m -> (int) FlightMissionHelper.calculateHeading(world, a.getFlightMissionId())).orElse(0),
                mission.map(m -> world.airports().byId(m.getDepartureAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> world.airports().byId(m.getDestinationAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmOrNull(m.getPlannedDepartureWorldTime())).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmOrNull(m.getPlannedArrivalWorldTime())).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmOrNull(m.getActualTakeoffWorldTime())).orElse("n/a"),
                null);
    }
}
