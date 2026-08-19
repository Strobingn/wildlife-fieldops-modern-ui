# Wildlife FieldOps Agent Framework service

This private FastAPI service hosts the Microsoft Agent Framework workflow used by the native Android app.

Workflow:

1. FieldPlanner turns job observations into a practical field plan.
2. SafetyAndComplianceReviewer checks hazards, PPE, protected species, seasonal restrictions, and permits.
3. FieldOpsFormatter returns the JSON shape already consumed by the Supabase ai-assistant function.

## Run locally

Create a virtual environment, install requirements, copy .env.example to .env, and start the service:

    pip install -r requirements.txt
    uvicorn app:app --reload --port 8080

Required environment variables:

- AGENT_FRAMEWORK_API_KEY: model provider key, kept on this service host
- AGENT_FRAMEWORK_BASE_URL: OpenAI-compatible endpoint, defaulting to xAI
- AGENT_FRAMEWORK_MODEL: model name
- AGENT_FRAMEWORK_SHARED_SECRET: optional shared secret for the Supabase gateway

Deploy this service behind private HTTPS. Then add AGENT_FRAMEWORK_URL and AGENT_FRAMEWORK_SHARED_SECRET to the Supabase Edge Function secrets. The Android app calls Supabase only; do not put the service secret or model key in the APK.

Health check: GET /health
Run endpoint: POST /v1/fieldops/run
