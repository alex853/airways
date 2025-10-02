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
@RequestMapping("/event-log")
@CrossOrigin
public class EventLogController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<EventDto> getAll() {
        return worldBean.read(world -> world.eventLog().all().stream()
                .map(e -> toDto(e))
                .toList());
    }

    @GetMapping("/object")
    public List<EventDto> getObject(@RequestParam(name = "type") final int type, @RequestParam(name = "id") final int id) {
        return worldBean.read(world -> world.eventLog()
                .filter(e -> e.getObject1Id() == id
                       || e.getObject2Id() == id
                       || e.getObject3Id() == id
                       || e.getObject4Id() == id)
                .map(e -> toDto(e))
                .toList());
    }

    private static EventDto toDto(final EventLog.Event e) {
        return new EventDto(
                        e.getId(),
                        WebTime.ts(e.getTime()),
                        e.getTypeRaw() + " - " + e.getType(),
                        e.getObject1Type() + " - " + e.getObject1Id(),
                        e.getObject2Type() + " - " + e.getObject2Id(),
                        e.getObject3Type() + " - " + e.getObject3Id(),
                        e.getObject4Type() + " - " + e.getObject4Id());
    }

    @Data
    @AllArgsConstructor
    private static class EventDto {
        private int id;
        private String time;
        private String type;
        private String object1;
        private String object2;
        private String object3;
        private String object4;
    }
}
