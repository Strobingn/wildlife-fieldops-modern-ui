from __future__ import annotations

import json
import os
import uuid
from functools import lru_cache
from typing import Any

from agent_framework import Agent, WorkflowBuilder
from agent_framework.openai import OpenAIChatCompletionClient
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict

app = FastAPI(title="Wildlife FieldOps Agent Framework")

class FieldOpsRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    mode: str = "field_plan"
    job: dict[str, Any] = {}
    observation: str = ""
    species: str = ""
    services: list[dict[str, Any]] = []
    inspections: list[dict[str, Any]] = []
    businessContext: str = ""

def setting(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()

def api_key() -> str:
    value = setting("AGENT_FRAMEWORK_API_KEY")
    if not value:
        raise HTTPException(503, "Agent Framework model key is not configured.")
    return value

def model() -> str:
    return setting("AGENT_FRAMEWORK_MODEL", "grok-4.5")

def base_url() -> str:
    return setting("AGENT_FRAMEWORK_BASE_URL", "https://api.x.ai/v1")

def check_secret(authorization: str | None) -> None:
    expected = setting("AGENT_FRAMEWORK_SHARED_SECRET")
    if expected and authorization != "Bearer " + expected:
        raise HTTPException(401, "Invalid service authorization.")

def prompt(payload: FieldOpsRequest) -> str:
    return json.dumps({
        "mode": payload.mode,
        "job": payload.job,
        "observation": payload.observation[:6000],
        "species": payload.species,
        "services": payload.services,
        "inspections": payload.inspections,
        "business_context": payload.businessContext or "Small nuisance wildlife removal company. Prefer practical, documented field work.",
    }, indent=2, default=str)

@lru_cache(maxsize=1)
def workflow():
    client = OpenAIChatCompletionClient(api_key=api_key(), base_url=base_url(), model=model())
    planner = Agent(
        client=client,
        name="FieldPlanner",
        instructions=(
            "Create a practical wildlife field plan from the job data. "
            "Separate observed facts from assumptions. Recommend inspection, exclusion, "
            "documentation, and customer follow-up steps. Do not invent legal requirements. "
            "Pass a concise plan to the next reviewer."
        ),
    )
    reviewer = Agent(
        client=client,
        name="SafetyAndComplianceReviewer",
        instructions=(
            "Review the field plan for rabies/vector exposure, PPE, droppings, bats, birds, "
            "protected species, seasonal restrictions, permits, pesticides, ladders, and "
            "electrical hazards. Flag items to verify with New York and local authorities "
            "when the facts are incomplete. Preserve useful plan details."
        ),
    )
    formatter = Agent(
        client=client,
        name="FieldOpsFormatter",
        instructions=(
            "Return ONLY valid JSON with exactly these top-level keys: mode, summary, "
            "recommended_next_steps, estimate_guidance, customer_message, invoice_notes, "
            "safety_flags, legal_or_permit_reminders, confidence. "
            "estimate_guidance must contain suggested_line_items, subtotal_low, "
            "subtotal_high, and pricing_notes. Each line item needs service, qty, "
            "unit_price, and rationale. Use numeric qty and prices. Pricing is guidance, "
            "not a guarantee. Never include markdown or extra commentary."
        ),
    )
    return WorkflowBuilder(start_executor=planner).add_edge(planner, reviewer).add_edge(reviewer, formatter).build()

def output_text(value: Any) -> str:
    text = getattr(value, "text", None)
    if text:
        return str(text)
    return str(value)

def parse_json(value: str) -> dict[str, Any]:
    cleaned = value.strip().replace("```json", "").replace("```", "").strip()
    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError:
        start, end = cleaned.find("{"), cleaned.rfind("}")
        if start < 0 or end <= start:
            raise HTTPException(502, "Agent Framework returned invalid JSON.")
        try:
            parsed = json.loads(cleaned[start:end + 1])
        except json.JSONDecodeError as exc:
            raise HTTPException(502, "Agent Framework returned invalid JSON.") from exc
    if not isinstance(parsed, dict):
        raise HTTPException(502, "Agent Framework returned a non-object result.")
    return parsed

@app.get("/health")
async def health():
    return {"ok": True, "service": "wildlife-fieldops-agent-framework", "framework": "microsoft-agent-framework", "model_configured": bool(setting("AGENT_FRAMEWORK_API_KEY")), "model": model(), "base_url": base_url()}

@app.post("/v1/fieldops/run")
async def run_fieldops(payload: FieldOpsRequest, authorization: str | None = Header(default=None)):
    check_secret(authorization)
    try:
        events = await workflow().run(prompt(payload))
        outputs = events.get_outputs()
        if not outputs:
            raise HTTPException(502, "Agent Framework returned no output.")
        result = parse_json(output_text(outputs[-1]))
        return {"ok": True, "provider": "microsoft-agent-framework", "workflow": "fieldops-plan-review-format-v1", "run_id": str(uuid.uuid4()), "result": result}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, "Agent Framework request failed: " + str(exc)[:500]) from exc
