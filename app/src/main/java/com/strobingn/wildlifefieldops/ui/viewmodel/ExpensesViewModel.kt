package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.ExpenseDao
import com.strobingn.wildlifefieldops.data.model.Expense
import com.strobingn.wildlifefieldops.data.model.ExpenseCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseDao: ExpenseDao
) : ViewModel() {

    val expenses = expenseDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalThisMonth = expenseDao.getAll()
        .map { list ->
            val now = System.currentTimeMillis()
            val monthStart = LocalPeriods.monthStart(now)
            list.filter { it.expenseDate >= monthStart }
                .sumOf { it.totalAmount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addExpense(expense: Expense) = viewModelScope.launch {
        expenseDao.insert(expense)
    }

    fun updateExpense(expense: Expense) = viewModelScope.launch {
        expenseDao.update(expense)
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        expenseDao.delete(expense)
    }

    fun createExpense(
        category: ExpenseCategory,
        description: String,
        amount: Double,
        taxAmount: Double,
        expenseDate: Long,
        vendorName: String,
        mileage: Double?,
        notes: String,
        jobId: String? = null
    ) = viewModelScope.launch {
        val total = amount + taxAmount
        val expense = Expense(
            category = category,
            description = description,
            amount = amount,
            taxAmount = taxAmount,
            totalAmount = total,
            expenseDate = expenseDate,
            vendorName = vendorName,
            mileage = mileage,
            notes = notes,
            jobId = jobId
        )
        expenseDao.insert(expense)
    }
}
