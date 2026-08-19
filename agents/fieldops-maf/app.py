"""Wildlife FieldOps Microsoft Agent Framework HTTP sidecar.

Run locally:
    pip install -r requirements.txt
    uvicorn app:app --host 0.0.0.0 --port 8088

Android emulator default URL: http://10.0.2.2:8088
"""

from __future__ import annotations

import json
import os
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from agents import (
    COMPLIANCE_INSTRUCTIONS,
    DISPATCHER_INSTRUCTIONS,
    ESTIMATOR_INSTRUCTIONS,
    FIELD_INSTRUCTIONS,
    ORCHESTRATOR_INSTRUCTIONS,
    llm_settings,
    run_maf_agent,
    try_build_maf_agents,
)
from tools import estimate_heuristic, quality_gaps, safety_flags, species_playbook

load_dotenv()

app = FastAPI(title="Wildlife FieldOps Agent Framework", version="5.0.0")
_MAF = try_build_maf_agents()
_SETTINGS = llm_settings()


class RunRequest(BaseModel):
    message: str
    agent: str = "orchestrator"
    species: str = ""
    context: dict[str, Any] = Field(default_factory=dict)


class RunResponse(BaseModel):
    text: str
    agent: str
    backend: str
    tools_used: list[str] = Field(default_factory=list)
    workflow: list[str] = Field(default_factory=list)


def _authorized(authorization: str | None) -> None:
    token = os.getenv("AGENT_API_TOKEN", "").strip()
    if not token:
        return
    expected = f"Bearer {token}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="Invalid agent token")


def _compose_prompt(req: RunRequest) -> str:
    parts = [req.message.strip()]
    if req.species:
        parts.append(f"Species context: {req.species}")
    if req.context:
        parts.append("Job context JSON:\n" + json.dumps(req.context, default=str)[:4000])
    tool_facts = {
        "species_playbook": species_playbook(req.message + " " + req.species),
        "estimate": estimate_heuristic(
            title=str(req.context.get("title") or ""),
            service_type=str(req.context.get("type") or req.context.get("serviceType") or req.species),
            notes=req.message,
            priority=str(req.context.get("priority") or ""),
        ),
        "quality": quality_gaps(req.context),
        "safety": safety_flags(req.message + " " + json.dumps(req.context, default=str)),
    }
    parts.append("Deterministic tool facts:\n" + json.dumps(tool_facts, default=str)[:3500])
    return "\n\n".join(parts)


def _route(agent: str) -> str:
    name = (agent or "orchestrator").strip().lower()
    aliases = {
        "field": "field",
        "fieldtech": "field",
        "tech": "field",
        "estimator": "estimator",
        "price": "estimator",
        "compliance": "compliance",
        "legal": "compliance",
        "dispatcher": "dispatcher",
        "route": "dispatcher",
        "orchestrator": "orchestrator",
        "auto": "orchestrator",
    }
    return aliases.get(name, "orchestrator")


def _system_for(agent: str) -> str:
    return {
        "field": FIELD_INSTRUCTIONS,
        "estimator": ESTIMATOR_INSTRUCTIONS,
        "compliance": COMPLIANCE_INSTRUCTIONS,
        "dispatcher": DISPATCHER_INSTRUCTIONS,
        "orchestrator": ORCHESTRATOR_INSTRUCTIONS,
    }[agent]


async def _openai_fallback(agent: str, prompt: str) -> str:
    if not _SETTINGS["api_key"]:
        playbook = species_playbook(prompt)
        return (
            "Agent Framework sidecar is running without an LLM key.\n"
            "Using deterministic tools only.\n\n"
            + json.dumps(playbook, indent=2)
        )
    from openai import OpenAI

    client = OpenAI(api_key=_SETTINGS["api_key"], base_url=_SETTINGS["base_url"])
    completion = client.chat.completions.create(
        model=_SETTINGS["model"],
        messages=[
            {"role": "system", "content": _system_for(agent).strip()},
            {"role": "user", "content": prompt},
        ],
        temperature=0.3,
        max_tokens=900,
    )
    return (completion.choices[0].message.content or "").strip()


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "ok": True,
        "maf_loaded": bool(_MAF),
        "model": _SETTINGS["model"],
        "base_url": _SETTINGS["base_url"],
        "key_configured": bool(_SETTINGS["api_key"]),
        "agents": list(_MAF.keys()) if _MAF else ["openai-fallback"],
    }


@app.get("/.well-known/agent.json")
def agent_card() -> dict[str, Any]:
    return {
        "name": "Wildlife FieldOps Orchestrator",
        "description": "Microsoft Agent Framework specialists for wildlife exclusion field operations.",
        "version": "5.0.0",
        "protocol": "http-json",
        "skills": [
            {"id": "field", "name": "FieldTech"},
            {"id": "estimator", "name": "Estimator"},
            {"id": "compliance", "name": "Compliance"},
            {"id": "dispatcher", "name": "Dispatcher"},
            {"id": "orchestrator", "name": "FieldOpsOrchestrator"},
        ],
        "endpoints": {"run": "/v1/run"},
    }


@app.post("/v1/run", response_model=RunResponse)
async def run(req: RunRequest, authorization: str | None = Header(default=None)) -> RunResponse:
    _authorized(authorization)
    if not req.message.strip():
        raise HTTPException(status_code=400, detail="message is required")
    agent = _route(req.agent)
    prompt = _compose_prompt(req)
    workflow = ["orchestrator"] if agent == "orchestrator" else [agent]
    tools_used = ["species_playbook", "estimate_heuristic", "quality_gaps", "safety_flags"]

    if _MAF and agent in _MAF:
        try:
            text = await run_maf_agent(_MAF[agent], prompt)
            return RunResponse(
                text=text,
                agent=agent,
                backend="microsoft-agent-framework",
                tools_used=tools_used,
                workflow=workflow,
            )
        except Exception as exc:
            text = await _openai_fallback(agent, prompt + f"\n\n(MAF error fallback: {exc})")
            return RunResponse(
                text=text,
                agent=agent,
                backend="openai-compatible-fallback",
                tools_used=tools_used,
                workflow=workflow,
            )

    text = await _openai_fallback(agent, prompt)
    return RunResponse(
        text=text,
        agent=agent,
        backend="openai-compatible-fallback",
        tools_used=tools_used,
        workflow=workflow,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app:app",
        host=os.getenv("AGENT_HOST", "0.0.0.0"),
        port=int(os.getenv("AGENT_PORT", "8088")),
        reload=False,
    )
