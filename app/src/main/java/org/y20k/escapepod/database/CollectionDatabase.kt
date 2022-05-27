/*
 * CollectionDatabase.kt
 * Implements the CollectionDatabase class
 * A CollectionDatabase class stores podcasts, episodes and the playback queue in a Room database
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import org.y20k.escapepod.database.daos.EpisodeDao
import org.y20k.escapepod.database.daos.EpisodeDescriptionDao
import org.y20k.escapepod.database.daos.PodcastDao
import org.y20k.escapepod.database.daos.PodcastDescriptionDao
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.objects.PodcastDescription
import org.y20k.escapepod.database.wrappers.EpisodeMostRecentView


/*
 * CollectionDatabase class
 */
@Database(version = 2, entities = [Podcast::class, PodcastDescription::class, Episode::class, EpisodeDescription::class], views = [EpisodeMostRecentView::class], autoMigrations = [AutoMigration (from = 1, to = 2, spec = CollectionDatabase.CustomMigrationSpec::class)], exportSchema = true)
@TypeConverters(Converters::class)
abstract class CollectionDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao

    abstract fun podcastDescriptionDao(): PodcastDescriptionDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun episodeDescriptionDao(): EpisodeDescriptionDao

    /* Specifies the database changes from version 1 to version 2 */
    @RenameColumn(fromColumnName = "playback_state", toColumnName = "is_playing", tableName = "episodes")
    class CustomMigrationSpec: AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            super.onPostMigrate(db)
            db.execSQL("UPDATE episodes SET is_playing = 0")
        }
    }

    /* Object used to create an offer an instance of the collection database */
    companion object {

        var INSTANCE: CollectionDatabase? = null

        fun getInstance(context: Context): CollectionDatabase {
            synchronized(this) {
                var instance: CollectionDatabase? = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(context.applicationContext, CollectionDatabase::class.java, "collection_database").apply {
                        fallbackToDestructiveMigration()
                    }.build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }

}
