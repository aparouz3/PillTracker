package com.pilltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY name COLLATE NOCASE")
    fun getAllFoods(): Flow<List<Food>>

    @Query("SELECT * FROM foods ORDER BY name COLLATE NOCASE")
    suspend fun getAllFoodsOnce(): List<Food>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: Food): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<Food>)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM foods ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomFoodOnce(): Food?
}