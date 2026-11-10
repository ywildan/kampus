package id.personal.campus

import android.app.Application
import android.content.Context
import androidx.room.Room
import id.personal.campus.data.CampusDatabase
import id.personal.campus.data.CampusRepository
import id.personal.campus.reminder.ScheduleCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CampusApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository: CampusRepository by lazy {
        CampusRepository(Room.databaseBuilder(this, CampusDatabase::class.java, "campus.db").build())
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.initialize()
            ScheduleCoordinator.refresh(this@CampusApplication)
        }
    }

    companion object {
        fun from(context: Context): CampusApplication = context.applicationContext as CampusApplication
    }
}
