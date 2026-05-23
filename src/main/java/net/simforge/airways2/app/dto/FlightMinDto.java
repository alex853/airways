package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;

@Data
@AllArgsConstructor
public class FlightMinDto {
    private final String fmId;
    private final String acId;
    private final String acRg;
    private final String acTp;
    private final String st;
    private final String dep;
    private final String dest;
    private final String dof;
    private final String pDep;
    private final String pArr;
    private final String aDep;
    private final String aTkf;
    private final String aLdg;
    private final String aArr;
    private final String aLdgA;

    public static FlightMinDto from(
            final World world,
            final FlightMissions.Mission fm) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(fm.getAircraftId()).orElseThrow();
        return new FlightMinDto(
                Id.encode(fm.getId()),
                Id.encode(fm.getAircraftId()),
                aircraft.getRegNo(),
                world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao(),
                fm.getStatus().name(),
                world.airports().byId(fm.getDepartureAirportId()).orElseThrow().getIcao(),
                world.airports().byId(fm.getDestinationAirportId()).orElseThrow().getIcao(),
                fm.getDateOfFlight().toString(),
                TimeTools.hhmmOrNull(fm.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(fm.getPlannedArrivalWorldTime()),
                TimeTools.hhmmOrNull(fm.getActualDepartureWorldTime()),
                TimeTools.hhmmOrNull(fm.getActualTakeoffWorldTime()),
                TimeTools.hhmmOrNull(fm.getActualLandingWorldTime()),
                TimeTools.hhmmOrNull(fm.getActualArrivalWorldTime()),
                world.airports().byId(fm.getActualLandingAirportId()).map(Airports.Airport::getIcao).orElse(null));
    }
}
