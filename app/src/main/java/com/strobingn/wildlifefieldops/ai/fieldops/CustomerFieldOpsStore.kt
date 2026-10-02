package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.InventoryItemDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.ReminderDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Reminder
import com.strobingn.wildlifefieldops.data.model.ReminderPriority
import com.strobingn.wildlifefieldops.data.model.ReminderStatus
import com.strobingn.wildlifefieldops.data.model.ReminderType
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.markManual
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerFieldOpsStore @Inject constructor(
    private val jobDao: JobDao,
    private val inventoryItemDao: InventoryItemDao,
    private val reminderDao: ReminderDao,
    private val customerDao: CustomerDao
) {

    suspend fun deductMaterial(jobId: String, itemId: String, qty: Double): String? {
        val job = jobDao.getById(jobId) ?: return "Job missing"
        val item = inventoryItemDao.getById(itemId) ?: return "Item missing"
        val used = qty.coerceAtLeast(0.1)
        val nextQty = InventoryDeduct.nextOnHand(item.quantityOnHand, used)
        inventoryItemDao.update(
            item.copy(
                quantityOnHand = nextQty,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
        )
        val usage = JobMaterialUsage(
            id = UUID.randomUUID().toString(),
            itemId = item.id,
            name = item.name,
            quantity = used,
            unitCost = item.unitCost
        )
        persist(job) { pricing ->
            val usages = pricing.materialUsages + usage
            val nextMaterials = if (pricing.materialsCostActual > 0) {
                pricing.materialsCostActual + usage.amount
            } else {
                usages.sumOf { it.amount }
            }
            pricing.copy(materialUsages = usages, materialsCostActual = nextMaterials)
        }
        return if (InventoryDeduct.isLow(nextQty, item.reorderLevel)) {
            "Used ${item.name}. Low stock: $nextQty ${item.unitOfMeasure}."
        } else {
            "Used ${item.name}."
        }
    }

    suspend fun saveWarranty(jobId: String, startAt: Long, termMonths: Int, covered: String) {
        val job = jobDao.getById(jobId) ?: return
        persist(job) {
            it.markManual(ManualField.WARRANTY_COVERED).copy(
                warrantyStartAt = startAt,
                warrantyTermMonths = termMonths.coerceAtLeast(0),
                warrantyCovered = covered
            )
        }
        val plan = WarrantyTracker.fromJob(startAt, termMonths, covered)
        val exp = plan.expiresAt ?: return
        reminderDao.insert(
            Reminder(
                title = "Warranty expiring — ${job.title.ifBlank { job.customerName }}",
                description = covered,
                jobId = job.id,
                customerId = job.customerId.takeIf { it.isNotBlank() },
                customerName = job.customerName,
                reminderType = ReminderType.WARRANTY_EXPIRING,
                priority = ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                dueDate = exp,
                notes = covered,
                isSynced = false
            )
        )
    }

    suspend fun saveSeasonal(job: Job, draft: SeasonalDraft) {
        persist(job) {
            it.markManual(ManualField.SEASONAL_TITLE, ManualField.SEASONAL_NOTES, ManualField.SEASONAL_DUE).copy(
                seasonalKind = draft.kind.name,
                seasonalDueAt = draft.dueAt,
                seasonalTitle = draft.title,
                seasonalNotes = draft.notes
            )
        }
        reminderDao.insert(
            Reminder(
                title = draft.title,
                description = draft.notes,
                jobId = job.id,
                customerId = job.customerId.takeIf { it.isNotBlank() },
                customerName = job.customerName,
                reminderType = ReminderType.SCHEDULE_MAINTENANCE,
                priority = ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                dueDate = draft.dueAt,
                notes = draft.notes,
                isSynced = false
            )
        )
    }

    suspend fun mergeCustomers(keepId: String, dropId: String) {
        val keep = customerDao.getById(keepId) ?: return
        val drop = customerDao.getById(dropId) ?: return
        val merged = DuplicateCustomer.mergeKeepLeft(keep, drop)
        customerDao.insert(merged)
        jobDao.getByCustomerOnce(drop.id).forEach { job ->
            jobDao.insert(
                JobFieldOpsCodec.mergeForSave(
                    job.copy(
                        customerId = merged.id,
                        customerName = merged.fullName,
                        address = job.address.ifBlank { listOf(merged.address, merged.city, merged.state, merged.zipCode).filter { it.isNotBlank() }.joinToString(", ") },
                        updatedAt = System.currentTimeMillis(),
                        isSynced = false
                    )
                )
            )
        }
        customerDao.insert(drop.copy(isActive = false, notes = drop.notes + "\nMerged into ${merged.id}", updatedAt = System.currentTimeMillis(), isSynced = false))
    }

    suspend fun expiringWarranties(now: Long = System.currentTimeMillis()): List<Pair<Job, WarrantyPlan>> =
        jobDao.getAllOnce().mapNotNull { job ->
            val plan = WarrantyTracker.fromJob(job.pricing.warrantyStartAt, job.pricing.warrantyTermMonths, job.pricing.warrantyCovered)
            if (plan.startAt != null && (plan.isExpiring(now) || plan.isExpired(now))) job to plan else null
        }

    private suspend fun persist(job: Job, update: (JobPricing) -> JobPricing) {
        jobDao.insert(
            JobFieldOpsCodec.mergeForSave(
                job.copy(pricing = update(job.pricing), updatedAt = System.currentTimeMillis(), isSynced = false)
            )
        )
    }
}
