package net.simforge.airways.processes.transfer.pilot;

import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.ops.GeoOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;

public class PilotTransferLauncher {

    public static void transferToAirport(ProcessEngineScheduling scheduling, Session session, Person person, Airport toAirport) {
        BM.start("PilotTransferLauncher.transferToAirport");
        try {
            double distance = Geo.distance(person.getLocationCity().getCoords(), toAirport.getCoords());
            int durationMinutes = GeoOps.calcTransferDurationMinutes(distance);

            transfer(scheduling, session, person, distance, durationMinutes, toAirport, null, Journey.Status.TransferToAirport);
        } finally {
            BM.stop();
        }
    }

    public static void transferToCity(ProcessEngineScheduling scheduling, Session session, Person person, City toCity) {
        BM.start("PilotTransferLauncher.transferToCity");
        try {
            double distance = Geo.distance(person.getLocationAirport().getCoords(), toCity.getCoords());
            int durationMinutes = GeoOps.calcTransferDurationMinutes(distance);

            transfer(scheduling, session, person, distance, durationMinutes, null, toCity, Journey.Status.TransferToCity);
        } finally {
            BM.stop();
        }
    }

    private static void transfer(ProcessEngineScheduling scheduling,
                                 Session session,
                                 Person person,
                                 double distance,
                                 int durationMinutes,
                                 Airport toAirport,
                                 City toCity,
                                 Journey.Status journeyStatus) {
        Journey journey = new Journey();
        journey.setGroupSize(1);
        journey.setStatus(journeyStatus);
        session.save(journey);

        Transfer transfer = new Transfer();
        transfer.setToAirport(toAirport);
        transfer.setToCity(toCity);
        transfer.setJourney(journey);
        transfer.setDistance(distance);
        transfer.setDuration(durationMinutes);
        session.save(transfer);

        journey.setTransfer(transfer);
        session.update(journey);

        person.setStatus(Person.Status.OnJourney);
        person.setJourney(journey);
        session.update(person);

        Pilot pilot = PilotOps.loadPilotByPersonId(session, person.getId());
        pilot.setStatus(Pilot.Status.Travelling);
        session.update(pilot);

        scheduling.fireEvent(session, PilotTransferStarted.class, transfer);
    }
}
