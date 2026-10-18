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
