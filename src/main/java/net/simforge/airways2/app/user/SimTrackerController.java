package net.simforge.airways2.app.user;

import net.simforge.airways2.app.beans.SimTrackerBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sim")
@CrossOrigin
public class SimTrackerController {
    @Autowired
    private SimTrackerBean simTrackerBean;

    @PostMapping("/posrep")
    public void processPosrep(@RequestAttribute("userId") int userId,
                              @RequestParam(name = "posrep", required = false) String posrep) {
        simTrackerBean.processPosrep(userId, posrep);
    }
}
