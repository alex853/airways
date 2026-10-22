package net.simforge.airways2.app.user;

import net.simforge.airways2.app.beans.SimTrackerBean;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimTrackerControllerTest {
    @Test
    void posrepAnswersWithTheStatusBuiltAfterTheReportIsProcessed() {
        final SimTrackerBean simTrackerBean = mock(SimTrackerBean.class);
        final FlightDashboardController dashboard = mock(FlightDashboardController.class);
        final FlightDashboardController.StatusDto status = new FlightDashboardController.StatusDto(null, null, null, null, null, null);
        when(dashboard.getStatus(anyInt())).thenReturn(status);
        final SimTrackerController controller = new SimTrackerController();
        ReflectionTestUtils.setField(controller, "simTrackerBean", simTrackerBean);
        ReflectionTestUtils.setField(controller, "flightDashboardController", dashboard);

        final FlightDashboardController.StatusDto answer = controller.processPosrep(7, "1,2,3");

        assertSame(status, answer);
        final InOrder order = inOrder(simTrackerBean, dashboard);
        order.verify(simTrackerBean).processPosrep(7, "1,2,3");
        order.verify(dashboard).getStatus(7);
    }
}
