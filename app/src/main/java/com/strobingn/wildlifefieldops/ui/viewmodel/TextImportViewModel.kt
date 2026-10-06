package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.TextMessageImport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Optional AI refine and existing-customer lookup for a text import.
 * The form still shows the offline parse immediately and does not save.
 */
@HiltViewModel
class TextImportViewModel @Inject constructor(
    private val aiService: AiService,
    private val customerDao: CustomerDao
) : ViewModel() {

    suspend fun refine(text: String, senderPhone: String?): TextMessageImport.Fields {
        val body = text.trim()
        if (body.isBlank()) return TextMessageImport.Fields()
        return runCatching { aiService.assistCustomerText(body, senderPhone) }
            .getOrElse { TextMessageImport.parse(body, senderPhone) }
    }

    suspend fun matches(fields: TextMessageImport.Fields): List<Customer> {
        val all = runCatching { customerDao.getAllOnce() }.getOrDefault(emptyList())
        return TextMessageImport.matchingCustomers(all, fields)
    }
}
