package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/manual-dispatch")
@CrossOrigin
public class ManualDispatchController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/available-aircraft")
    public ResponseEntity<List<AircraftDto>> getAvailableAircraft() {
        final World world = worldBean.world();
        final Collection<Aircrafts.Aircraft> availableAircraft =
                world.aircrafts().all().stream()
                        .filter(a -> a.getOperationalStatus() == Aircrafts.OperationalStatus.Idle
                                || a.getOperationalStatusRaw() == 0) // todo ak1 temporal fix due to enum code-vs-ordinal issue, remove it once all aircraft statuses will be reassigned
                        .toList();
        return ResponseEntity.ok(availableAircraft.stream()
                .map(a -> new AircraftDto(
                        a.getId(),
                        world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                        a.getRegNo(),
                        world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao()))
                .toList());
    }

    @PostMapping("/dispatch-flight")
    public ResponseEntity<?> dispatchFlight() {
        throw new UnsupportedOperationException();
    }

    @Data
    @AllArgsConstructor
    private static class AircraftDto {
        private int id;
        private String type;
        private String regNo;
        private String location;
    }
}
