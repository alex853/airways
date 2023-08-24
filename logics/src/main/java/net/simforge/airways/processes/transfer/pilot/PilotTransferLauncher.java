package net.simforge.airways.processes.transfer.pilot;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.ops.GeoOps;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processes.journey.event.CancelOnArrivalToCity;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class PilotTransferLauncher {
    private static final Logger log = LoggerFactory.getLogger(PilotTransferLauncher.class);

    public static void transferToAirport(ProcessEngine engine, Session session, Person person, Airport toAirport) {
        BM.start("PilotTransferLauncher.transferToAirport");
        try {
            double distance = Geo.distance(person.getLocationCity().getCoords(), toAirport.getCoords());
            int durationMinutes = GeoOps.calcTransferDurationMinutes(distance);

            Journey journey = new Journey();
            journey.setGroupSize(1);
            journey.setStatus(Journey.Status.TransferToAirport);
            session.save(journey);

            Transfer transfer = new Transfer();
            transfer.setToAirport(toAirport);
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
        } finally {
            BM.stop();
        }
    }

    public static void startTransferToCityThenEvent(ProcessEngine engine, Session session, Journey journey, City toCity, Class<? extends Event> eventClass) {
        BM.start("TransferLauncher.startTransferToCityThenEvent");
        try {
/*            journey = session.load(Journey.class, journey.getId());

            double distance = calcMaxDistance(session, journey, toCity.getCoords());
            int durationMinutes = GeoOps.calcTransferDurationMinutes(distance);

            Transfer transfer = new Transfer();
            transfer.setToCity(toCity);
            transfer.setJourney(journey);
            transfer.setDistance(distance);
            transfer.setDuration(durationMinutes);
            transfer.setOnStartedStatus(Journey.Status.TransferToCity);
            transfer.setOnFinishedEvent(eventClass.getName());
            session.save(transfer);

            journey.setTransfer(transfer);
            session.update(journey);

            engine.fireEvent(session, TransferStarted.class, transfer);*/
        } finally {
            BM.stop();
        }
    }

    public static void startTransferToBiggestCityThenCancel(ProcessEngine engine, Session session, Journey journey) {
        BM.start("TransferLauncher.startTransferToBiggestCityThenCancel");
        try {
            List<Person> persons = JourneyOps.getPersons(session, journey);
            List<Airport> airports = persons.stream().map(Person::getLocationAirport).collect(Collectors.toList());
            Airport currentAirport = airports.get(0);

            City theBiggestCity = GeoOps.loadBiggestCityLinkedToAirport(session, currentAirport);

            //noinspection ConstantConditions
            EventLog.info(session, log, journey, "Transfer & Cancel to city " + theBiggestCity.getName());

            PilotTransferLauncher.startTransferToCityThenEvent(engine, session, journey, theBiggestCity, CancelOnArrivalToCity.class);

        } finally {
            BM.stop();
        }
    }
}
