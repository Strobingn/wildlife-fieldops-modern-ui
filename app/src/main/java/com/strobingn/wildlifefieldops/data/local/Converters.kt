package com.strobingn.wildlifefieldops.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromJobStatus(value: JobStatus): String = value.name

    @TypeConverter
    fun toJobStatus(value: String): JobStatus = try {
        JobStatus.valueOf(value)
    } catch (_: Exception) {
        JobStatus.PENDING
    }

    @TypeConverter
    fun fromJobPriority(value: JobPriority): String = value.name

    @TypeConverter
    fun toJobPriority(value: String): JobPriority = try {
        JobPriority.valueOf(value)
    } catch (_: Exception) {
        JobPriority.MEDIUM
    }

    @TypeConverter
    fun fromJobType(value: JobType): String = value.name

    /** Accepts enum names or free-form service labels from older / custom rows. */
    @TypeConverter
    fun toJobType(value: String): JobType = try {
        JobType.valueOf(value)
    } catch (_: IllegalArgumentException) {
        JobType.fromLabel(value)
    }

    @TypeConverter
    fun fromCustomerType(value: CustomerType): String = value.name

    @TypeConverter
    fun toCustomerType(value: String): CustomerType = CustomerType.valueOf(value)

    @TypeConverter
    fun fromInspectionType(value: InspectionType): String = value.name

    @TypeConverter
    fun toInspectionType(value: String): InspectionType = InspectionType.valueOf(value)

    @TypeConverter
    fun fromFindingSeverity(value: FindingSeverity): String = value.name

    @TypeConverter
    fun toFindingSeverity(value: String): FindingSeverity = FindingSeverity.valueOf(value)

    @TypeConverter
    fun fromPhotoCategory(value: PhotoCategory): String = value.name

    @TypeConverter
    fun toPhotoCategory(value: String): PhotoCategory = PhotoCategory.valueOf(value)

    @TypeConverter
    fun fromExpenseCategory(value: ExpenseCategory): String = value.name

    @TypeConverter
    fun toExpenseCategory(value: String): ExpenseCategory = ExpenseCategory.valueOf(value)

    @TypeConverter
    fun fromExpenseStatus(value: ExpenseStatus): String = value.name

    @TypeConverter
    fun toExpenseStatus(value: String): ExpenseStatus = ExpenseStatus.valueOf(value)

    @TypeConverter
    fun fromTrapStatus(value: TrapStatus): String = value.name

    @TypeConverter
    fun toTrapStatus(value: String): TrapStatus = TrapStatus.valueOf(value)

    @TypeConverter
    fun fromCatchType(value: CatchType): String = value.name

    @TypeConverter
    fun toCatchType(value: String): CatchType = CatchType.valueOf(value)

    @TypeConverter
    fun fromReminderType(value: ReminderType): String = value.name

    @TypeConverter
    fun toReminderType(value: String): ReminderType = ReminderType.valueOf(value)

    @TypeConverter
    fun fromReminderPriority(value: ReminderPriority): String = value.name

    @TypeConverter
    fun toReminderPriority(value: String): ReminderPriority = ReminderPriority.valueOf(value)

    @TypeConverter
    fun fromReminderStatus(value: ReminderStatus): String = value.name

    @TypeConverter
    fun toReminderStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)

    @TypeConverter
    fun fromInvoiceStatus(value: InvoiceStatus): String = value.name

    @TypeConverter
    fun toInvoiceStatus(value: String): InvoiceStatus = InvoiceStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromInvoiceLineItemList(value: List<InvoiceLineItem>): String = gson.toJson(value)

    @TypeConverter
    fun toInvoiceLineItemList(value: String): List<InvoiceLineItem> {
        val listType = object : TypeToken<List<InvoiceLineItem>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromJobPricing(value: JobPricing): String = PricingJson.encode(value)

    @TypeConverter
    fun toJobPricing(value: String?): JobPricing = PricingJson.decode(value)
}
