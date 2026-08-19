"""Microsoft Agent Framework specialists for Wildlife FieldOps."""

from __future__ import annotations

import os
from typing import Any, Callable

from tools import estimate_heuristic, quality_gaps, safety_flags, species_playbook

FIELD_INSTRUCTIONS = """
You are FieldTech, a licensed-minded wildlife exclusion technician for Hudson Valley / New York field work.
Exclusion first. Never invent illegal kill methods. Prefer one-way devices, sealing, sanitation, and documented evidence.
Be concise and phone-readable. Use bullets. Flag PPE and legal timing.
"""

ESTIMATOR_INSTRUCTIONS = """
You are Estimator for a nuisance wildlife business.
Return realistic US pricing with labor hours, materials, equipment, disposal, and rationale.
Do not invent permits that were not mentioned. Call out missing scope.
"""

COMPLIANCE_INSTRUCTIONS = """
You are Compliance for wildlife control in New York / US.
Watch MBTA, bat season, rabies vectors, dependent young, histoplasmosis, and customer warnings.
Never claim a method is legal in every state. Ask for the state if missing.
"""

DISPATCHER_INSTRUCTIONS = """
You are Dispatcher for FieldOps.
Prioritize overdue, urgent, indoor living-space, and high-value jobs.
Ask for address, assignment, and access blockers before sending a tech.
"""

ORCHESTRATOR_INSTRUCTIONS = """
You are FieldOps Orchestrator using Microsoft Agent Framework patterns.
Route work:
- species, trapping, exclusion, photos, PPE -> FieldTech
- price, estimate, invoice language -> Estimator
- law, season, protected species, waste -> Compliance
- schedule, route, assignment -> Dispatcher
Combine answers into one field-ready brief. Do not invent job facts.
"""


def llm_settings() -> dict[str, str]:
    key = (
        os.getenv("XAI_API_KEY")
        or os.getenv("OPENAI_API_KEY")
        or os.getenv("LLM_API_KEY")
        or ""
    ).strip()
    base = (os.getenv("OPENAI_BASE_URL") or os.getenv("XAI_BASE_URL") or "https://api.x.ai/v1").rstrip("/")
    model = os.getenv("OPENAI_MODEL") or os.getenv("XAI_MODEL") or os.getenv("LLM_MODEL") or "grok-4.5"
    return {"api_key": key, "base_url": base, "model": model}


def _bind_tools() -> list[Callable[..., Any]]:
    return [species_playbook, estimate_heuristic, quality_gaps, safety_flags]


def try_build_maf_agents() -> dict[str, Any] | None:
    """Build MAF agents when the package is installed. Return None if API differs."""
    settings = llm_settings()
    if not settings["api_key"]:
        return None
    try:
        from agent_framework import Agent
        from agent_framework.openai import OpenAIChatClient
    except Exception:
        return None

    client = OpenAIChatClient(
        api_key=settings["api_key"],
        base_url=settings["base_url"],
        model_id=settings["model"],
    )
    tools = _bind_tools()
    specs = {
        "field": ( "FieldTech", FIELD_INSTRUCTIONS),
        "estimator": ("Estimator", ESTIMATOR_INSTRUCTIONS),
        "compliance": ("Compliance", COMPLIANCE_INSTRUCTIONS),
        "dispatcher": ("Dispatcher", DISPATCHER_INSTRUCTIONS),
        "orchestrator": ("FieldOpsOrchestrator", ORCHESTRATOR_INSTRUCTIONS),
    }
    built: dict[str, Any] = {}
    for key, (name, instructions) in specs.items():
        try:
            built[key] = Agent(
                client=client,
                name=name,
                instructions=instructions.strip(),
                tools=tools,
            )
        except TypeError:
            built[key] = Agent(
                chat_client=client,
                name=name,
                instructions=instructions.strip(),
                tools=tools,
            )
    return built


async def run_maf_agent(agent: Any, prompt: str) -> str:
    result = await agent.run(prompt)
    for attr in ("text", "output", "content"):
        value = getattr(result, attr, None)
        if isinstance(value, str) and value.strip():
            return value.strip()
    if hasattr(result, "messages"):
        messages = getattr(result, "messages") or []
        if messages:
            last = messages[-1]
            for attr in ("text", "content"):
                value = getattr(last, attr, None)
                if isinstance(value, str) and value.strip():
                    return value.strip()
    return str(result)
