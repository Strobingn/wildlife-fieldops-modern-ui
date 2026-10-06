package com.strobingn.wildlifefieldops.util

import android.content.Context
import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.model.Customer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentContactsEntryPoint {
    fun customerDao(): CustomerDao
}

/**
 * Every PDF packet builder runs its packet through [fill] so blank customer
 * phone/email come from the job's linked customer record. A failed lookup
 * never blocks the PDF: the packet is returned as given.
 */
object DocumentContacts {
    fun customerFor(context: Context, customerId: String): Customer? {
        if (customerId.isBlank()) return null
        return runCatching {
            val dao = EntryPointAccessors
                .fromApplication(context.applicationContext, DocumentContactsEntryPoint::class.java)
                .customerDao()
            runBlocking { withContext(Dispatchers.IO) { dao.getById(customerId) } }
        }.getOrNull()
    }

    fun fill(context: Context, packet: StandardJobPacket): StandardJobPacket {
        if (packet.customerPhone.isNotBlank() && packet.customerEmail.isNotBlank()) return packet
        return JobContactFallback.apply(packet, customerFor(context, packet.job.customerId))
    }
}
