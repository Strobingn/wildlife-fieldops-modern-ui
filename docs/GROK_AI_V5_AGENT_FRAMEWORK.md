# GrokAIV5 — Microsoft Agent Framework in Wildlife FieldOps

Branch: `GrokAIV5`  
Source: https://github.com/microsoft/agent-framework

## Why a sidecar

Microsoft Agent Framework is Python / .NET. It does not ship an Android runtime.
The field app therefore keeps:

1. Room + `AIOperationsEngine` for offline facts
2. Existing xAI Grok chat in `AiService`
3. A new optional HTTP orchestrator that runs MAF specialists

## What landed

| Layer | Path | Role |
|---|---|---|
| MAF service | `agents/fieldops-maf/` | FieldTech, Estimator, Compliance, Dispatcher, Orchestrator |
| Android client | `ai/AgentFrameworkClient.kt` | Health check + `/v1/run` |
| Chat path | `AiService.ask()` | Tries MAF first, then Grok, then offline knowledge |
| Build flag | `AGENT_FRAMEWORK_URL` | Empty = current behavior |

## Default behavior

If `AGENT_FRAMEWORK_URL` is blank, the app is unchanged.

If it is set, chat goes through the orchestrator first.
If the sidecar is down, `AiService` falls back to the existing Grok / offline path.

## Local loop

1. Start `agents/fieldops-maf` on port 8088
2. Rebuild with `AGENT_FRAMEWORK_URL` (emulator: `http://10.0.2.2:8088`)
3. Open AI Assistant and ask a species or estimate question
4. Reply footer will mention `microsoft-agent-framework` when MAF handled it

## Agents

- FieldTech — exclusion, trapping, species, PPE
- Estimator — labor/materials drafts
- Compliance — NY / US legal and seasonal checks
- Dispatcher — schedule and route priority
- FieldOpsOrchestrator — routes the request and returns one field brief
