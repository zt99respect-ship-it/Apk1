package com.livetranslator.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [TranslationHistoryEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class LiveTranslatorDatabase : RoomDatabase() {
  abstract fun translationHistoryDao(): TranslationHistoryDao

  companion object {
    @Volatile private var instance: LiveTranslatorDatabase? = null

    fun getInstance(context: Context): LiveTranslatorDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                LiveTranslatorDatabase::class.java,
                "live_translator.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = false)
              .build()
              .also { instance = it }
        }
  }
}
