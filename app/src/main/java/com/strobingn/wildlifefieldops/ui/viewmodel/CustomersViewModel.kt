package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.DeletedRecordDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import com.strobingn.wildlifefieldops.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val customerDao: CustomerDao,
    private val deletedRecordDao: DeletedRecordDao,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val customers = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            customerDao.getAll()
        } else {
            customerDao.search(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerCount = customerDao.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getCustomerById(id: String): Flow<Customer?> = flow {
        emit(customerDao.getById(id))
    }

    fun saveCustomer(customer: Customer) = viewModelScope.launch {
        customerDao.insert(customer)
    }

    fun updateCustomer(customer: Customer) = viewModelScope.launch {
        customerDao.update(customer.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteCustomer(customer: Customer) = viewModelScope.launch {
        deletedRecordDao.insert(
            DeletedRecord(
                id = customer.id,
                entityType = DeletedRecord.TYPE_CUSTOMER,
                synced = false
            )
        )
        customerDao.delete(customer)
        syncRepository.tryRemoteDelete(DeletedRecord.TYPE_CUSTOMER, customer.id)
    }

    fun createCustomer(
        firstName: String,
        lastName: String,
        companyName: String,
        email: String,
        phone: String,
        address: String,
        city: String,
        state: String,
        zipCode: String,
        customerType: com.strobingn.wildlifefieldops.data.model.CustomerType,
        notes: String,
        billingAddress: String,
        billingContact: String,
        paymentTerms: String
    ) = viewModelScope.launch {
        val customer = Customer(
            firstName = firstName,
            lastName = lastName,
            companyName = companyName,
            email = email,
            phone = phone,
            address = address,
            city = city,
            state = state,
            zipCode = zipCode,
            customerType = customerType,
            notes = notes,
            billingAddress = billingAddress,
            billingContact = billingContact,
            paymentTerms = paymentTerms
        )
        customerDao.insert(customer)
    }
}
