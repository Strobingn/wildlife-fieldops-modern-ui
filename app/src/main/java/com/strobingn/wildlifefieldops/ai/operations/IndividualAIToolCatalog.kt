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
        Tool("forecast_confidence", "Forecast Confidence Center", "Explains whether the dataset is mature enough for forecasts.", "Forecast maturity score", listOf("Complete missing job fields", "Record schedules and closeout costs", "Treat low-maturity forecasts as directional only")),

        // 21-40: additional launchable tools added alongside the 66-85 engine signals.
        Tool("lead_aging", "New Lead Aging Monitor", "Flags pending leads that have gone unscheduled for more than 48 hours.", "New lead aging alert", listOf("Call the customer back", "Confirm species and urgency", "Book the earliest realistic appointment")),
        Tool("urgent_unscheduled", "Urgent Scheduling Guard", "Finds urgent or high-priority jobs that still have no appointment.", "Urgent-without-schedule alert", listOf("Open every urgent/high job with no date", "Call to confirm availability", "Lock in a firm appointment today")),
        Tool("priority_mix", "Priority Mix Auditor", "Checks how much open work is marked low priority.", "Priority mix auditor", listOf("Re-read low-priority job notes", "Confirm no safety or urgency signal was missed", "Re-prioritize if evidence supports it")),
        Tool("returning_customers", "Returning Customer Tracker", "Measures how much of the business is repeat customers.", "Returning-customer ratio", listOf("Pull prior visit history", "Assign a consistent technician when possible", "Reference past work in the estimate")),
        Tool("concurrent_customer_jobs", "Duplicate Job Detector", "Finds customers with more than one open job at the same time.", "Concurrent-job customer alert", listOf("Open both jobs side by side", "Confirm they are separate service needs", "Merge or cancel true duplicates")),
        Tool("technician_concentration", "Technician Bus-Factor Monitor", "Shows how concentrated open work is on one technician.", "Technician concentration risk", listOf("Review the busiest technician's queue", "Reassign jobs that do not require that specialist", "Cross-train a backup technician")),
        Tool("title_hygiene", "Job Title Cleanup Assistant", "Finds jobs with blank or generic titles.", "Blank-title data hygiene", listOf("Open each flagged job", "Rename using service type and address", "Save and confirm it appears correctly in lists")),
        Tool("coordinate_integrity", "GPS Integrity Checker", "Finds jobs storing invalid 0,0 coordinates.", "Coordinate integrity checker", listOf("Re-capture GPS from the field", "Or re-geocode the saved address", "Verify the pin lands on the correct property")),
        Tool("prework_photos", "Pre-Work Photo Reminder", "Finds active jobs with no photos captured yet.", "Pre-work photo gap", listOf("Capture exterior condition photos", "Capture any visible entry points", "Attach photos before leaving the property")),
        Tool("undocumented_value", "Undocumented Value Tracker", "Totals completed-job value with zero photo documentation.", "Undocumented value at risk", listOf("Sort completed jobs by value", "Request or capture missing photos", "Close the highest-value gaps first")),
        Tool("multi_species", "Multi-Species Complexity Planner", "Finds jobs that involve more than one species.", "Multi-species complexity flag", listOf("Confirm every species present", "Plan method and timing per species", "Allow extra labor time on the visit")),
        Tool("bat_season_compliance", "Bat Maternity Season Guard", "Flags bat jobs scheduled during the June-August maternity window.", "Bat-season compliance timing", listOf("Verify current maternity-season rules", "Inspect for dependent young before exclusion", "Document the legal basis for the chosen method")),
        Tool("sla_compliance", "Urgent Response SLA Tracker", "Measures how many urgent/high jobs were scheduled within 24 hours.", "Service-level response compliance", listOf("Review urgent jobs scheduled late", "Identify the delay cause", "Tighten dispatch process for the next urgent call")),
        Tool("long_cycle_jobs", "Long-Cycle Job Reviewer", "Finds completed jobs that took more than 30 days start to finish.", "Long-cycle job detector", listOf("Review the scheduling and material delays", "Note any customer-caused delay", "Adjust the process for similar future jobs")),
        Tool("round_estimates", "Estimate Itemization Checker", "Finds priced jobs using a flat round-number estimate.", "Round-number estimate detector", listOf("Reopen the estimate", "Break out labor, material, and travel", "Confirm the total still matches the quoted price")),
        Tool("field_readiness", "Dispatch Readiness Index", "Scores how many open jobs are fully ready for dispatch.", "Field readiness composite index", listOf("Confirm address and coordinates", "Confirm assigned technician", "Confirm access and safety notes are recorded")),
        Tool("technician_overlap", "Technician Overload Detector", "Finds technician-days with more than 4 scheduled stops.", "Technician day-overlap detector", listOf("Review the overloaded technician's day", "Move flexible stops to another day or technician", "Confirm realistic travel time between stops")),
        Tool("cost_escalation", "Repeat-Property Cost Escalation Tracker", "Finds repeat properties where cost is rising visit to visit.", "Repeat-property cost trend", listOf("Compare entry points across visits", "Check for scope creep or a missed root cause", "Discuss a permanent exclusion plan with the customer")),
        Tool("name_formatting", "Customer Name Formatting Auditor", "Finds customer names stored in all caps.", "Customer name formatting auditor", listOf("Open the customer record", "Correct capitalization", "Save so invoices and messages read correctly")),
        Tool("commercial_sites", "Commercial Site Planner", "Flags jobs at suite, unit, or multi-tenant addresses.", "Commercial-site complexity flag", listOf("Confirm property manager contact", "Confirm access hours and parking", "Confirm any site-specific safety requirements"))
    )
}
