package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.EventLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/event-log")
@CrossOrigin
public class EventLogController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public ResponseEntity<List<EventDto>> getAll() {
        final World world = worldBean.world();
        final Collection<EventLog.Event> logs = world.eventLog().all();
        return ResponseEntity.ok(logs.stream()
                .map(e -> new EventDto(
                        e.getId(),
                        WebTime.ts(e.getTime()),
                        e.getTypeRaw() + " - " + e.getType(),
                        e.getObject1Type() + " - " + e.getObject1Id(),
                        e.getObject2Type() + " - " + e.getObject2Id(),
                        e.getObject3Type() + " - " + e.getObject3Id(),
                        e.getObject4Type() + " - " + e.getObject4Id()))
                .toList());
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
