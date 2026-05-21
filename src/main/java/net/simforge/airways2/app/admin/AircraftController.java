package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftFullDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/aircraft")
@CrossOrigin
public class AircraftController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<AircraftFullDto> getAll() {
        return worldBean.read(world -> world.aircrafts().all()
                .map(a -> AircraftFullDto.from(world, a))
                .toList());
    }
}
