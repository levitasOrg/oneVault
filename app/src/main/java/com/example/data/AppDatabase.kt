package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.crypto.SecureStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [VaultItem::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Load the SQLCipher native libraries before opening the database.
                System.loadLibrary("sqlcipher")

                // The passphrase is generated once and stored Keystore-encrypted, so the
                // on-disk database file is fully encrypted and unreadable without the device key.
                val passphrase = SecureStore(context.applicationContext).getOrCreateDbPassphrase()
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "onevault_database"
                )
                    .openHelperFactory(factory)
                    // Note: destructive migration was intentionally removed. Bumping the schema
                    // version now requires a real Migration so user vault data is never silently wiped.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
