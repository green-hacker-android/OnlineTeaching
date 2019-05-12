package com.codingblocks.cbonlineapp.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import com.codingblocks.cbonlineapp.database.models.Course

@Dao
abstract class CourseDao : BaseDao<Course> {

    @Query("SElECT * FROM Course ")
    abstract fun getCourses(): LiveData<List<Course>>

    @Query("SElECT * FROM Course where uid = :courseId ")
    abstract fun getCourse(courseId: String): Course

    @Query("SElECT * FROM Course where uid = :courseId ")
    abstract fun getMyCourse(courseId: String): LiveData<Course>

    @Query("SElECT * FROM Course where uid != " + "'" + "'")
    abstract fun getMyCourses(): LiveData<List<Course>>
}
