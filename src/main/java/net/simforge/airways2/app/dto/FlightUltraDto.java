package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.ScheduledFlightMissionGenerator;
import net.simforge.commons.misc.JavaTime;
import net.simforge.networkview.core.report.ReportUtils;

import java.time.Duration;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
public class FlightUltraDto {
    private final String fmId;
    private final String fmSt;
    private final String acId;
    private final String acRg;
    private final String acTp;
    private final String vcSt;
    private final long vcLs;
    private final String tfId;
    private final String tfSt;
    private final String sfNo;
    private final String dep;
    private final String dest;
    private final String aLdgA; // actual landing airport
    private final String dof;
    private final String pDep;
    private final String pArr;
    private final String aDep;
    private final String aTkf;
    private final String aLdg;
    private final String aArr;
    private final String eTkf;
    private final String eLdg;
    private final String eArr;
    private final String tTkts;
    private final String rTkts;
    private final String sTkts;
    private final Integer ckdIn;
    private final Integer pOnBrd;

    public static FlightUltraDto from(
            final World world,
            final FlightMissions.Mission fm) {
        FlightTimeline timeline = FlightMissionToTimeline.byMission(fm);
        TransportFlights.Flight tf = world.transportFlights().byFlightMissionId(fm.getId()).orElse(null);
        ScheduledFlights.Flight sf = tf != null ? world.scheduledFlights().byId(tf.getScheduledFlightId()).orElse(null) : null;
        Aircrafts.Aircraft ac = world.aircrafts().byId(fm.getAircraftId()).orElse(null);
        AircraftTypes.AircraftType act = ac != null ? world.aircraftTypes().byId(ac.getAircraftTypeId()).orElse(null) : null;

        return new FlightUltraDto(
                Id.encode(fm.getId()),
                fm.getStatus().name(),

                ac != null ? Id.encode(ac.getId()) : null,
                ac != null ? ac.getRegNo() : null,
                act != null ? act.getIcao() : null,

                null,
                0,

                tf != null ? Id.encode(tf.getId()) : null,
                tf != null ? tf.getStatus().name() : null,
                sf != null ? ScheduledFlightMissionGenerator.getFlightNumberById(sf.getScheduleId()) : null,

                world.airports().getIcao(fm.getDepartureAirportId()).orElse(null),
                world.airports().getIcao(fm.getDestinationAirportId()).orElse(null),

                world.airports().byId(fm.getActualLandingAirportId()).map(Airports.Airport::getIcao).orElse(null),

                fm.getDateOfFlight().toString(),

                TimeTools.hhmmPlusDaysOrNull(fm.getPlannedDepartureWorldTime()),
                TimeTools.hhmmPlusDaysOrNull(fm.getPlannedArrivalWorldTime()),

                TimeTools.hhmmPlusDaysOrNull(fm.getActualDepartureWorldTime()),
                TimeTools.hhmmPlusDaysOrNull(fm.getActualTakeoffWorldTime()),
                TimeTools.hhmmPlusDaysOrNull(fm.getActualLandingWorldTime()),
                TimeTools.hhmmPlusDaysOrNull(fm.getActualArrivalWorldTime()),

                TimeTools.hhmmPlusDaysOrNull(timeline.getTakeoff().getEstimatedTime()),
                TimeTools.hhmmPlusDaysOrNull(timeline.getLanding().getEstimatedTime()),
                TimeTools.hhmmPlusDaysOrNull(timeline.getBlocksOn().getEstimatedTime()),

                tf != null ? tf.getTotalTickets().toString() : null,
                tf != null ? tf.getRemainedTickets().toString() : null,
                tf != null ? tf.getTotalTickets().minus(tf.getRemainedTickets()).toString() : null,
                tf != null ? (tf.getPaxCheckedIn() > 0 ? tf.getPaxCheckedIn() : null) : null,
                tf != null ? (tf.getPaxOnBoard() > 0 ? tf.getPaxOnBoard() : null) : null
        );
    }

    public FlightUltraDto applyVatsimContext(PilotContext vc) {
        return this.toBuilder()
                .vcSt(vc != null ? vc.getFlightStage().name() : null)
                .vcLs(vc != null ? Duration.between(ReportUtils.fromTimestampJava(vc.getPositionLastSeen()), JavaTime.nowUtc()).toSeconds() : 0)
                .build();
    }
}
