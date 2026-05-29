package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;

import java.util.Optional;

@Data
@AllArgsConstructor
public class AircraftFullDto {
    private String id;
    private String acType;
    private String acReg;
    private String aoName;
    private String lcSt;
    private String lcApt;
    private String opSt;
    private float lat;
    private float lon;
    private int hdg;
    private int alt;
    private String fmId;
    private String dep;
    private String dest;
    private String pDep;
    private String pArr;
    private String aTkf;
    private String eLdg;
    private String fsTime;
    private int fsCycles;
    private String lU;

    public static AircraftFullDto from(World world, Aircrafts.Aircraft a, boolean encodeIds) {
        final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(a.getFlightMissionId());
        return new AircraftFullDto(
                Id.encode(a.getId(), encodeIds),
                world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                a.getRegNo(),
                world.aircraftOperators().byId(a.getAircraftOperatorId()).map(AircraftOperators.AircraftOperator::getName).orElse(null),
                a.getLocationStatus().name(),
                world.airports().byId(a.getLocationAirportId()).map(Airports.Airport::getIcao).orElse(null),
                a.getOperationalStatus().name(),
                a.getLocationLatitude(),
                a.getLocationLongitude(),
                a.getLocationHeading(),
                a.getLocationAltitude(),
                Id.encodeOrNull(a.getFlightMissionId(), encodeIds),
                mission.map(m -> world.airports().byId(m.getDepartureAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> world.airports().byId(m.getDestinationAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmOrNull(m.getPlannedDepartureWorldTime())).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmOrNull(m.getPlannedArrivalWorldTime())).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmOrNull(m.getActualTakeoffWorldTime())).orElse("n/a"),
                null,
                TimeTools.minutesToHmm(a.getFlightTime()),
                a.getFlownCycles(),
                a.getLastUpdated() > 0 ? Time.toLdt(a.getLastUpdated()).toString() : null);
    }
}
