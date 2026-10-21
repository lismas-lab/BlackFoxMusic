package com.blackfox.music.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ArtistEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN isUserUploaded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN favoriteOrderPosition INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `followed_artists` (
                        `channelId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL DEFAULT '',
                        `subscribers` TEXT NOT NULL DEFAULT '',
                        `followedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`channelId`)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "black_fox_music.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Автоматически создаем системный плейлист "Избранное"
                        CoroutineScope(Dispatchers.IO).launch {
                            getInstance(context).musicDao().insertPlaylist(
                                PlaylistEntity(
                                    name = "Избранное",
                                    isFavoriteSystem = true
                                )
                            )
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
