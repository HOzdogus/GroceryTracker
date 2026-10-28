package uk.hozdogus.grocerytracker.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("Europe/London")

    @Test fun beforeEightRunsThisMorning() =
        assertEquals(Duration.ofHours(2), CheckWorker.untilNextCheck(ZonedDateTime.of(2026, 10, 28, 6, 0, 0, 0, zone)))

    @Test fun afterEightRunsTomorrow() =
        assertEquals(Duration.ofHours(23), CheckWorker.untilNextCheck(ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone)))
}
