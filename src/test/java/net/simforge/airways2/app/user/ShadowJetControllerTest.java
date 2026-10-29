package net.simforge.airways2.app.user;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShadowJetControllerTest {
    private World world;
    private ShadowJetController controller;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        final WorldRunnerBean worldBean = mock(WorldRunnerBean.class);
        when(worldBean.read(any())).thenAnswer(invocation -> ((WorldAccess.Action<?>) invocation.getArgument(0)).invoke(world));

        controller = new ShadowJetController();
        ReflectionTestUtils.setField(controller, "worldBean", worldBean);
    }

    @Test
    public void aircraftTypesAreTheTypesWithPerformanceDataSortedByIcao() {
        world.aircraftTypes().create("B738", "738");
        world.aircraftTypes().create("ZZZZ", null); // no performance data: the time of the arrival cannot be calculated
        world.aircraftTypes().create("A320", "320");

        final List<ShadowJetController.AircraftTypeDto> types = controller.getAircraftTypes();

        assertEquals(List.of("A320", "B738"), types.stream().map(ShadowJetController.AircraftTypeDto::getIcao).toList());
    }

    @Test
    public void noTypesNoList() {
        assertEquals(0, controller.getAircraftTypes().size());
    }
}
