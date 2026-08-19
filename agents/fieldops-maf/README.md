# FieldOps Microsoft Agent Framework sidecar

This is the GrokAIV5 integration of [microsoft/agent-framework](https://github.com/microsoft/agent-framework).

The Android app stays offline-first. This Python service is an optional multi-agent backend:

- `FieldTech` — exclusion, trapping, species, PPE
- `Estimator` — labor/materials drafts
- `Compliance` — NY / US legal and seasonal checks
- `Dispatcher` — schedule and route priority
- `FieldOpsOrchestrator` — routes the request and returns one field brief

## Run

```bash
cd agents/fieldops-maf
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# set XAI_API_KEY
uvicorn app:app --host 0.0.0.0 --port 8088
```

Phone on the same LAN: `http://YOUR_LAN_IP:8088`  
Emulator: `http://10.0.2.2:8088`

Bake the URL into the APK:

```bash
export AGENT_FRAMEWORK_URL=http://10.0.2.2:8088
./gradlew :app:assembleDebug
```

## Contract

`POST /v1/run`

```json
{
  "message": "Bat in the attic, ridge vent, July",
  "agent": "orchestrator",
  "species": "bat",
  "context": { "title": "Ridge vent bats", "priority": "HIGH" }
}
```

`GET /health` and `GET /.well-known/agent.json` are used by the Android client for discovery.
