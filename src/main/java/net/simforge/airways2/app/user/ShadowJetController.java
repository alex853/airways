package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.world.computations.PilotPayRates;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.processors.ShadowJetPilotPay;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * The booking of ShadowJet flights: the page "Book Flight" of the site. For now the reference of the aircraft types and the terms of the pilot on a type.
 */
@RestController
@RequestMapping("/shadow-jet")
@CrossOrigin
public class ShadowJetController {
    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * The reference of the aircraft types a ShadowJet flight can be booked on: the types of the world which have performance data, because the time of the
     * arrival of a flight is calculated from it. The types by the ICAO code.
     */
    @GetMapping("/aircraft-types")
    public List<AircraftTypeDto> getAircraftTypes() {
        return worldBean.read(world -> world.aircraftTypes().all()
                .filter(type -> AircraftPerformanceDatabase.getPerformance(type.getIcao()).isPresent())
                .sorted(Comparator.comparing(AircraftTypes.AircraftType::getIcao))
                .map(type -> new AircraftTypeDto(type.getIcao()))
                .toList());
    }

    /**
     * What the pilot of the user gets on a type of aircraft at ShadowJet: the qualifications of the pilot on the type, the base hourly rate, the deductions and the
     * shares of ShadowJet. The same answer as the one of BusyBirds (GET /busy-birds/pilot-terms), but by the type of aircraft, not by an aircraft, and with the rates
     * of ShadowJet (PilotPayRates.SHADOW_JET).
     */
    @GetMapping("/pilot-terms")
    public BusyBirdsController.PilotTermsDto getPilotTerms(@RequestAttribute("userId") final int userId,
                                                           @RequestParam("type") final String aircraftType) {
        return worldBean.read(world -> {
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The user has no pilot (person)");
            }
            if (world.aircraftTypes().byIcao(aircraftType).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown aircraft type " + aircraftType);
            }
            return BusyBirdsController.buildPilotTerms(world, personId, aircraftType, ShadowJetPilotPay.baseHourlyRate(aircraftType), PilotPayRates.SHADOW_JET);
        });
    }

    @Data
    @AllArgsConstructor
    public static class AircraftTypeDto {
        private String icao;
        // TODO the name of the type
    }
}
