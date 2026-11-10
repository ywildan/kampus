package id.personal.campus.data

object InitialSchedule {
    val courses: List<CourseEntity> = listOf(
        CourseEntity(name = "Pengantar Bisnis", weekday = 1,
            startMinute = 15 * 60 + 10, endMinute = 17 * 60 + 40, room = "A.3b.4"),
        CourseEntity(name = "Bahasa Inggris", weekday = 2,
            startMinute = 10 * 60, endMinute = 11 * 60 + 40, room = "A.4b.7",
            section = "02", roomNote = "Lab. Komputer"),
        CourseEntity(name = "Budaya dan Karakter Tidar", weekday = 2,
            startMinute = 13 * 60 + 40, endMinute = 15 * 60 + 20, room = "A.3b.1"),
        CourseEntity(name = "Ketentuan Umum Perpajakan", weekday = 3,
            startMinute = 7 * 60, endMinute = 8 * 60 + 40, room = "A.3b.5"),
        CourseEntity(name = "Bahasa Indonesia", weekday = 3,
            startMinute = 10 * 60 + 40, endMinute = 12 * 60 + 20, room = "A.3b.2"),
        CourseEntity(name = "Pengantar Akuntansi", weekday = 3,
            startMinute = 12 * 60 + 30, endMinute = 15 * 60, room = "A.3b.6"),
        CourseEntity(name = "Matematika Bisnis", weekday = 4,
            startMinute = 7 * 60, endMinute = 9 * 60 + 30, room = "A.3b.2"),
        CourseEntity(name = "Pengantar Teori Ekonomi", weekday = 4,
            startMinute = 9 * 60 + 40, endMinute = 12 * 60 + 10, room = "A.3b.1"),
    )
}
