package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;

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
    }
}
