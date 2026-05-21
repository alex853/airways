package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftFullDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/aircraft")
@CrossOrigin
public class AircraftController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<AircraftFullDto> getAll(@RequestParam(name = "offset", required = false) final Integer offset,
                                        @RequestParam(name = "limit", required = false) final Integer limit) {
        return worldBean.read(world -> world.aircrafts().all()
                .skip(offset != null ? offset : 0)
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(a -> AircraftFullDto.from(world, a))
                .toList());
    }
}
