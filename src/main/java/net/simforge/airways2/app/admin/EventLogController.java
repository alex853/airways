package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.datamodel.EventLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/event-log")
@CrossOrigin
public class EventLogController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<EventDto> getAll() {
        return worldBean.read(world -> world.eventLog().all()
                .map(EventLogController::toDto)
                .toList());
    }

    @GetMapping("/object")
    public List<EventDto> getObject(@RequestParam(name = "type") final int type, @RequestParam(name = "id") final int id) {
        return worldBean.read(world -> world.eventLog()
                .filter(e -> (e.getObject1Id() == id && e.getObject1TypeRaw() == type)
                        || (e.getObject2Id() == id && e.getObject2TypeRaw() == type)
                        || (e.getObject3Id() == id && e.getObject3TypeRaw() == type)
                        || (e.getObject4Id() == id && e.getObject4TypeRaw() == type)).stream()
                .map(EventLogController::toDto)
                .toList());
    }

    private static EventDto toDto(final EventLog.Event e) {
        return new EventDto(
                e.getId(),
                TimeTools.ts(e.getTime()),
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
