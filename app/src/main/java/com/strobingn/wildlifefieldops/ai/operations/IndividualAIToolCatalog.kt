package com.strobingn.wildlifefieldops.ai.operations

/**
 * User-facing AI tools. Each entry launches independently and binds to one
 * real-data signal produced by [AIOperationsEngine].
 */
object IndividualAIToolCatalog {
    data class Tool(
        val id: String,
        val title: String,
        val purpose: String,
        val insightName: String,
        val checklist: List<String>
    )

    val tools: List<Tool> = listOf(
        Tool("lead_response", "Lead Response Coach", "Measures how quickly new calls become appointments.", "Lead-to-appointment speed", listOf("Review oldest unscheduled lead", "Confirm urgency and species", "Offer the earliest appropriate window")),
        Tool("weekend_staffing", "Weekend Staffing Planner", "Uses scheduled work to measure weekend demand.", "Weekend workload predictor", listOf("Review Saturday and Sunday stops", "Confirm emergency coverage", "Verify weekend pricing policy")),
        Tool("morning_routes", "Morning Route Planner", "Finds morning workload and protects species activity windows.", "Morning route load", listOf("Group nearby morning stops", "Load long-job materials first", "Confirm dawn access instructions")),
        Tool("late_day_safety", "Late-Day Safety Planner", "Flags work that may run beyond safe daylight.", "Late-day route load", listOf("Review sunset and roof access", "Move flexible exterior work earlier", "Confirm lighting and second-technician needs")),
        Tool("tomorrow_prep", "Tomorrow Prep Assistant", "Builds a readiness signal for tomorrow's appointments.", "Tomorrow readiness check", listOf("Verify every address", "Confirm assigned technician", "Check access, equipment, and customer contact")),
        Tool("customer_records", "Customer Record Auditor", "Finds jobs that are not connected to a verified customer.", "Customer identity auditor", listOf("Add customer name", "Link the customer record", "Verify phone, email, and property address")),
        Tool("service_classifier", "Service Type Classifier", "Finds jobs that cannot feed pricing or inventory forecasts.", "Service classification assistant", listOf("Read verified field evidence", "Choose the correct service type", "Avoid species guesses without confirmation")),
        Tool("cancelled_recovery", "Cancelled Job Recovery", "Measures estimated value lost to cancelled work.", "Cancelled revenue leakage", listOf("Review cancellation reason", "Separate lost work from postponed work", "Contact only customers with a justified next step")),
        Tool("invoice_recovery", "Invoice Collection Assistant", "Prioritizes value still sitting in invoiced status.", "Invoice value recovery", listOf("Sort invoices by age", "Verify completion documentation", "Send a factual payment reminder")),
        Tool("price_consistency", "Price Consistency Analyzer", "Measures estimate variation across recorded work.", "Price consistency monitor", listOf("Compare the same service scopes", "Separate access and material differences", "Update the price book only from comparable jobs")),
        Tool("profit_margin", "Profit Margin Analyzer", "Reviews recorded margin on the highest-volume service.", "Top-service margin analyzer", listOf("Confirm actual labor cost", "Confirm material cost", "Review callbacks and unbilled scope")),
        Tool("momentum", "Business Momentum Tracker", "Shows how much operational data changed recently.", "Operational momentum", listOf("Review the last seven days", "Resolve stagnant open work", "Use recent data for near-term decisions")),
        Tool("follow_up_finder", "Follow-Up Task Finder", "Extracts follow-up commitments from job notes.", "Follow-up commitment extractor", listOf("Open each identified job", "Create a dated visit or task", "Record the promised customer outcome")),
        Tool("repair_scope", "Repair Scope Builder", "Finds structural entry and repair evidence in records.", "Structural repair scope classifier", listOf("Measure the opening", "Identify substrate and access", "List material and secondary gaps")),
        Tool("zoonotic_safety", "Zoonotic Safety Assistant", "Flags possible disease, bite, and waste exposure.", "Zoonotic exposure warning", listOf("Verify the hazard", "Select required PPE", "Document containment and disposal procedure")),
        Tool("weather_work", "Weather Work Planner", "Finds jobs with weather-sensitive field conditions.", "Weather-sensitive work planner", listOf("Check the current forecast", "Review roof, ladder, sealant, and trap safety", "Reschedule unsafe work")),
        Tool("equipment_loadout", "Equipment Loadout Builder", "Turns equipment cues in notes into a loadout review.", "Equipment cue extractor", listOf("Check ladders and lift needs", "Load species-specific devices", "Verify PPE, repair material, and camera")),
        Tool("property_access", "Property Access Planner", "Detects gates, tenants, pets, keys, and permission risks.", "Property access blocker", listOf("Confirm gate or access code", "Confirm tenant and pet arrangements", "Verify legal permission before entry")),
        Tool("warranty_closeout", "Warranty Closeout Assistant", "Checks whether completed work records warranty scope.", "Warranty record auditor", listOf("Record covered repairs", "Record exclusions and customer duties", "Record warranty term and start date")),
        Tool("forecast_confidence", "Forecast Confidence Center", "Explains whether the dataset is mature enough for forecasts.", "Forecast maturity score", listOf("Complete missing job fields", "Record schedules and closeout costs", "Treat low-maturity forecasts as directional only"))
    )
}
