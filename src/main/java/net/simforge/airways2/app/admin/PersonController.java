package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Persons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/person")
@CrossOrigin
public class PersonController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/{id}")
    public PersonDto getById(@PathVariable("id") final int id) {
        return worldBean.read(world -> world.persons().byId(id)
                .map(p -> toDto(world, p))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person #" + id + " not found")));
    }

    /**
     * Switches the feature flag of the warp drive of the person on or off (the pilot who has it moves to any airport at once).
     */
    @PostMapping("/{id}/warp-drive")
    public String setWarpDrive(@PathVariable("id") final int id, @RequestParam("enabled") final boolean enabled) {
        return worldBean.modifySync(world -> {
            final Persons.Person person = world.persons().byId(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person #" + id + " not found"));
            person.setWarpDrive(enabled);
            // the flag is not shown in any answer of the server: it only decides which ways the travel offers
            return "done";
        });
    }

    /**
     * Switches the auto finish setting of the pilot on or off (the user cannot change the settings of the pilot yet).
     */
    @PostMapping("/{id}/auto-finish")
    public String setAutoFinish(@PathVariable("id") final int id, @RequestParam("enabled") final boolean enabled) {
        return worldBean.modifySync(world -> {
            final Persons.Person person = world.persons().byId(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person #" + id + " not found"));
            person.setAutoFinish(enabled);
            return "done";
        });
    }

    private static PersonDto toDto(final World world, final Persons.Person p) {
        final Persons.LocationType locationType = p.getLocationType();
        final String locationName = switch (locationType) {
            case City -> world.cities().byId(p.getLocationId()).map(c -> c.getName()).orElse(null);
            case Airport -> world.airports().getIcao(p.getLocationId()).orElse(null);
            default -> null;
        };

        return new PersonDto(
                p.getId(),
                p.getType() != null ? p.getType().name() : null,
                p.getState() != null ? p.getState().name() : null,
                p.getHeartbeatTime(),
                p.getHeartbeatTime() > 0 ? TimeTools.ts(p.getHeartbeatTime()) : null,
                p.getUserId(),
                p.isPlayerCharacter(),
                locationType != null ? locationType.name() : null,
                p.getLocationId(),
                locationName,
                p.getOriginCityId(),
                p.getOriginCityId() > 0 ? world.cities().byId(p.getOriginCityId()).map(c -> c.getName()).orElse(null) : null,
                p.getInitials(),
                p.getAvatarId());
    }

    @Data
    @AllArgsConstructor
    public static class PersonDto {
        private int id;
        private String type;
        private String state;
        private int heartbeatTime;
        private String heartbeatTimeTs;
        private int userId;
        private boolean playerCharacter;
        private String locationType;
        private int locationId;
        private String locationName;
        private int originCityId;
        private String originCityName;
        private String initials;
        private int avatarId;
    }
}
