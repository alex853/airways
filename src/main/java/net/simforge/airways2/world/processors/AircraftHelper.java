package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;

import static com.google.common.base.Preconditions.checkArgument;

public class AircraftHelper {
    public static void moveParkedAircraftToAnotherAirport(
            final World world,
            final Aircrafts.Aircraft aircraft,
            final Airports.Airport targetAirport) {
        checkArgument(aircraft.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport);

        aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
        aircraft.setLocationAirportId(targetAirport.getId());
        aircraft.setLocationLatitude(targetAirport.getLatitude());
        aircraft.setLocationLongitude(targetAirport.getLongitude());
        aircraft.setLocationAltitude(0);

        aircraft.setLastUpdated(world.getWorldTime());
    }

    public static Aircrafts.Aircraft releaseAndParkAircraft(World world, Aircrafts.Aircraft aircraft) {
        FlightMissions.Mission mission = world.flightMissions().byId(aircraft.getFlightMissionId()).orElseThrow();
        if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.Flying) {
            aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);

            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
            aircraft.setFlightMissionId(0);

            aircraft.setLastUpdated(world.getWorldTime());
        } else {
            Airports.Airport departureAirport = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();

            aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
            aircraft.setLocationAirportId(mission.getDepartureAirportId());
            aircraft.setLocationLatitude(departureAirport.getLatitude());
            aircraft.setLocationLongitude(departureAirport.getLongitude());
            aircraft.setLocationAltitude(0);

            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
            aircraft.setFlightMissionId(0);

            aircraft.setLastUpdated(world.getWorldTime());
        }
        return aircraft;
    }
}
