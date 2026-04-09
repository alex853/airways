package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// constantly running process which finds some, few, not too many W or J journeys in looking for tickets status
// and pick them up - mark them as 'special processing'
// save info into dedicated storage and sets expiration date - lets start from 24 hours
// that means 'mission', or 'job', or 'order'
// when mission expires without being picked up by any pilot, the journey is released back to 'normal processing'

// the screen with available missions shows list of missions, from-to airport,
// offered aircraft and its proposed full itinerary, including total flight time,
// plus mission cost, and pilot's paycheck

// the pilot can take the mission, this means pilot commits to make all the flights for the mission within 48 hours window
// all the flight missions are created, pilot can specify and change departure time of any mission
// departure time of passenger flight influences to journey's checkin time
// checkin time and boarding time can be shortened due to small airplane size
// comparison of planned and actual departure & arrival times can be used as a measure for kind of bonus or something else

// "back to base" flight can be omitted *** this will be reviewed later...... there is an obligation to fly back to base
// however if someone flies two missions in a row, this "back to base" flight and then positioning flight is weird
// on the other hand, if someone drops, leaves plane unattended for days or weeks, this should be avoided and punished

// so, the mission has execution time window (48 hours), the airplane should be returned back to the base by end
// of the window, if it is not returned - cancel the mission, pay for flown and charge the fine for non-flown leg
// on the other hand, if the "passenger" flight is already flown, this aircraft can be assigned to other missions,
// including by other pilots
// if such aircraft is assigned to another mission, the previous mission is considered finished and payments can be done

// mission cancellation.....
public class BusyBirdsMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsMissionGenerator.class);
    private static final int maxJourneyCount = 10;

    private static long lastExecution;

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 60 * 60 * 1000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        List<Journeys.Journey> journeysToBook = world.busyBirdsMissionControl().getJourneysToBook();
        int journeysToPickUp = maxJourneyCount - journeysToBook.size();

        if (journeysToPickUp <= 0) {
            log.info("there are {} journey(s) to book available, limit is set to {} journeys, no need to pick up more", journeysToBook.size(), maxJourneyCount);
            return;
        }

        log.info("there are {} journey(s) to book available, limit is set to {} journeys, let's pick up one more", journeysToBook.size(), maxJourneyCount);

        List<Journeys.Journey> foundJourneys = world.journeys().filter(world.journeys().byNoSpecialProcessing())
                .filter(j -> j.getStatus() == Journeys.Status.LookingForTickets)
                .filter(j -> j.getCabinService() == CabinLayout.Service.F)
                .toList();
        if (foundJourneys.isEmpty()) {
            log.info("no first class journey looking for tickets found, nothing to pick up so far");
            return;
        }

        int index = Tools.random(0, foundJourneys.size()-1);
        Journeys.Journey journey = foundJourneys.get(index);

        log.info("journey {} - from {} to {}, pax {} - picked up",
                journey.toString(),
                world.cities().byId(journey.getFromCityId()).get().getName(),
                world.cities().byId(journey.getToCityId()).get().getName(),
                journey.getGroupSize());
        journey.setSpecialProcessing(true);
    }
}
