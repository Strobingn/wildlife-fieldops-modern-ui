package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.data.local.DeletedRecordDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobRepository @Inject constructor(
    private val jobDao: JobDao,
    private val deletedRecordDao: DeletedRecordDao
) {
    fun getAllJobs(): Flow<List<Job>> = jobDao.getAll()

    fun getJobsByStatus(status: JobStatus): Flow<List<Job>> = jobDao.getByStatus(status)

    suspend fun getJobById(id: String): Job? = jobDao.getById(id)

    suspend fun saveJob(job: Job) = jobDao.insert(job)

    suspend fun updateJob(job: Job) = jobDao.update(job)

    suspend fun deleteJob(job: Job) {
        deletedRecordDao.insert(
            DeletedRecord(
                id = job.id,
                entityType = DeletedRecord.TYPE_JOB,
                synced = false
            )
        )
        jobDao.delete(job)
    }

    fun searchJobs(query: String): Flow<List<Job>> = jobDao.search(query)

    suspend fun getUnsyncedJobs(): List<Job> = jobDao.getUnsynced()

    suspend fun updateJobCounty(id: String, county: String?, state: String?) =
        jobDao.updateCounty(id, county, state)
}
