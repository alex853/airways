package net.simforge.airways.processes.transfer.pilot;

import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.ops.GeoOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PilotTransferLauncher {
    private static final Logger log = LoggerFactory.getLogger(PilotTransferLauncher.class);

    public static void transferToAirport(ProcessEngine engine, Session session, Person person, Airport toAirport) {
        BM.start("PilotTransferLauncher.transferToAirport");
        try {
            double distance = Geo.distance(person.getLocationCity().getCoords(), toAirport.getCoords());
            int durationMinutes = GeoOps.calcTransferDurationMinutes(distance);

            transfer(engine, session, person, distance, durationMinutes, toAirport, null, Journey.Status.TransferToAirport);
        } finally {
            BM.stop();
        }
    }

    public static void transferToCity(ProcessEngine engine, Session session, Person person, City toCity) {
        BM.start("PilotTransferLauncher.transferToCity");
        try {
            double distance = Geo.distance(person.getLocationAirport().getCoords(), toCity.getCoords());
            int durationMinutes = GeoOps.calcTransferDurationMinutes(distance);

            transfer(engine, session, person, distance, durationMinutes, null, toCity, Journey.Status.TransferToCity);
        } finally {
            BM.stop();
        }
    }

    private static void transfer(ProcessEngine engine,
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

        engine.fireEvent(session, PilotTransferStarted.class, transfer);
    }
}
