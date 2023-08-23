package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.flow.City2CityFlow;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.ops.GeoOps;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.ProcessEngineBuilder;
import net.simforge.airways.processengine.RealTimeMachine;
import net.simforge.airways.processes.journey.activity.LookingForTickets;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pilot")
@CrossOrigin
public class PilotController {

    private static final Logger log = LoggerFactory.getLogger(PilotController.class);

    @GetMapping("/status")
    public PilotStatusDto getStatus() {
        final SessionInfo sessionInfo = SessionInfo.get();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {

            final Person person = session.get(Person.class, sessionInfo.getPersonId());
            final Pilot pilot = session.get(Pilot.class, sessionInfo.getPilotId());

            if (person.getType() != Person.Type.Excluded
                || pilot.getType() != Pilot.Type.PlayerCharacter) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid person or pilot loaded");
            }

            return new PilotStatusDto(
                    new PersonDto(person),
                    new PilotDto(pilot)
            );
        }
    }

    @PostMapping("/travel/book")
    public void bookTravel(@RequestParam(value = "destinationCityId") final int destinationCityId) {
        final SessionInfo sessionInfo = SessionInfo.get();

        // todo rework it!
        RealTimeMachine timeMachine = new RealTimeMachine();
        ProcessEngine engine = ProcessEngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(AirwaysApp.getSessionFactory())
                .build();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            HibernateUtils.transaction(session, () -> {

                final Person person = session.get(Person.class, sessionInfo.getPersonId());
                final Pilot pilot = session.get(Pilot.class, sessionInfo.getPilotId());

                if (person.getType() != Person.Type.Excluded
                        || pilot.getType() != Pilot.Type.PlayerCharacter) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid person or pilot loaded");
                }

                if (person.getStatus() != Person.Status.Idle
                        || person.getJourney() != null
                        || pilot.getStatus() != Pilot.Status.Idle) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status of person or pilot");
                }

                final City originCity;
                if (person.getLocationCity() != null) {
                    originCity = person.getLocationCity();
                } else {
                    final Airport locationAirport = person.getLocationAirport();
                    originCity = GeoOps.loadBiggestCityLinkedToAirport(session, locationAirport);
                    if (originCity == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to find origin city");
                    }
                }

                final City destinationCity = session.get(City.class, destinationCityId);
                if (destinationCity == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to find destination city");
                }

                final City2CityFlow c2cFlow = (City2CityFlow) session
                        .createQuery("from City2CityFlow " +
                                "where fromFlow.city = :fromCity " +
                                "  and toFlow.city = :toCity")
                        .setParameter("fromCity", originCity)
                        .setParameter("toCity", destinationCity)
                        .uniqueResult();

                final Journey journey = new Journey();
                journey.setGroupSize(1);
                journey.setFromCity(originCity);
                journey.setToCity(destinationCity);
                journey.setC2cFlow(c2cFlow);
                journey.setStatus(Journey.Status.LookingForTickets);
                session.save(journey);
                EventLog.info(session, log, journey, String.format("PILOT TRAVEL - New journey from %s to %s", originCity.getName(), destinationCity.getName()), c2cFlow, originCity, destinationCity);

                engine.startActivity(session, LookingForTickets.class, journey, timeMachine.now().plusDays(1));

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
        private final String fullName;
        private final String originCityName;
        private final String statusName;
        private final Integer locationCityId;
        private final String locationCityName;
        private final Integer locationAirportId;
        private final String locationAirportName;
        private final Integer journeyId;

        public PersonDto(Person person) {
            this.fullName = person.getName() + " " + person.getSurname();
            this.originCityName = person.getOriginCity().getCityWithCountryName();
            this.statusName = person.getStatus().toString();
            this.locationCityId = person.getLocationCity() != null ? person.getLocationCity().getId() : null;
            this.locationCityName = person.getLocationCity() != null ? person.getLocationCity().getCityWithCountryName() : null;
            this.locationAirportId = person.getLocationAirport() != null ? person.getLocationAirport().getId() : null;
            this.locationAirportName = person.getLocationAirport() != null ? person.getLocationAirport().getIcao() + " " + person.getLocationAirport().getName() : null;
            this.journeyId = person.getJourney() != null ? person.getJourney().getId() : null;
        }

        public String getFullName() {
            return fullName;
        }

        public String getOriginCityName() {
            return originCityName;
        }

        public String getStatusName() {
            return statusName;
        }

        public Integer getLocationCityId() {
            return locationCityId;
        }

        public String getLocationCityName() {
            return locationCityName;
        }

        public Integer getLocationAirportId() {
            return locationAirportId;
        }

        public String getLocationAirportName() {
            return locationAirportName;
        }

        public Integer getJourneyId() {
            return journeyId;
        }
    }

    private static class PilotDto {
        private final String statusName;

        public PilotDto(Pilot pilot) {
            statusName = pilot.getStatus().toString();
        }

        public String getStatusName() {
            return statusName;
        }
    }
}
