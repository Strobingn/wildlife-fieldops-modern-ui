package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.pricing.Money

data class ProfitInput(
    val quoted: Double = 0.0,
    val paid: Double = 0.0,
    val materialsCost: Double = 0.0,
    val laborHours: Double = 0.0,
    val laborRate: Double = 85.0,
    val laborCostOverride: Double? = null,
    val expenses: Double = 0.0,
    val mileageCost: Double = 0.0
)

data class ProfitResult(
    val revenue: Double,
    val laborCost: Double,
    val materialsCost: Double,
    val expenses: Double,
    val mileageCost: Double,
    val totalCost: Double,
    val profit: Double,
    val marginPercent: Double
)

object JobProfit {
    fun compute(input: ProfitInput): ProfitResult {
        val revenue = Money.round(
            if (input.paid > 0.0) input.paid else input.quoted
        )
        val laborCalc = Money.times(input.laborHours, input.laborRate)
        val labor = input.laborCostOverride?.let { Money.round(it) } ?: laborCalc
        val materials = Money.round(input.materialsCost)
        val expenses = Money.round(input.expenses)
        val mileage = Money.round(input.mileageCost)
        val cost = Money.plus(labor, materials, expenses, mileage)
        val profit = Money.round(revenue - cost)
        val margin = if (revenue > 0.0) Money.round((profit / revenue) * 100.0) else 0.0
        return ProfitResult(
            revenue = revenue,
            laborCost = labor,
            materialsCost = materials,
            expenses = expenses,
            mileageCost = mileage,
            totalCost = cost,
            profit = profit,
            marginPercent = margin
        )
    }
}
