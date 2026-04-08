package net.simforge.airways2.app.tools;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;

@Data
@AllArgsConstructor
public class EnhancedFlightMissionDto {
    private int id;
    private int acId;
    private String acReg;
    private String acType;
    private String st;
    private String dep;
    private String dest;
    private String dof;
    private String pDep;
    private String pArr;
    private String aDep;
    private String aTof;
    private String aLdg;
    private String aArr;
    private String aLdgA;

    public static EnhancedFlightMissionDto fromMission(
            final World world,
            final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        return new EnhancedFlightMissionDto(
                mission.getId(),
                mission.getAircraftId(),
                aircraft.getRegNo(),
                world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao(),
                mission.getStatus().name(),
                world.airports().byId(mission.getDepartureAirportId()).orElseThrow().getIcao(),
                world.airports().byId(mission.getDestinationAirportId()).orElseThrow().getIcao(),
                mission.getDateOfFlight().toString(),
                TimeTools.hhmmOrNull(mission.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(mission.getPlannedArrivalWorldTime()),
                TimeTools.hhmmOrNull(mission.getActualDepartureWorldTime()),
                TimeTools.hhmmOrNull(mission.getActualTakeoffWorldTime()),
                TimeTools.hhmmOrNull(mission.getActualLandingWorldTime()),
                TimeTools.hhmmOrNull(mission.getActualArrivalWorldTime()),
                world.airports().byId(mission.getActualLandingAirportId()).map(Airports.Airport::getIcao).orElse(null));
    }
}
