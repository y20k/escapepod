package org.y20k.escapepod.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = arrayOf(PodcastEntity::class, EpisodeEntity::class), version = 1)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao


    companion object {

        @Volatile
        private var INSTANCE: CollectionDatabase? = null

        fun getInstance(context: Context): CollectionDatabase {
            synchronized(this) {
                var instance: CollectionDatabase? = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.applicationContext,
                            CollectionDatabase::class.java,
                            "collection_database"
                    )
                            .fallbackToDestructiveMigration()
                            .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }

}
