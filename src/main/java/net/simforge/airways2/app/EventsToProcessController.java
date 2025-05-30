package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events-to-process")
@CrossOrigin
public class EventsToProcessController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<EventDto> getAll() {
        return worldBean.read(world -> world.eventsToProcess().all().stream()
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
