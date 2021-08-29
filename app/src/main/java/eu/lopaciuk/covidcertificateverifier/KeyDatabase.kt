package eu.lopaciuk.covidcertificateverifier

import android.content.Context
import androidx.room.*

@Database(entities = [Key::class], version = 1)
abstract class KeyDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
}

fun getKeyDatabase(applicationContext: Context): KeyDatabase {
    return Room.databaseBuilder(
        applicationContext,
        KeyDatabase::class.java,
        "key-database"
    ).build()
}

@Entity(tableName = "keys")
data class Key(
    @PrimaryKey @ColumnInfo(name = "kid") val kid: ByteArray,
    @ColumnInfo(name = "key") val publicKey: ByteArray
)


@Dao
interface KeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(key: Key)

    @Delete
    fun delete(key: Key)

    @Query("SELECT * FROM keys")
    fun getAllKeys(): Array<Key>

    @Query("SELECT * FROM keys WHERE kid = :kid")
    fun getKeyByKid(kid: ByteArray): Key?

    @Query("DELETE FROM keys")
    fun deleteAll()
}