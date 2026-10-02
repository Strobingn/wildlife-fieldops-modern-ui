package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.InventoryItemDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.ReminderDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Reminder
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.data.model.ReminderPriority
import com.strobingn.wildlifefieldops.data.model.ReminderStatus
import com.strobingn.wildlifefieldops.data.model.ReminderType
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

    suspend fun updateMaterial(jobId: String, usageId: String, qty: Double): String? {
        if (qty <= 0.0) return "Enter a quantity, or remove the line."
        val job = jobDao.getById(jobId) ?: return "Job missing"
        val usage = job.pricing.materialUsages.firstOrNull { it.id == usageId } ?: return "Line missing"
        val item = inventoryItemDao.getById(usage.itemId)
        val now = System.currentTimeMillis()
        if (item != null) {
            inventoryItemDao.update(
                item.copy(
                    quantityOnHand = InventoryDeduct.adjust(item.quantityOnHand, usage.quantity, qty),
                    updatedAt = now,
                    isSynced = false
                )
            )
        }
        val usages = job.pricing.materialUsages.map { if (it.id == usageId) it.copy(quantity = qty) else it }
        persist(job) { it.copy(materialUsages = usages, materialsCostActual = materialCost(it, usages)) }
        return "Updated ${usage.name}."
    }

    suspend fun removeMaterial(jobId: String, usageId: String): String? {
        val job = jobDao.getById(jobId) ?: return "Job missing"
        val usage = job.pricing.materialUsages.firstOrNull { it.id == usageId } ?: return "Line missing"
        val item = inventoryItemDao.getById(usage.itemId)
        val now = System.currentTimeMillis()
        if (item != null) {
            inventoryItemDao.update(
                item.copy(
                    quantityOnHand = InventoryDeduct.restore(item.quantityOnHand, usage.quantity),
                    updatedAt = now,
                    isSynced = false
                )
            )
        }
        val usages = job.pricing.materialUsages.filterNot { it.id == usageId }
        persist(job) { it.copy(materialUsages = usages, materialsCostActual = materialCost(it, usages)) }
        return "Removed ${usage.name}. Stock restored."
    }

    private fun materialCost(pricing: JobPricing, usages: List<JobMaterialUsage>): Double {
        val sum = usages.sumOf { it.amount }
        return if (pricing.materialsCostActual > 0.0) {
            val previous = pricing.materialUsages.sumOf { it.amount }
            (pricing.materialsCostActual - previous + sum).coerceAtLeast(0.0)
        } else {
            sum
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

    suspend fun saveSeasonal(job: Job, save: SeasonalSave) {
        val latest = jobDao.getById(job.id) ?: job
        val existingId = latest.pricing.seasonalReminderId
        val newId = UUID.randomUUID().toString()
        val reminderId = SeasonalReminder.reminderIdForSave(existingId, save.dueAt, newId)
        if (save.dueAt == null) {
            if (existingId.isNotBlank()) {
                reminderDao.getById(existingId)?.let { reminderDao.delete(it) }
            }
        } else {
            reminderDao.insert(
                Reminder(
                    id = reminderId,
                    title = save.title.ifBlank { "Seasonal reminder" },
                    description = save.notes,
                    jobId = latest.id,
                    customerId = latest.customerId.takeIf { it.isNotBlank() },
                    customerName = latest.customerName,
                    reminderType = ReminderType.SCHEDULE_MAINTENANCE,
                    priority = ReminderPriority.MEDIUM,
                    status = ReminderStatus.PENDING,
                    dueDate = save.dueAt,
                    notes = save.notes,
                    isSynced = false
                )
            )
        }
        persist(latest) {
            it.markManual(ManualField.SEASONAL_TITLE, ManualField.SEASONAL_NOTES, ManualField.SEASONAL_DUE).copy(
                seasonalKind = save.kind?.name.orEmpty(),
                seasonalDueAt = save.dueAt,
                seasonalTitle = save.title,
                seasonalNotes = save.notes,
                seasonalReminderId = reminderId
            )
        }
    }

    suspend fun mergeCustomers(keepId: String, dropId: String): CustomerMergeUndo? {
        if (keepId.isBlank() || dropId.isBlank() || keepId == dropId) return null
        val keep = customerDao.getById(keepId) ?: return null
        val drop = customerDao.getById(dropId) ?: return null
        val moved = jobDao.getByCustomerOnce(drop.id).map {
            JobCustomerLink(it.id, it.customerId, it.customerName, it.address)
        }
        val snapshot = CustomerMergeUndo(keep, drop, moved)
        val merged = DuplicateCustomer.mergeKeepLeft(keep, drop)
        customerDao.insert(merged)
        val now = System.currentTimeMillis()
        jobDao.getByCustomerOnce(drop.id).forEach { job ->
            jobDao.insert(
                JobFieldOpsCodec.mergeForSave(
                    job.copy(
                        customerId = merged.id,
                        customerName = merged.fullName,
                        address = job.address.ifBlank { listOf(merged.address, merged.city, merged.state, merged.zipCode).filter { it.isNotBlank() }.joinToString(", ") },
                        updatedAt = now,
                        isSynced = false
                    )
                )
            )
        }
        customerDao.insert(drop.copy(isActive = false, notes = drop.notes + "\nMerged into ${merged.id}", updatedAt = now, isSynced = false))
        return snapshot
    }

    suspend fun undoMerge(snapshot: CustomerMergeUndo) {
        val now = System.currentTimeMillis()
        customerDao.insert(snapshot.keepBefore.copy(updatedAt = now, isSynced = false))
        customerDao.insert(snapshot.dropBefore.copy(isActive = true, updatedAt = now, isSynced = false))
        snapshot.movedJobs.forEach { link ->
            val job = jobDao.getById(link.jobId) ?: return@forEach
            jobDao.insert(
                JobFieldOpsCodec.mergeForSave(
                    job.copy(
                        customerId = link.customerId,
                        customerName = link.customerName,
                        address = link.address,
                        updatedAt = now,
                        isSynced = false
                    )
                )
            )
        }
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
