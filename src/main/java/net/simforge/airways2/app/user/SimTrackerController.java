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
    @Autowired
    private FlightDashboardController flightDashboardController;

    /**
     * Takes the report of the sim and answers with the same status as GET /flight-dashboard/status, built after the report is processed, so a client which
     * sends reports does not need to ask the status separately.
     */
    @PostMapping("/posrep")
    public FlightDashboardController.StatusDto processPosrep(@RequestAttribute("userId") int userId,
                                                              @RequestParam(name = "posrep", required = false) String posrep) {
        simTrackerBean.processPosrep(userId, posrep);
        return flightDashboardController.getStatus(userId);
    }
}
