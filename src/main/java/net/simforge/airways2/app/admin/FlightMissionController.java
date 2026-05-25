package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/flight-mission")
@CrossOrigin
public class FlightMissionController {
    @Autowired
    private WorldRunnerBean worldBean;

    // nothing for now
}
