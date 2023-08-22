package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.flow.City2CityFlow;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.Airport2City;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.ProcessEngineBuilder;
import net.simforge.airways.processengine.RealTimeMachine;
import net.simforge.airways.processes.journey.activity.LookingForTickets;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pilot")
@CrossOrigin
public class PilotController {

    private static final Logger log = LoggerFactory.getLogger(PilotController.class);

    @GetMapping("/status")
    public PilotStatusDto getStatus() {
        final SessionInfo sessionInfo = SessionInfo.get();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {

            Person person = (Person) session
                    .createQuery("from Person where id = :personId")
                    .setParameter("personId", sessionInfo.getPersonId())
                    .uniqueResult();

            Pilot pilot = (Pilot) session
                    .createQuery("from Pilot where id = :pilotId")
                    .setParameter("pilotId", sessionInfo.getPilotId())
                    .uniqueResult();

            if (person.getType() != Person.Type.Excluded
                || pilot.getType() != Pilot.Type.PlayerCharacter) {
                throw new IllegalArgumentException("Invalid person or pilot loaded");
            }

            return new PilotStatusDto(
                    new PersonDto(person),
                    new PilotDto(pilot)
            );
        }
    }

    @GetMapping("/travel/book")
    public void bookTravel() {
        final SessionInfo sessionInfo = SessionInfo.get();

        // todo rework it!
        RealTimeMachine timeMachine = new RealTimeMachine();
        ProcessEngine engine = ProcessEngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(AirwaysApp.getSessionFactory())
                .build();

        final int destinationCityId = 13;

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            HibernateUtils.transaction(session, () -> {

                final Person person = (Person) session
                        .createQuery("from Person where id = :personId")
                        .setParameter("personId", sessionInfo.getPersonId())
                        .uniqueResult();

                final Pilot pilot = (Pilot) session
                        .createQuery("from Pilot where id = :pilotId")
                        .setParameter("pilotId", sessionInfo.getPilotId())
                        .uniqueResult();

                // todo check person status
                // todo check pilot status
                // todo check if person journey is absent

                // find origin city
                final City originCity;
                if (person.getLocationCity() != null) {
                    originCity = person.getLocationCity();
                } else {
                    final Airport locationAirport = person.getLocationAirport();
                    final Airport2City airport2City = (Airport2City) session
                            .createQuery("from Airport2City a2c " +
                                    "where airport = :locationAirport " +
                                    "order by city.population desc")
                            .setParameter("locationAirport", locationAirport)
                            .setMaxResults(1)
                            .uniqueResult();
                    if (airport2City == null) {
                        throw new IllegalArgumentException("Unable to find origin city");
                    }
                    originCity = airport2City.getCity();
                }

                final City destinationCity = (City) session
                        .createQuery("from City where id = :destinationCityId")
                        .setParameter("destinationCityId", destinationCityId)
                        .uniqueResult();
                if (destinationCity == null) {
                    throw new IllegalArgumentException("Unable to find destination city");
                }

                final City2CityFlow c2cFlow = (City2CityFlow) session
                        .createQuery("from City2CityFlow " +
                                "where fromFlow.city = :fromCity " +
                                "  and toFlow.city = :toCity")
                        .setParameter("fromCity", originCity)
                        .setParameter("toCity", destinationCity)
                        .uniqueResult();

                // todo refactor it into JourneyOps?
                final Journey journey = new Journey();
                journey.setGroupSize(1);
                journey.setFromCity(originCity);
                journey.setToCity(destinationCity);
                journey.setC2cFlow(c2cFlow);
                journey.setStatus(Journey.Status.LookingForTickets);

                engine.startActivity(session, LookingForTickets.class, journey, timeMachine.now().plusDays(1));

                session.save(journey);
                EventLog.info(session, log, journey, String.format("PILOT TRAVEL - New journey from %s to %s", originCity.getName(), destinationCity.getName()), c2cFlow, originCity, destinationCity);

                person.setStatus(Person.Status.OnJourney);
                person.setJourney(journey);
                session.update(person);
                EventLog.info(session, log, person, String.format("PILOT TRAVEL - Decided to travel from %s to %s", originCity.getName(), destinationCity.getName()), journey);

                pilot.setStatus(Pilot.Status.Travelling);
                session.update(pilot);
                EventLog.info(session, log, pilot, String.format("PILOT TRAVEL - Decided to travel from %s to %s", originCity.getName(), destinationCity.getName()), journey);
            });
        }
    }

    private static class PilotStatusDto {
        private final PersonDto person;
        private final PilotDto pilot;

        public PilotStatusDto(PersonDto person, PilotDto pilot) {
            this.person = person;
            this.pilot = pilot;
        }

        public PersonDto getPerson() {
            return person;
        }

        public PilotDto getPilot() {
            return pilot;
        }
    }

    private static class PersonDto {
        private final String name;
        private final String surname;
        private final int originCityId;
        private final int statusCode;
        private final String statusName;
        private final Integer locationCityId;
        private final Integer locationAirportId;
        private final Integer journeyId;

        public PersonDto(Person person) {
            this.name = person.getName();
            this.surname = person.getSurname();
            this.originCityId = person.getOriginCity().getId();
            this.statusCode = person.getStatus().code();
            this.statusName = person.getStatus().name();
            this.locationCityId = person.getLocationCity() != null ? person.getLocationCity().getId() : null;
            this.locationAirportId = person.getLocationAirport() != null ? person.getLocationAirport().getId() : null;
            this.journeyId = person.getJourney() != null ? person.getJourney().getId() : null;
        }

        public String getName() {
            return name;
        }

        public String getSurname() {
            return surname;
        }

        public int getOriginCityId() {
            return originCityId;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusName() {
            return statusName;
        }

        public Integer getLocationCityId() {
            return locationCityId;
        }

        public Integer getLocationAirportId() {
            return locationAirportId;
        }

        public Integer getJourneyId() {
            return journeyId;
        }
    }

    private static class PilotDto {
        private final int statusCode;
        private final String statusName;

        public PilotDto(Pilot pilot) {
            statusCode = pilot.getStatus().code();
            statusName = pilot.getStatus().name();
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusName() {
            return statusName;
        }
    }
}
