package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.EventsToProcess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/events-to-process")
@CrossOrigin
public class EventsToProcessController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public ResponseEntity<List<EventDto>> getAll() {
        final World world = worldBean.world();
        final Collection<EventsToProcess.Event> aircraft = world.eventsToProcess().all();
        return ResponseEntity.ok(aircraft.stream()
                .map(e -> new EventDto(
                        e.getId(),
                        e.getStatusRaw() + " - " + e.getStatus(),
                        e.getTypeRaw() + " - " + e.getType(),
                        e.getObjectId(),
                        WebTime.ts(e.getTime())))
                .toList());
    }

    @Data
    @AllArgsConstructor
    private static class EventDto {
        private int id;
        private String status;
        private String type;
        private int objectId;
        private String time;
    }
}
