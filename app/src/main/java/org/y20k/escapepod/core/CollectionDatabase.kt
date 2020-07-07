package org.y20k.escapepod.core

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = arrayOf(PodcastEntity::class), version = 1)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
