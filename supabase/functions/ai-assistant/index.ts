// supabase/functions/ai-assistant/index.ts
// Wildlife FieldOps AI. The xAI key is a Supabase secret (XAI_API_KEY), never a client secret.
// Deployed behavior (structured modes, optional Agent Framework, ai_runs) stays.
// Android chat/estimate/report calls use action "complete" and receive raw text.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type AiMode =
  | "field_plan"
  | "job_notes"
  | "estimate"
  | "customer_message"
  | "invoice_notes"
  | "risk_check"
  | "photo_inspection"
  | "business_query";

type ChatMessage = { role?: string; content?: string };

type AiRequest = {
  action?: string;
  mode?: AiMode;
  job?: Record<string, unknown>;
  observation?: string;
  species?: string;
  services?: Array<Record<string, unknown>>;
  inspections?: Array<Record<string, unknown>>;
  businessContext?: string;
  imageUrl?: string;
  question?: string;
  system?: string;
  user?: string;
  prompt?: string;
  messages?: ChatMessage[];
  maxTokens?: number;
  max_tokens?: number;
  temperature?: number;
  jsonMode?: boolean;
  json_mode?: boolean;
  imageDataUrl?: string;
  /** Optional. Older app builds omit this. Vision still runs. */
  detectionExamples?: unknown;
};

type DetectionExample = {
  label: string;
  description: string;
  verdict: string;
  modelLabel: string;
  thumbnailDataUrl: string;
};

const FEW_SHOT_MARKER = "Confirmed field corrections:";

type Provider = { name: string; key: string; baseUrl: string; model: string };

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function publicError(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  return message
    .replace(/Bearer\s+\S+/gi, "Bearer [redacted]")
    .replace(/xai-[A-Za-z0-9_\-]+/g, "[redacted]")
    .replace(/sk-[A-Za-z0-9_\-]+/g, "[redacted]")
    .slice(0, 500);
}

function safeString(value: unknown, max: number) {
  return String(value ?? "").slice(0, max);
}

function isCompleteAction(payload: AiRequest): boolean {
  const action = (payload.action ?? "").trim().toLowerCase();
  return action === "complete" || action === "chat";
}

function completeMessages(payload: AiRequest): Array<{ role: string; content: string }> | null {
  if (Array.isArray(payload.messages) && payload.messages.length > 0) {
    const messages = payload.messages
      .filter((message) => message && typeof message.content === "string" && typeof message.role === "string")
      .slice(0, 8)
      .map((message) => ({
        role: safeString(message.role, 20),
        content: safeString(message.content, 12000),
      }))
      .filter((message) => message.role.length > 0 && message.content.length > 0);
    return messages.length > 0 ? messages : null;
  }
  const system = safeString(payload.system, 8000).trim();
  const user = safeString(payload.user ?? payload.prompt, 12000).trim();
  const messages: Array<{ role: string; content: string }> = [];
  if (system) messages.push({ role: "system", content: system });
  if (user) messages.push({ role: "user", content: user });
  return messages.length > 0 ? messages : null;
}

const FAST_XAI_MODEL = "grok-4.20-0309-non-reasoning";
const SLOW_XAI_MODELS = new Set(["grok-4", "grok-4-latest", "grok-4-0709"]);

// Fast non-reasoning model by default; a configured slow reasoning model is ignored.
function xaiModel(): string {
  const configured = (Deno.env.get("XAI_MODEL") || Deno.env.get("LLM_MODEL") || "").trim();
  if (!configured) return FAST_XAI_MODEL;
  const lower = configured.toLowerCase();
  if (SLOW_XAI_MODELS.has(lower) || (lower.includes("reasoning") && !lower.includes("non-reasoning"))) return FAST_XAI_MODEL;
  return configured;
}

function provider(): Provider {
  const xaiKey = Deno.env.get("XAI_API_KEY") || Deno.env.get("GROK_API_KEY") || Deno.env.get("LLM_API_KEY");
  if (xaiKey) {
    return {
      name: "xai",
      key: xaiKey,
      baseUrl: Deno.env.get("XAI_BASE_URL") || "https://api.x.ai/v1",
      model: xaiModel(),
    };
  }
  const openaiKey = Deno.env.get("OPENAI_API_KEY");
  if (openaiKey) {
    return {
      name: "openai",
      key: openaiKey,
      baseUrl: Deno.env.get("OPENAI_BASE_URL") || "https://api.openai.com/v1",
      model: Deno.env.get("OPENAI_MODEL") || "gpt-4o-mini",
    };
  }
  const openrouterKey = Deno.env.get("OPENROUTER_API_KEY");
  if (openrouterKey) {
    return {
      name: "openrouter",
      key: openrouterKey,
      baseUrl: "https://openrouter.ai/api/v1",
      model: Deno.env.get("OPENROUTER_MODEL") || "openai/gpt-4o-mini",
    };
  }
  throw new Error(
    "No AI provider secret is configured in Supabase. Add XAI_API_KEY (preferred), GROK_API_KEY, OPENAI_API_KEY, or OPENROUTER_API_KEY.",
  );
}

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

function parseJson(text: string) {
  const cleaned = text.trim().replace(/^```json\s*/i, "").replace(/^```\s*/i, "").replace(/```$/i, "").trim();
  try {
    return JSON.parse(cleaned);
  } catch {
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
  return {
    question,
    snapshot: snapshot.data?.[0] ?? null,
    jobs: jobs.data ?? [],
    callbacks: callbacks.data ?? [],
    inventoryAlerts: inventory.data ?? [],
  };
}

async function chatCompletion(
  selected: Provider,
  messages: Array<{ role: string; content: unknown }>,
  temperature: number,
  maxTokens: number,
  jsonMode: boolean,
): Promise<string> {
  const body: Record<string, unknown> = {
    model: selected.model,
    messages,
    temperature,
    max_tokens: maxTokens,
  };
  if (jsonMode) body.response_format = { type: "json_object" };
  const response = await fetch(`${selected.baseUrl.replace(/\/$/, "")}/chat/completions`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${selected.key}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  const raw = await response.text();
  if (!response.ok) {
    throw new Error(`${selected.name} HTTP ${response.status}: ${raw.slice(0, 600)}`);
  }
  const decoded = JSON.parse(raw);
  const content = decoded?.choices?.[0]?.message?.content;
  if (!content || typeof content !== "string") {
    throw new Error("AI provider returned no message content");
  }
  return content;
}

function sanitizeDetectionExamples(raw: unknown): DetectionExample[] {
  if (!Array.isArray(raw)) return [];
  const out: DetectionExample[] = [];
  for (const item of raw.slice(0, 6)) {
    if (!item || typeof item !== "object") continue;
    const row = item as Record<string, unknown>;
    const label = safeString(row.label ?? row.correctedLabel ?? row.corrected_label, 80).trim();
    const description = safeString(row.description ?? row.whatImageShows ?? row.what_image_shows ?? row.notes, 240).trim();
    const verdict = safeString(row.verdict, 20).trim();
    const modelLabel = safeString(row.modelLabel ?? row.model_label, 80).trim();
    const thumbnailDataUrl = safeString(row.thumbnailDataUrl ?? row.thumbnail_data_url, 40_000).trim();
    if (!label && !description && !modelLabel) continue;
    out.push({ label, description, verdict, modelLabel, thumbnailDataUrl });
  }
  return out;
}

function fewShotText(examples: DetectionExample[]): string {
  const lines = examples.map((example) => {
    const shown = example.label || example.modelLabel || "unlabeled";
    const model = example.modelLabel ? `Model said "${example.modelLabel}". ` : "";
    const verdict = example.verdict ? `Verdict ${example.verdict}. ` : "";
    const desc = example.description ? ` Shows: ${example.description}.` : "";
    return `- ${model}${verdict}Correct label: "${shown}".${desc}`;
  });
  return [
    FEW_SHOT_MARKER,
    "A wildlife technician confirmed or corrected these photo findings. When the new photo is similar, prefer the correct label.",
    "Entry points include gap, vent, soffit, roof return, chimney, gable vent, and foundation crack.",
    "If the correction is an entry point, keep species on the allowed list and name the entry in evidence_notes.",
    ...lines,
  ].join("\n");
}

function mergeDetectionExamples(clientRows: DetectionExample[], stored: DetectionExample[]): DetectionExample[] {
  const seen = new Set<string>();
  const out: DetectionExample[] = [];
  for (const row of [...clientRows, ...stored]) {
    const key = `${row.verdict}|${row.modelLabel}|${row.label}|${row.description}`.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(row);
    if (out.length >= 6) break;
  }
  return out;
}

async function loadStoredDetectionExamples(): Promise<DetectionExample[]> {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !key) return [];
  try {
    const client = createClient(url, key, { auth: { persistSession: false } });
    const { data, error } = await client
      .from("ai_detection_feedback")
      .select("verdict,corrected_label,model_label,what_image_shows,notes")
      .in("verdict", ["YES", "NO", "PARTLY"])
      .order("created_at", { ascending: false })
      .limit(6);
    if (error || !data) return [];
    return sanitizeDetectionExamples(data.map((row: Record<string, unknown>) => ({
      label: row.corrected_label || row.model_label,
      modelLabel: row.model_label,
      description: row.what_image_shows || row.notes,
      verdict: row.verdict,
    })));
  } catch {
    return [];
  }
}

function appendFewShot(
  messages: Array<{ role: string; content: unknown }>,
  examples: DetectionExample[],
): Array<{ role: string; content: unknown }> {
  if (examples.length === 0) return messages;
  const already = messages.some((message) =>
    message.role === "system" && typeof message.content === "string" && message.content.includes(FEW_SHOT_MARKER)
  );
  const withText = already ? messages : [{ role: "system", content: fewShotText(examples) }, ...messages];
  const thumbPattern = /^data:image\/(jpeg|jpg|png|webp);base64,[a-z0-9+/=\r\n]+$/i;
  let budget = 120_000;
  let used = 0;
  const parts: Array<Record<string, unknown>> = [
    { type: "text", text: "Example photos from confirmed corrections. Use them only as similar cases." },
  ];
  for (const example of examples) {
    const url = example.thumbnailDataUrl;
    if (used >= 2 || !url || url.length > 40_000 || url.length > budget || !thumbPattern.test(url)) continue;
    used += 1;
    budget -= url.length;
    parts.push({ type: "image_url", image_url: { url } });
  }
  if (parts.length === 1) return withText;
  return [...withText, { role: "user", content: parts }];
}

function visionMessages(
  messages: Array<{ role: string; content: string }>,
  image: string,
): Array<{ role: string; content: unknown }> {
  let attached = false;
  return messages.map((message) => {
    if (attached || message.role !== "user") return message;
    attached = true;
    return {
      role: "user",
      content: [
        { type: "text", text: message.content },
        { type: "image_url", image_url: { url: image } },
      ],
    };
  });
}

async function handleComplete(payload: AiRequest): Promise<Response> {
  const messages = completeMessages(payload);
  if (!messages) {
    return jsonResponse({ ok: false, error: "Send system, user, or messages." }, 400);
  }
  const image = String(payload.imageDataUrl ?? payload.imageUrl ?? "").trim();
  let outbound: Array<{ role: string; content: unknown }> = messages;
  if (image) {
    if (image.length > 1_500_000) {
      return jsonResponse({ ok: false, error: "Photo is too large for species ID." }, 400);
    }
    // Data URLs only. A remote http(s) image would make the provider fetch an attacker-chosen URL.
    const usable = /^data:image\/(jpeg|jpg|png|webp);base64,[a-z0-9+/=\r\n]+$/i.test(image);
    if (!usable) {
      return jsonResponse({ ok: false, error: "Species photo must be a JPEG, PNG, or WebP data URL." }, 400);
    }
    outbound = visionMessages(messages, image);
    const examples = mergeDetectionExamples(
      sanitizeDetectionExamples(payload.detectionExamples),
      await loadStoredDetectionExamples(),
    );
    outbound = appendFewShot(outbound, examples);
  }
  const selected = provider();
  const requestedTokens = Number(payload.maxTokens ?? payload.max_tokens ?? 900);
  const maxTokens = Number.isFinite(requestedTokens) ? Math.min(2000, Math.max(1, Math.round(requestedTokens))) : 900;
  const requestedTemperature = Number(payload.temperature ?? 0.2);
  const temperature = Number.isFinite(requestedTemperature)
    ? Math.min(1, Math.max(0, requestedTemperature))
    : 0.2;
  const jsonMode = payload.jsonMode === true || payload.json_mode === true;
  const text = await chatCompletion(selected, outbound, temperature, maxTokens, jsonMode);
  return jsonResponse({
    ok: true,
    provider: selected.name,
    model: selected.model,
    text,
    vision: image.length > 0,
  });
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse({ ok: false, error: "Method not allowed. Use POST." }, 405);
  try {
    const payload = await request.json() as AiRequest;
    if (isCompleteAction(payload)) {
      return await handleComplete(payload);
    }

    let agentResult = null;
    try {
      agentResult = await callAgentFramework(payload);
    } catch (error) {
      console.warn("Agent Framework unavailable; using existing provider:", publicError(error));
    }
    if (agentResult) {
      return jsonResponse({ ok: true, provider: "microsoft-agent-framework", result: agentResult });
    }

    const selected = provider();
    const authorization = request.headers.get("Authorization") ?? "";
    const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: authorization } },
    });
    const mode = payload.mode ?? "field_plan";
    if (!payload.job && !payload.observation && !payload.species && !payload.imageUrl && !payload.question) {
      throw new Error("Provide job, observation, species, imageUrl, or question");
    }

    const context = mode === "business_query"
      ? await businessContext(client, payload.question ?? "Summarize business performance")
      : {
        job: payload.job ?? {},
        observation: payload.observation ?? "",
        species: payload.species ?? "",
        services: payload.services ?? [],
        inspections: payload.inspections ?? [],
        businessContext: payload.businessContext ?? "",
      };

    const storedExamples = payload.imageUrl
      ? mergeDetectionExamples(
        sanitizeDetectionExamples(payload.detectionExamples),
        await loadStoredDetectionExamples(),
      )
      : [];
    const fewShot = storedExamples.length > 0 ? "\n" + fewShotText(storedExamples) : "";
    const system = [
      "You are Wildlife FieldOps AI for a professional nuisance-wildlife company.",
      "Return only valid JSON.",
      "Do not invent laws, measurements, species certainty, prices, or completed work.",
      "Flag uncertainty and require technician confirmation for image findings.",
      "For compliance, identify what must be verified against the cited agency source.",
      "For estimates, calculate transparent line items and explain assumptions.",
      "For business queries, answer only from the supplied database context.",
      fewShot,
    ].filter((line) => line.length > 0).join("\n");

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

    const content = await chatCompletion(
      selected,
      [{ role: "system", content: system }, { role: "user", content: userContent }],
      0.15,
      2200,
      true,
    );
    const result = parseJson(content);

    await client.from("ai_runs").insert({
      job_id: payload.job && typeof payload.job.id === "string" ? payload.job.id : null,
      mode,
      input: payload,
      output: result,
      provider: selected.name,
    });

    return jsonResponse({ ok: true, provider: selected.name, model: selected.model, result });
  } catch (error) {
    console.error("ai-assistant failed:", publicError(error));
    return jsonResponse({ ok: false, error: publicError(error) }, 500);
  }
});
