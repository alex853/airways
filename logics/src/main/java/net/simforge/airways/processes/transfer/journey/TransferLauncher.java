package net.simforge.airways.processes.transfer.journey;

import net.simforge.airways.ops.GeoOps;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processes.journey.event.ArrivalToAirportFromCity;
import net.simforge.airways.processes.journey.event.CancelOnArrivalToCity;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class TransferLauncher {
    private static final Logger log = LoggerFactory.getLogger(TransferLauncher.class);

    public static void scheduleTransferToAirport(ProcessEngine engine, Session session, Journey journey, Airport toAirport, LocalDateTime deadline) {
        BM.start("TransferLauncher.scheduleTransferToAirport");
        try {
            journey = session.load(Journey.class, journey.getId());

            double distance = calcMaxDistance(session, journey, toAirport.getCoords());
            int durationMinutes = GeoOps.calcTransferDurationMinutes(distance);

            Transfer transfer = new Transfer();
            transfer.setToAirport(toAirport);
            transfer.setJourney(journey);
            transfer.setDistance(distance);
            transfer.setDuration(durationMinutes);
            transfer.setOnStartedStatus(Journey.Status.TransferToAirport);
            transfer.setOnFinishedEvent(ArrivalToAirportFromCity.class.getName());
            session.save(transfer);

            journey.setTransfer(transfer);
            session.update(journey);

            engine.scheduleEvent(session, TransferStarted.class, transfer, deadline.minusMinutes(durationMinutes));
        } finally {
            BM.stop();
        }
    }

    public static void startTransferToCityThenEvent(ProcessEngine engine, Session session, Journey journey, City toCity, Class<? extends Event> eventClass) {
        BM.start("TransferLauncher.startTransferToCityThenEvent");
        try {
            journey = session.load(Journey.class, journey.getId());

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

            engine.fireEvent(session, TransferStarted.class, transfer);
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

            TransferLauncher.startTransferToCityThenEvent(engine, session, journey, theBiggestCity, CancelOnArrivalToCity.class);

        } finally {
            BM.stop();
        }
    }

    private static double calcMaxDistance(Session session, Journey journey, Geo.Coords toCoords) {
        BM.start("TransferLauncher.calcMaxDistance");
        try {

            List<Person> persons = JourneyOps.getPersons(session, journey);
            //noinspection UnnecessaryLocalVariable
            final double maxDistance = persons.stream().mapToDouble(person -> {
                if (person.getLocationCity() != null)
                    return Geo.distance(person.getLocationCity().getCoords(), toCoords);
                if (person.getLocationAirport() != null)
                    return Geo.distance(person.getLocationAirport().getCoords(), toCoords);
                return 0.0;
            }).max().orElse(0.0);

            return maxDistance;
        } finally {
            BM.stop();
        }
    }
}
