package com.tanuki276.ativs;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class UsageAccumulatorTest {
    @Test public void clipsSessionAtBothWindowBoundaries() {
        UsageAccumulator accumulator = new UsageAccumulator(10000, 20000);
        accumulator.resume("app", 5000);
        accumulator.pause("app", 25000);
        UsageAccumulator.Record result = accumulator.finish().get("app");
        assertEquals(10000, result.timeMs);
        assertEquals(0, result.launches);
        assertEquals(20000, result.lastUsed);
    }

    @Test public void closesOpenSessionAtQueryEnd() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.resume("app", 5000);
        assertEquals(15000, accumulator.finish().get("app").timeMs);
    }

    @Test public void excludesEventsOutsideWindow() {
        UsageAccumulator accumulator = new UsageAccumulator(10000, 20000);
        accumulator.resume("old", 1000);
        accumulator.pause("old", 2000);
        accumulator.resume("future", 20000);
        assertTrue(accumulator.finish().isEmpty());
    }

    @Test public void duplicateResumeDoesNotDuplicateTimeOrLaunches() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.resume("app", 1000);
        accumulator.resume("app", 2000);
        accumulator.pause("app", 5000);
        UsageAccumulator.Record result = accumulator.finish().get("app");
        assertEquals(4000, result.timeMs);
        assertEquals(1, result.launches);
    }

    @Test public void activityTransitionWithinPackageIsNotAnotherLaunch() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.resume("app", 1000);
        accumulator.pause("app", 5000);
        accumulator.resume("app", 5100);
        accumulator.pause("app", 8000);
        UsageAccumulator.Record result = accumulator.finish().get("app");
        assertEquals(6900, result.timeMs);
        assertEquals(1, result.launches);
    }

    @Test public void realReopenCountsAsAnotherLaunch() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.resume("app", 1000);
        accumulator.pause("app", 5000);
        accumulator.resume("app", 10000);
        accumulator.pause("app", 15000);
        assertEquals(2, accumulator.finish().get("app").launches);
    }

    @Test public void rapidSwitchBackCountsAsAnotherLaunch() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.resume("a", 1000);
        accumulator.pause("a", 5000);
        accumulator.resume("b", 5100);
        accumulator.pause("b", 5200);
        accumulator.resume("a", 5300);
        assertEquals(2, accumulator.finish().get("a").launches);
    }

    @Test public void appSwitchClosesMissingPauseWithoutOverlappingTime() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.resume("a", 1000);
        accumulator.resume("b", 5000);
        accumulator.pause("a", 6000); // Late pause must not close b.
        accumulator.pause("b", 10000);
        Map<String, UsageAccumulator.Record> result = accumulator.finish();
        assertEquals(4000, result.get("a").timeMs);
        assertEquals(5000, result.get("b").timeMs);
    }

    @Test public void screenLockOrShutdownEndsSessionAndLaunchDebounce() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.resume("app", 1000);
        accumulator.interrupt(5000);
        accumulator.resume("app", 5100);
        accumulator.interrupt(6000);
        UsageAccumulator.Record result = accumulator.finish().get("app");
        assertEquals(4900, result.timeMs);
        assertEquals(2, result.launches);
    }

    @Test public void warmupDoesNotAwardLaunchInsideWindow() {
        UsageAccumulator accumulator = new UsageAccumulator(10000, 20000);
        accumulator.resume("app", 5000);
        accumulator.pause("app", 10050);
        accumulator.resume("app", 10100);
        accumulator.pause("app", 15000);
        UsageAccumulator.Record result = accumulator.finish().get("app");
        assertEquals(4950, result.timeMs);
        assertEquals(0, result.launches);
    }

    @Test public void missingResumeIsNotInvented() {
        UsageAccumulator accumulator = new UsageAccumulator(0, 20000);
        accumulator.pause("app", 5000);
        assertTrue(accumulator.finish().isEmpty());
    }

    @Test public void emptyHistoryProducesNoRecords() {
        assertTrue(new UsageAccumulator(0, 20000).finish().isEmpty());
    }
}
