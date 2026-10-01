package com.strobingn.wildlifefieldops.ui.viewmodel

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.VisitDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.PreferredContact
import com.strobingn.wildlifefieldops.data.model.Visit
import com.strobingn.wildlifefieldops.data.remote.GeoPoint
import com.strobingn.wildlifefieldops.data.remote.LiveSyncPayloads
import com.strobingn.wildlifefieldops.data.workspace.AddressGeocoder
import com.strobingn.wildlifefieldops.data.workspace.JobCustomerWorkspace
import com.strobingn.wildlifefieldops.data.workspace.JobSaveRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Combined Job screen view-model logic lives in [JobCustomerWorkspace]
 * so JVM tests can cover create / link / edit without Robolectric.
 */
class JobWorkspaceViewModelTest {

    private val jobs = FakeJobDao()
    private val customers = FakeCustomerDao()
    private val visits = FakeVisitDao()
    private val workspace = JobCustomerWorkspace(
        jobDao = jobs,
        customerDao = customers,
        visitDao = visits,
        geocoder = AddressGeocoder { GeoPoint(41.4459, -74.4207) }
    )

    @Test
    fun emptyCustomerSectionDoesNotInsertCustomer() = runBlocking {
        val result = workspace.save(newJobRequest(JobCustomerDraft()))
        assertNull(result.customer)
        assertTrue(result.job.customerId.isEmpty())
        assertTrue(customers.rows.isEmpty())
        assertFalse(result.job.isSynced)
    }

    @Test
    fun creatingJobInsertsCustomerAndMarksBothUnsynced() = runBlocking {
        val result = workspace.save(
            newJobRequest(
                JobCustomerDraft(
                    name = "Pat Rivera",
                    phone = "8455550100",
                    email = "pat@example.com",
                    address = "12 Oak St",
                    city = "Middletown",
                    state = "NY",
                    zipCode = "10940",
                    notes = "Gate code 1234",
                    preferredContact = PreferredContact.TEXT
                )
            )
        )
        val customer = result.customer
        assertNotNull(customer)
        assertEquals("Pat", customer!!.firstName)
        assertEquals("Rivera", customer.lastName)
        assertEquals("8455550100", customer.phone)
        assertEquals("Text", customer.billingContact)
        assertFalse(customer.isSynced)
        assertFalse(result.job.isSynced)
        assertEquals(customer.id, result.job.customerId)
        assertEquals("Pat Rivera", result.job.customerName)
        assertEquals("12 Oak St, Middletown, NY 10940", result.job.address)
        assertEquals(41.4459, result.job.latitude!!, 1e-4)
        assertEquals(41.4459, customer.latitude!!, 1e-4)

        val payload = LiveSyncPayloads.customer(customer)
        assertEquals("Pat Rivera", payload.name)
        assertEquals("8455550100", payload.phone)
        assertEquals("Middletown", payload.town)
        assertEquals("NY", payload.state)
        assertEquals("10940", payload.zip)
        assertEquals("Gate code 1234", payload.notes)
    }

    @Test
    fun editingCustomerFromJobUpdatesSharedRecordAndSiblingJobs() = runBlocking {
        val first = workspace.save(
            newJobRequest(
                JobCustomerDraft(
                    name = "Sam Lee",
                    phone = "5551111",
                    address = "1 Main St",
                    city = "Cornwall",
                    state = "NY"
                ),
                title = "Attic raccoon"
            )
        )
        val customerId = first.customer!!.id
        val sibling = workspace.save(
            newJobRequest(
                JobCustomerDraft(customerId = customerId, name = "Sam Lee", address = "1 Main St", city = "Cornwall", state = "NY"),
                title = "Follow-up bait"
            )
        )
        assertEquals(customerId, sibling.job.customerId)

        val updated = workspace.saveCustomerOnJob(
            sibling.job,
            JobCustomerDraft.fromCustomer(first.customer!!).copy(
                name = "Samantha Lee",
                phone = "8455559999",
                address = "88 Willow Ave",
                city = "Cornwall",
                state = "NY",
                notes = "Side gate"
            )
        )
        val shared = customers.rows.getValue(customerId)
        assertEquals("Samantha", shared.firstName)
        assertEquals("Lee", shared.lastName)
        assertEquals("8455559999", shared.phone)
        assertEquals("Side gate", shared.notes)
        assertFalse(shared.isSynced)

        val otherJob = jobs.rows.getValue(first.job.id)
        assertEquals("Samantha Lee", otherJob.customerName)
        assertTrue(otherJob.address.contains("88 Willow Ave"))
        assertFalse(otherJob.isSynced)
        assertEquals(customerId, updated.job.customerId)
    }

    @Test
    fun pickingExistingCustomerLinksWithoutCreatingASecondRow() = runBlocking {
        val existing = Customer(firstName = "Alex", lastName = "Ng", phone = "111", address = "9 Pine")
        customers.insert(existing)
        val result = workspace.save(
            newJobRequest(
                JobCustomerDraft.fromCustomer(existing).copy(phone = "222")
            )
        )
        assertEquals(1, customers.rows.size)
        assertEquals(existing.id, result.job.customerId)
        assertEquals("222", customers.rows.getValue(existing.id).phone)
        assertEquals("Alex Ng", result.job.customerName)
    }

    @Test
    fun jobWithoutLinkedCustomerShowsEmptyDraftReadyToFill() = runBlocking {
        val job = Job(title = "Open job", customerId = "", customerName = "", address = "")
        jobs.insert(job)
        val draft = workspace.loadDraft(job)
        assertEquals("", draft.customerId)
        assertEquals("", draft.name)
        assertEquals("", draft.phone)
        assertFalse(draft.hasAnyInfo())
    }

    @Test
    fun denormalizedJobFillsDraftUntilCustomerIsCreated() = runBlocking {
        val job = Job(title = "Legacy", customerName = "Kim Cole", address = "5 Brook St")
        val draft = workspace.loadDraft(job)
        assertEquals("Kim Cole", draft.name)
        assertEquals("5 Brook St", draft.address)
        assertTrue(draft.hasAnyInfo())
        assertFalse(draft.isLinked())
    }

    @Test
    fun searchMatchesNamePhoneEmailAndAddress() = runBlocking {
        customers.insert(Customer(firstName = "Jordan", lastName = "Pike", phone = "8451112222", email = "jp@x.com", address = "77 Gate Rd"))
        assertEquals(1, workspace.searchCustomers("Jordan").size)
        assertEquals(1, workspace.searchCustomers("845111").size)
        assertEquals(1, workspace.searchCustomers("jp@x").size)
        assertEquals(1, workspace.searchCustomers("Gate Rd").size)
        assertTrue(workspace.searchCustomers("zzz").isEmpty())
        assertTrue(workspace.searchCustomers("  ").isEmpty())
    }

    @Test
    fun typedValuesWinOverPreviousCustomerFields() = runBlocking {
        val created = workspace.save(
            newJobRequest(JobCustomerDraft(name = "Old Name", email = "old@x.com", phone = "1"))
        )
        val overwritten = workspace.save(
            newJobRequest(
                JobCustomerDraft.fromCustomer(created.customer!!).copy(
                    name = "New Name",
                    email = "new@x.com",
                    phone = "9"
                ),
                existing = created.job,
                title = created.job.title
            )
        )
        val row = customers.rows.getValue(created.customer!!.id)
        assertEquals("New", row.firstName)
        assertEquals("Name", row.lastName)
        assertEquals("new@x.com", row.email)
        assertEquals("9", row.phone)
        assertEquals("New Name", overwritten.job.customerName)
    }

    @Test
    fun parsePersonNameSplitsLastWordAsLastName() {
        assertEquals("Pat" to "Rivera", JobCustomerDraft.parsePersonName("Pat Rivera"))
        assertEquals("Mary Anne" to "Cole", JobCustomerDraft.parsePersonName("Mary Anne Cole"))
        assertEquals("Kim" to "", JobCustomerDraft.parsePersonName("Kim"))
        assertEquals("" to "", JobCustomerDraft.parsePersonName("  "))
    }

    private fun newJobRequest(
        customer: JobCustomerDraft,
        title: String = "Raccoon job",
        existing: Job? = null
    ) = JobSaveRequest(
        existingJob = existing,
        title = title,
        description = "Attic",
        type = "Raccoon",
        priority = JobPriority.MEDIUM,
        estimatedValue = 450.0,
        notes = "Job notes",
        appointmentTimes = listOf(1_700_000_000_000L),
        customer = customer
    )
}

private class FakeJobDao : JobDao {
    val rows = LinkedHashMap<String, Job>()

    override fun getAll(): Flow<List<Job>> = flow { emit(rows.values.toList()) }
    override suspend fun getAllOnce(): List<Job> = rows.values.toList()
    override fun getByStatus(status: JobStatus): Flow<List<Job>> =
        flow { emit(rows.values.filter { it.status == status }) }
    override suspend fun getById(id: String): Job? = rows[id]
    override fun observeById(id: String): Flow<Job?> = flow { emit(rows[id]) }
    override suspend fun countByServiceType(serviceType: String): Int =
        rows.values.count { it.type == serviceType }
    override suspend fun reassignServiceType(oldType: String, newType: String, updatedAt: Long): Int {
        var n = 0
        rows.values.toList().forEach { job ->
            if (job.type == oldType) {
                rows[job.id] = job.copy(type = newType, updatedAt = updatedAt, isSynced = false)
                n++
            }
        }
        return n
    }
    override fun getByCustomer(customerId: String): Flow<List<Job>> =
        flow { emit(rows.values.filter { it.customerId == customerId }) }
    override suspend fun getByCustomerOnce(customerId: String): List<Job> =
        rows.values.filter { it.customerId == customerId }
    override suspend fun getUnsynced(): List<Job> = rows.values.filter { !it.isSynced }
    override suspend fun countUnsynced(): Int = getUnsynced().size
    override fun search(query: String): Flow<List<Job>> = flow {
        emit(rows.values.filter { it.title.contains(query, true) })
    }
    override suspend fun insert(job: Job) { rows[job.id] = job }
    override suspend fun insertAll(jobs: List<Job>) { jobs.forEach { insert(it) } }
    override suspend fun update(job: Job) { rows[job.id] = job }
    override suspend fun delete(job: Job) { rows.remove(job.id) }
    override suspend fun deleteById(id: String) { rows.remove(id) }
    override suspend fun count(): Int = rows.size
    override suspend fun countByStatus(status: JobStatus): Int = rows.values.count { it.status == status }
    override suspend fun markSynced(id: String) {
        rows[id]?.let { rows[id] = it.copy(isSynced = true, syncError = null) }
    }
    override suspend fun markSyncError(id: String, error: String?) {
        rows[id]?.let { rows[id] = it.copy(syncError = error) }
    }
    override suspend fun deleteAll() { rows.clear() }
    override suspend fun updateCounty(id: String, county: String?, state: String?, updatedAt: Long) {
        rows[id]?.let { rows[id] = it.copy(county = county, state = state, updatedAt = updatedAt, isSynced = false) }
    }
}

private class FakeCustomerDao : CustomerDao {
    val rows = LinkedHashMap<String, Customer>()

    override fun getAll(): Flow<List<Customer>> = flow { emit(rows.values.filter { it.isActive }) }
    override suspend fun getAllOnce(): List<Customer> = rows.values.toList()
    override suspend fun getById(id: String): Customer? = rows[id]
    override suspend fun getUnsynced(): List<Customer> = rows.values.filter { !it.isSynced }
    override suspend fun countUnsynced(): Int = getUnsynced().size
    override fun search(query: String): Flow<List<Customer>> = flow { emit(searchOnce(query)) }
    override suspend fun searchOnce(query: String): List<Customer> {
        val q = query.lowercase()
        return rows.values.filter { customer ->
            customer.isActive && listOf(
                customer.firstName, customer.lastName, customer.companyName,
                customer.phone, customer.email, customer.address
            ).any { it.contains(q, ignoreCase = true) }
        }.take(12)
    }
    override fun observeById(id: String): Flow<Customer?> = flow { emit(rows[id]) }
    override suspend fun insert(customer: Customer) { rows[customer.id] = customer }
    override suspend fun insertAll(customers: List<Customer>) { customers.forEach { insert(it) } }
    override suspend fun update(customer: Customer) { rows[customer.id] = customer }
    override suspend fun delete(customer: Customer) { rows.remove(customer.id) }
    override suspend fun count(): Int = rows.values.count { it.isActive }
    override suspend fun markSynced(id: String) {
        rows[id]?.let { rows[id] = it.copy(isSynced = true, syncError = null) }
    }
    override suspend fun markSyncError(id: String, error: String?) {
        rows[id]?.let { rows[id] = it.copy(syncError = error) }
    }
    override suspend fun deleteAll() { rows.clear() }
}

private class FakeVisitDao : VisitDao {
    val rows = LinkedHashMap<String, Visit>()
    override fun getAll(): Flow<List<Visit>> = flow { emit(rows.values.toList()) }
    override fun getByJob(jobId: String): Flow<List<Visit>> =
        flow { emit(rows.values.filter { it.jobId == jobId }) }
    override suspend fun getByJobOnce(jobId: String): List<Visit> =
        rows.values.filter { it.jobId == jobId }
    override suspend fun deletePendingForJob(jobId: String) {
        rows.values.filter { it.jobId == jobId && !it.isCompleted }.forEach { rows.remove(it.id) }
    }
    override suspend fun insert(visit: Visit) { rows[visit.id] = visit }
    override suspend fun update(visit: Visit) { rows[visit.id] = visit }
    override suspend fun delete(visit: Visit) { rows.remove(visit.id) }
}
