package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;

@Data
@AllArgsConstructor
class EnhancedFlightMissionDto {
    private int id;
    private int acId;
    private String acReg;
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

    public static EnhancedFlightMissionDto fromMission(
            final World world,
            final FlightMissions.Mission mission) {
        return new EnhancedFlightMissionDto(
                mission.getId(),
                mission.getAircraftId(),
                world.aircrafts().byId(mission.getAircraftId()).orElseThrow().getRegNo(),
                mission.getStatus().name(),
                world.airports().byId(mission.getDepartureAirportId()).orElseThrow().getIcao(),
                world.airports().byId(mission.getDestinationAirportId()).orElseThrow().getIcao(),
                WebTime.ymdOrNull(mission.getPlannedDepartureTime()),
                WebTime.hmOrNull(mission.getPlannedDepartureTime()),
                WebTime.hmOrNull(mission.getPlannedArrivalTime()),
                WebTime.hmOrNull(mission.getActualDepartureTime()),
                WebTime.hmOrNull(mission.getActualTakeoffTime()),
                WebTime.hmOrNull(mission.getActualLandingTime()),
                WebTime.hmOrNull(mission.getActualArrivalTime()));
    }
}
