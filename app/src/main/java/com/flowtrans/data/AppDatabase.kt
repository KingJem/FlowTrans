package com.flowtrans.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun protocolToString(p: Protocol): String = p.name
    @TypeConverter fun stringToProtocol(s: String): Protocol = Protocol.valueOf(s)
}

@Database(entities = [ProxyProfile::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "flowtrans.db",
            ).build().also { instance = it }
        }
    }
}
