import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type AiMode = "field_plan" | "job_notes" | "estimate" | "customer_message" | "invoice_notes" | "risk_check" | "photo_inspection" | "business_query";
type AiRequest = {
  mode?: AiMode;
  job?: Record<string, unknown>;
  observation?: string;
  species?: string;
  services?: Array<Record<string, unknown>>;
  inspections?: Array<Record<string, unknown>>;
  businessContext?: string;
  imageUrl?: string;
  question?: string;
};

type Provider = { name: string; key: string; baseUrl: string; model: string };
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

async function callAgentFramework(payload: AiRequest) {
  const base = Deno.env.get("AGENT_FRAMEWORK_URL")?.trim();
  if (!base) return null;
  const secret = Deno.env.get("AGENT_FRAMEWORK_SHARED_SECRET")?.trim();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (secret) headers.Authorization = "Bearer " + secret;
  const endpoint = base.replace(/\/+$/, "") + "/v1/fieldops/run";
  const response = await fetch(endpoint, { method: "POST", headers, body: JSON.stringify(payload) });
  const raw = await response.text();
  if (!response.ok) throw new Error("Agent Framework error " + response.status + ": " + raw.slice(0, 600));
  const data = JSON.parse(raw);
  if (!data?.ok || !data?.result) throw new Error("Agent Framework returned no result.");
  return data.result;
}
function provider(): Provider {
  const xaiKey = Deno.env.get("XAI_API_KEY") || Deno.env.get("GROK_API_KEY");
  if (xaiKey) return {
    name: "xai",
    key: xaiKey,
    baseUrl: Deno.env.get("XAI_BASE_URL") || "https://api.x.ai/v1",
    model: Deno.env.get("XAI_MODEL") || Deno.env.get("LLM_MODEL") || "grok-4-latest",
  };
  const openaiKey = Deno.env.get("OPENAI_API_KEY");
  if (openaiKey) return {
    name: "openai",
    key: openaiKey,
    baseUrl: Deno.env.get("OPENAI_BASE_URL") || "https://api.openai.com/v1",
    model: Deno.env.get("OPENAI_MODEL") || "gpt-4o-mini",
  };
  const openrouterKey = Deno.env.get("OPENROUTER_API_KEY");
  if (openrouterKey) return {
    name: "openrouter",
    key: openrouterKey,
    baseUrl: "https://openrouter.ai/api/v1",
    model: Deno.env.get("OPENROUTER_MODEL") || "openai/gpt-4o-mini",
  };
  throw new Error("No AI provider secret is configured in Supabase. Add XAI_API_KEY, GROK_API_KEY, OPENAI_API_KEY, or OPENROUTER_API_KEY.");
}

function parseJson(text: string) {
  const cleaned = text.trim().replace(/^```json\s*/i, "").replace(/^```\s*/i, "").replace(/```$/i, "").trim();
  try { return JSON.parse(cleaned); } catch {
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start >= 0 && end > start) return JSON.parse(cleaned.slice(start, end + 1));
    throw new Error("AI response was not valid JSON");
  }
}

async function businessContext(client: ReturnType<typeof createClient>, question: string) {
  const [snapshot, jobs, callbacks, inventory] = await Promise.all([
    client.from("business_snapshot_v2").select("*").limit(1),
    client.from("jobs").select("id,title,status,species,customer_name,grand_total,scheduled_start,completed_at").order("created_at", { ascending: false }).limit(50),
    client.from("callbacks").select("reason,status,cost,created_at").order("created_at", { ascending: false }).limit(30),
    client.from("inventory_alerts").select("name,quantity,reorder_level,shortage").limit(30),
  ]);
  return { question, snapshot: snapshot.data?.[0] ?? null, jobs: jobs.data ?? [], callbacks: callbacks.data ?? [], inventoryAlerts: inventory.data ?? [] };
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const payload = await request.json() as AiRequest;
    let agentResult = null;
    try {
      agentResult = await callAgentFramework(payload);
    } catch (error) {
      console.warn("Agent Framework unavailable; using existing provider:", error);
    }
    if (agentResult) {
      return new Response(JSON.stringify({ ok: true, provider: "microsoft-agent-framework", result: agentResult }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const selected = provider();
    const authorization = request.headers.get("Authorization") ?? "";
    const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: authorization } } });
    const mode = payload.mode ?? "field_plan";
    if (!payload.job && !payload.observation && !payload.species && !payload.imageUrl && !payload.question) {
      throw new Error("Provide job, observation, species, imageUrl, or question");
    }

    const context = mode === "business_query"
      ? await businessContext(client, payload.question ?? "Summarize business performance")
      : {
          job: payload.job ?? {}, observation: payload.observation ?? "", species: payload.species ?? "",
          services: payload.services ?? [], inspections: payload.inspections ?? [], businessContext: payload.businessContext ?? "",
        };

    const system = [
      "You are Wildlife FieldOps AI for a professional nuisance-wildlife company.",
      "Return only valid JSON.",
      "Do not invent laws, measurements, species certainty, prices, or completed work.",
      "Flag uncertainty and require technician confirmation for image findings.",
      "For compliance, identify what must be verified against the cited agency source.",
      "For estimates, calculate transparent line items and explain assumptions.",
      "For business queries, answer only from the supplied database context.",
    ].join("\n");

    const textPrompt = JSON.stringify({
      mode,
      context,
      requiredShape: {
        summary: "string",
        species: "string or null",
        confidence: "number 0..1",
        entryPoints: ["string"],
        damage: ["string"],
        recommendations: ["string"],
        safetyFlags: ["string"],
        complianceChecks: ["string"],
        estimateLineItems: [{ service: "string", quantity: 0, unitPrice: 0, rationale: "string" }],
        customerMessage: "string",
        invoiceNotes: "string",
        answer: "string",
      },
    });

    const userContent: unknown = payload.imageUrl
      ? [
          { type: "text", text: textPrompt },
          { type: "image_url", image_url: { url: payload.imageUrl } },
        ]
      : textPrompt;

    const response = await fetch(`${selected.baseUrl.replace(/\/$/, "")}/chat/completions`, {
      method: "POST",
      headers: { Authorization: `Bearer ${selected.key}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: selected.model,
        messages: [{ role: "system", content: system }, { role: "user", content: userContent }],
        temperature: 0.15,
        max_tokens: 2200,
        response_format: { type: "json_object" },
      }),
    });
    const raw = await response.text();
    if (!response.ok) throw new Error(`${selected.name} HTTP ${response.status}: ${raw.slice(0, 600)}`);
    const decoded = JSON.parse(raw);
    const content = decoded?.choices?.[0]?.message?.content;
    if (!content) throw new Error("AI provider returned no message content");
    const result = parseJson(content);

    await client.from("ai_runs").insert({
      job_id: payload.job && typeof payload.job.id === "string" ? payload.job.id : null,
      mode,
      input: payload,
      output: result,
      provider: selected.name,
    });

    return new Response(JSON.stringify({ ok: true, provider: selected.name, model: selected.model, result }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    return new Response(JSON.stringify({ ok: false, error: error instanceof Error ? error.message : String(error) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});