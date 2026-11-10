package id.personal.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialScheduleTest {
    @Test fun allEightSlotsMatchReferenceSchedule() {
        val expected = listOf(
            listOf("1", "910", "1060", "A.3b.4", "01"),
            listOf("2", "600", "700", "A.4b.7", "02"),
            listOf("2", "820", "920", "A.3b.1", "01"),
            listOf("3", "420", "520", "A.3b.5", "01"),
            listOf("3", "640", "740", "A.3b.2", "01"),
            listOf("3", "750", "900", "A.3b.6", "01"),
            listOf("4", "420", "570", "A.3b.2", "01"),
            listOf("4", "580", "730", "A.3b.1", "01"),
        )
        assertEquals(expected, InitialSchedule.courses.map {
            listOf(it.weekday.toString(), it.startMinute.toString(), it.endMinute.toString(), it.room, it.section)
        })
        assertEquals("Lab. Komputer", InitialSchedule.courses[1].roomNote)
        assertTrue(InitialSchedule.courses.all { it.enabled && it.startMinute < it.endMinute })
    }

    @Test fun seedDoesNotInventSemesterDatesOrEnableReminders() {
        val initial = SettingsEntity()
        assertEquals("Gasal 2026/2027", initial.semesterName)
        assertEquals("Asia/Jakarta", initial.zoneId)
        assertTrue(!initial.semesterConfirmed && !initial.remindersEnabled)
    }
}
