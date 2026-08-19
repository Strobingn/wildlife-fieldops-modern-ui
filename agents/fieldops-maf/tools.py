"""Deterministic field tools the Microsoft Agent Framework agents can call."""

from __future__ import annotations

from typing import Any


SPECIES_GUIDANCE: dict[str, dict[str, str]] = {
    "bat": {
        "window": "Dusk through dawn",
        "inspect": "Roofline, ridge vents, gable vents, fascia transitions",
        "note": "Confirm seasonal/legal timing before exclusion; seal secondary gaps first. Never kill. Use one-way valves.",
        "ppe": "N95+ respirator for guano; histoplasmosis risk.",
    },
    "raccoon": {
        "window": "Dusk through early morning",
        "inspect": "Soffits, roof returns, chimneys, decks, crawlspaces",
        "note": "Verify dependent young before eviction or exclusion. Rabies vector.",
        "ppe": "Leather gloves, never handle bare-handed.",
    },
    "squirrel": {
        "window": "Morning and late afternoon",
        "inspect": "Roof edges, dormers, vents, trees contacting the structure",
        "note": "Locate all travel routes before installing one-way devices.",
        "ppe": "Gloves; watch for attic contamination.",
    },
    "skunk": {
        "window": "Dusk through night",
        "inspect": "Decks, sheds, crawlspaces, foundation voids",
        "note": "Low-stress exclusion; verify den status before sealing. Rabies vector.",
        "ppe": "Covered trap; full PPE.",
    },
    "woodchuck": {
        "window": "Morning and late afternoon",
        "inspect": "Burrow network, sheds, decks, gardens, foundation edges",
        "note": "Account for secondary burrow exits and structural undermining.",
        "ppe": "Gloves; watch for collapse risk near foundations.",
    },
    "bird": {
        "window": "Dawn through daylight",
        "inspect": "Vents, soffits, signs, ledges, roof cavities",
        "note": "Confirm nest/egg status and MBTA / protected-species rules. Exclusion only.",
        "ppe": "Eye protection; sanitation PPE if droppings are heavy.",
    },
    "snake": {
        "window": "Warm daylight and dusk",
        "inspect": "Foundation gaps, clutter, rodents, moisture sources",
        "note": "Correct prey and entry conditions. ID before handling; venomous species to DEC.",
        "ppe": "Snake tongs; never bare-handed.",
    },
}


def _blob(*parts: str) -> str:
    return " ".join(p or "" for p in parts).lower()


def detect_species(text: str) -> list[str]:
    found: list[str] = []
    blob = (text or "").lower()
    aliases = {
        "bat": ["bat", "bats", "guano"],
        "raccoon": ["raccoon", "coon"],
        "squirrel": ["squirrel"],
        "skunk": ["skunk"],
        "woodchuck": ["woodchuck", "groundhog"],
        "bird": ["bird", "pigeon", "starling", "sparrow"],
        "snake": ["snake"],
    }
    for species, words in aliases.items():
        if any(w in blob for w in words):
            found.append(species)
    return found


def species_playbook(observation: str) -> dict[str, Any]:
    hits = detect_species(observation)
    if not hits:
        return {
            "species": [],
            "guidance": "No species keyword detected. Confirm signs, droppings, entry points, and sounds before selecting a method.",
        }
    return {
        "species": hits,
        "guidance": [
            {"species": name, **SPECIES_GUIDANCE[name]} for name in hits if name in SPECIES_GUIDANCE
        ],
    }


def estimate_heuristic(
    title: str = "",
    service_type: str = "",
    notes: str = "",
    priority: str = "",
) -> dict[str, Any]:
    blob = _blob(title, service_type, notes)
    hours, materials, equipment, disposal = 2.0, 40.0, 25.0, 0.0
    if "bat" in blob:
        hours, materials, equipment, disposal = 4.0, 180.0, 60.0, 75.0
    elif "raccoon" in blob or "coon" in blob:
        hours, materials, equipment, disposal = 3.0, 90.0, 45.0, 40.0
    elif "squirrel" in blob:
        hours, materials, equipment, disposal = 2.5, 70.0, 30.0, 0.0
    elif "skunk" in blob:
        hours, materials, equipment, disposal = 2.0, 50.0, 35.0, 0.0
    elif "attic" in blob or "guano" in blob or "cleanout" in blob:
        hours, materials, equipment, disposal = 5.0, 120.0, 80.0, 150.0
    elif "exclusion" in blob or "seal" in blob:
        hours, materials, equipment, disposal = 3.5, 140.0, 40.0, 0.0
    elif "dead" in blob:
        hours, materials, equipment, disposal = 1.5, 30.0, 0.0, 60.0
    elif "inspect" in blob:
        hours, materials, equipment, disposal = 1.0, 0.0, 0.0, 0.0
    if "urgent" in priority.lower() or "high" in priority.lower():
        hours += 0.5
    rate = 85.0
    labor = hours * rate
    subtotal = labor + materials + equipment + disposal
    return {
        "laborHours": hours,
        "laborRate": rate,
        "materialsCost": materials,
        "equipmentCost": equipment,
        "disposalCost": disposal,
        "subtotalBeforeTax": round(subtotal, 2),
        "rationale": "Heuristic draft from service keywords. Technician must verify access, multi-entry, and return visits.",
    }


def quality_gaps(job: dict[str, Any] | None) -> dict[str, Any]:
    job = job or {}
    missing: list[str] = []
    if not str(job.get("address") or "").strip():
        missing.append("address")
    if not str(job.get("customerName") or job.get("customer") or "").strip():
        missing.append("customer")
    if not str(job.get("description") or "").strip():
        missing.append("description")
    if float(job.get("estimatedValue") or 0) <= 0:
        missing.append("estimate")
    if not str(job.get("type") or job.get("serviceType") or "").strip():
        missing.append("service type")
    status = str(job.get("status") or "").upper()
    if status in {"COMPLETED", "PAID"} and float(job.get("actualCost") or 0) <= 0:
        missing.append("actual cost")
    score = max(0, 100 - len(missing) * 14)
    return {"score": score, "missing": missing}


def safety_flags(text: str) -> list[str]:
    blob = (text or "").lower()
    flags: list[str] = []
    checks = [
        ("rabies", "Rabies-vector species language present — PPE and no bare-handed handling."),
        ("bat", "Protected-species / exclusion-only path. Confirm season and one-way devices."),
        ("guano", "Histoplasmosis risk — respirator and containment."),
        ("young", "Possible dependent young — verify life stage before exclusion."),
        ("babies", "Possible dependent young — verify life stage before exclusion."),
        ("nest", "Possible nest/maternity — legal method check required."),
        ("roof", "Elevated work — ladder/fall protection plan."),
        ("ladder", "Elevated work — ladder/fall protection plan."),
        ("chimney", "Confined/elevated access — second-tech and lighting check."),
        ("bite", "Bite protocol: wash 15 minutes and seek medical care."),
    ]
    seen: set[str] = set()
    for word, flag in checks:
        if word in blob and flag not in seen:
            flags.append(flag)
            seen.add(flag)
    return flags
