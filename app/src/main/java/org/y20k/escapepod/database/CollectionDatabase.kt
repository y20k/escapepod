/*
 * CollectionDatabase.kt
 * Implements the CollectionDatabase class
 * A CollectionDatabase class stores podcasts, episodes and the playback queue in a Room database
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
@Database(entities = [Podcast::class, PodcastDescription::class, Episode::class, EpisodeDescription::class], views = [EpisodeMostRecentView::class], version = 1)
@TypeConverters(Converters::class)
abstract class CollectionDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao

    abstract fun podcastDescriptionDao(): PodcastDescriptionDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun episodeDescriptionDao(): EpisodeDescriptionDao


    /* Object used to create an offer an instance of the collection database */
    companion object {

        var INSTANCE: CollectionDatabase? = null

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
