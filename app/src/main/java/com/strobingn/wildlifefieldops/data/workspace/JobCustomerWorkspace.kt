package com.strobingn.wildlifefieldops.data.workspace

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.VisitDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.DefaultServiceTypes
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.Visit
import com.strobingn.wildlifefieldops.data.remote.GeoPoint
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import java.util.UUID
import javax.inject.Inject

fun interface AddressGeocoder {
    suspend fun geocode(address: String): GeoPoint?
}

data class JobSaveRequest(
    val existingJob: Job?,
    val title: String,
    val description: String,
    val type: String,
    val priority: JobPriority,
    val estimatedValue: Double,
    val notes: String,
    val appointmentTimes: List<Long>,
    val actualCost: Double? = null,
    val customer: JobCustomerDraft
)

data class JobSaveResult(
    val job: Job,
    val customer: Customer?
)

/**
 * Shared save path for the unified Job screen. Room writes set
 * `isSynced = false` and bump `updatedAt` so AutoSync pushes `jobs` and
 * `customers` with the current live DTOs.
 */
class JobCustomerWorkspace @Inject constructor(
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val visitDao: VisitDao,
    private val geocoder: AddressGeocoder
) {
    private suspend fun geocode(address: String): GeoPoint? = geocoder.geocode(address)

    suspend fun loadDraft(job: Job?): JobCustomerDraft {
        if (job == null) return JobCustomerDraft()
        val linked = job.customerId.takeIf { it.isNotBlank() }?.let { customerDao.getById(it) }
        if (linked != null) return JobCustomerDraft.fromCustomer(linked)
        if (job.customerName.isBlank() && job.address.isBlank() && job.customerId.isBlank()) {
            return JobCustomerDraft()
        }
        return JobCustomerDraft.fromJobDenormalized(job)
    }

    suspend fun searchCustomers(query: String): List<Customer> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return customerDao.searchOnce(q)
    }

    suspend fun save(request: JobSaveRequest): JobSaveResult {
        val now = System.currentTimeMillis()
        val linked = upsertCustomer(request.customer, now)
        val base = request.existingJob ?: Job()
        val nextPricing = PricingCalculator.withTypedJobTotal(
            base.pricing,
            request.estimatedValue.takeIf { it > 0.0 }
        )
        val quote = PricingCalculator.compute(nextPricing)
        val serviceAddress = linked?.let { composeStoredAddress(it) }
            ?: request.customer.composedServiceAddress()
        val displayName = linked?.fullName?.trim().orEmpty()
            .ifBlank { request.customer.displayName() }
        val addressChanged = serviceAddress != base.address
        var job = base.copy(
            title = request.title,
            description = request.description,
            customerId = linked?.id.orEmpty(),
            customerName = displayName,
            address = serviceAddress,
            type = DefaultServiceTypes.display(request.type),
            priority = request.priority,
            estimatedValue = quote.total.effective,
            actualCost = request.actualCost ?: base.actualCost,
            notes = request.notes,
            scheduledDate = request.appointmentTimes.minOrNull() ?: base.scheduledDate,
            updatedAt = now,
            isSynced = false,
            pricing = nextPricing,
            latitude = if (!addressChanged) base.latitude else null,
            longitude = if (!addressChanged) base.longitude else null
        )
        job = withCoordinates(job, linked)
        jobDao.insert(job)
        if (linked != null) {
            propagateToSiblingJobs(linked, job.id, now)
        }
        replacePendingVisits(job, request.appointmentTimes)
        return JobSaveResult(job, linked)
    }

    /**
     * Update the shared customer from Job Details without rewriting the
     * schedule. Still marks both rows unsynced.
     */
    suspend fun saveCustomerOnJob(job: Job, draft: JobCustomerDraft): JobSaveResult {
        val now = System.currentTimeMillis()
        val linked = upsertCustomer(draft, now)
        val serviceAddress = linked?.let { composeStoredAddress(it) }
            ?: draft.composedServiceAddress()
        val displayName = linked?.fullName?.trim().orEmpty()
            .ifBlank { draft.displayName() }
        val addressChanged = serviceAddress.isNotBlank() && serviceAddress != job.address
        var updated = job.copy(
            customerId = linked?.id.orEmpty(),
            customerName = displayName,
            address = serviceAddress.ifBlank { job.address },
            updatedAt = now,
            isSynced = false,
            latitude = if (!addressChanged) job.latitude else null,
            longitude = if (!addressChanged) job.longitude else null
        )
        updated = withCoordinates(updated, linked)
        jobDao.insert(updated)
        if (linked != null) {
            propagateToSiblingJobs(linked, updated.id, now)
        }
        visitDao.getByJobOnce(updated.id).forEach { visit ->
            if (visit.customerId != updated.customerId || visit.customerName != updated.customerName) {
                visitDao.insert(
                    visit.copy(
                        customerId = updated.customerId,
                        customerName = updated.customerName,
                        updatedAt = now,
                        isSynced = false
                    )
                )
            }
        }
        return JobSaveResult(updated, linked)
    }

    private suspend fun upsertCustomer(draft: JobCustomerDraft, now: Long): Customer? {
        if (!draft.hasAnyInfo()) return null
        val existing = draft.customerId.takeIf { it.isNotBlank() }?.let { customerDao.getById(it) }
        val (firstName, lastName) = JobCustomerDraft.parsePersonName(draft.name)
        val id = existing?.id ?: draft.customerId.ifBlank { UUID.randomUUID().toString() }
        var customer = (existing ?: Customer(id = id)).copy(
            id = id,
            firstName = firstName,
            lastName = lastName,
            companyName = draft.companyName.trim(),
            email = draft.email.trim(),
            phone = draft.phone.trim(),
            address = draft.address.trim(),
            city = draft.city.trim(),
            state = draft.state.trim(),
            zipCode = draft.zipCode.trim(),
            notes = draft.notes.trim(),
            billingAddress = draft.billingAddress.trim(),
            billingContact = draft.preferredContact.label,
            paymentTerms = existing?.paymentTerms ?: "Net 30",
            customerType = existing?.customerType ?: com.strobingn.wildlifefieldops.data.model.CustomerType.RESIDENTIAL,
            alternatePhone = existing?.alternatePhone.orEmpty(),
            isActive = existing?.isActive ?: true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isSynced = false,
            syncError = null,
            latitude = existing?.latitude,
            longitude = existing?.longitude
        )
        val geoQuery = composeStoredAddress(customer)
        if (geoQuery.isNotBlank() && (customer.latitude == null || customer.longitude == null ||
                customer.address != existing?.address || customer.city != existing?.city)
        ) {
            geocode(geoQuery)?.let { point ->
                customer = customer.copy(latitude = point.latitude, longitude = point.longitude)
            }
        }
        customerDao.insert(customer)
        return customer
    }

    private suspend fun propagateToSiblingJobs(customer: Customer, currentJobId: String, now: Long) {
        val serviceAddress = composeStoredAddress(customer)
        val displayName = customer.fullName
        jobDao.getByCustomerOnce(customer.id)
            .filter { it.id != currentJobId }
            .forEach { sibling ->
                val addressChanged = sibling.address != serviceAddress
                var updated = sibling.copy(
                    customerName = displayName,
                    address = serviceAddress.ifBlank { sibling.address },
                    updatedAt = now,
                    isSynced = false,
                    latitude = if (!addressChanged) sibling.latitude else customer.latitude ?: sibling.latitude,
                    longitude = if (!addressChanged) sibling.longitude else customer.longitude ?: sibling.longitude
                )
                if (updated.latitude == null || updated.longitude == null) {
                    updated = withCoordinates(updated, customer)
                }
                jobDao.insert(updated)
            }
    }

    private suspend fun replacePendingVisits(job: Job, appointmentTimes: List<Long>) {
        visitDao.deletePendingForJob(job.id)
        appointmentTimes.distinct().sorted().forEach { scheduledAt ->
            visitDao.insert(
                Visit(
                    jobId = job.id,
                    customerId = job.customerId,
                    customerName = job.customerName,
                    technicianName = job.assignedTo,
                    visitDate = scheduledAt,
                    startTime = scheduledAt
                )
            )
        }
    }

    private suspend fun withCoordinates(job: Job, customer: Customer?): Job {
        if (job.latitude != null && job.longitude != null) return job
        if (customer?.latitude != null && customer.longitude != null) {
            return job.copy(latitude = customer.latitude, longitude = customer.longitude)
        }
        if (job.address.isBlank()) return job
        val point = geocode(job.address) ?: return job
        return job.copy(latitude = point.latitude, longitude = point.longitude)
    }

    private fun composeStoredAddress(customer: Customer): String =
        JobCustomerDraft.fromCustomer(customer).composedServiceAddress()
            .ifBlank { customer.address.trim() }
}
