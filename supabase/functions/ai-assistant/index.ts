// supabase/functions/ai-assistant/index.ts
// Wildlife Whisperer FieldOps AI Assistant
// Supports: OpenRouter, Gemini, OpenAI, DeepSeek, Moonshot
// Add your preferred API key to Supabase Edge Function Secrets

type AiMode =
  | "field_plan"
  | "job_notes"
  | "estimate"
  | "customer_message"
  | "invoice_notes"
  | "risk_check";

type AiRequest = {
  mode?: AiMode;
  job?: Record<string, unknown>;
  observation?: string;
  species?: string;
  services?: Array<Record<string, unknown>>;
  inspections?: Array<Record<string, unknown>>;
  businessContext?: string;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

function safeString(value: unknown, max = 6000) {
  return String(value ?? "").slice(0, max);
}

function buildMessages(payload: AiRequest) {
  const mode = payload.mode || "field_plan";

  const system = [
    "You are the Wildlife Whisperer FieldOps AI assistant.",
    "You help a nuisance wildlife removal technician produce practical field notes, estimate guidance, customer messages, and invoice notes.",
    "You are not a lawyer, veterinarian, pesticide label authority, or code-enforcement official.",
    "Do not invent exact legal claims. Give reminders to verify local/state rules, pesticide labels, bat exclusion timing, permits, and protected species requirements.",
    "Prefer concise, job-ready output. Use plain English. Avoid hype.",
    "Pricing should be guidance only and should be framed as a suggested range, not a guaranteed price.",
    "Return ONLY valid JSON. Do not wrap it in markdown. Do not add extra commentary.",
  ].join("\n");

  const user = JSON.stringify(
    {
      requested_mode: mode,
      business_context:
        payload.businessContext ||
        "Small nuisance wildlife removal company. Services include inspection, exclusion, repair, trapping coordination, sanitation, and documentation.",
      job: payload.job || {},
      species: payload.species || payload.job?.species || "",
      observation: safeString(payload.observation || payload.job?.notes || ""),
      services: payload.services || [],
      inspections: payload.inspections || [],
      required_json_shape: {
        mode: "string",
        summary: "string",
        recommended_next_steps: ["string"],
        estimate_guidance: {
          suggested_line_items: [
            {
              service: "string",
              qty: "number",
              unit_price: "number",
              rationale: "string",
            },
          ],
          subtotal_low: "number",
          subtotal_high: "number",
          pricing_notes: "string",
        },
        customer_message: "string",
        invoice_notes: "string",
        safety_flags: ["string"],
        legal_or_permit_reminders: ["string"],
        confidence: "low | medium | high",
      },
    },
    null,
    2,
  );

  return [
    { role: "system", content: system },
    { role: "user", content: user },
  ];
}

function extractJson(text: string) {
  const cleaned = text
    .trim()
    .replace(/^```json\s*/i, "")
    .replace(/^```\s*/i, "")
    .replace(/```$/i, "")
    .trim();

  try {
    return JSON.parse(cleaned);
  } catch {
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start >= 0 && end > start) {
      return JSON.parse(cleaned.slice(start, end + 1));
    }
    throw new Error("AI returned text that was not valid JSON.");
  }
}

function getDemoResult(payload: AiRequest) {
  const species = payload.species || payload.job?.species || "Wildlife";
  const observation = payload.observation || payload.job?.notes || "";

  return {
    mode: payload.mode || "field_plan",
    summary: `Demo mode: ${species} inspection noted. ${observation.slice(0, 60)}...`,
    recommended_next_steps: [
      "Photograph all entry points and damage.",
      "Write detailed inspection notes before pricing.",
      "Check for secondary access points.",
      "Document warranty boundaries with customer.",
      "Schedule follow-up within 48 hours.",
    ],
    estimate_guidance: {
      suggested_line_items: [
        { service: "Inspection", qty: 1, unit_price: 125, rationale: "Required for all jobs" },
        { service: "Exclusion repair", qty: 1, unit_price: 150, rationale: "Seal entry points" },
      ],
      subtotal_low: 275,
      subtotal_high: 450,
      pricing_notes: "Demo estimate. Add a real API key for live AI.",
    },
    customer_message: `Hi, we inspected your property for ${species} activity. We found evidence and recommend exclusion work. We'll send a detailed estimate shortly.`,
    invoice_notes: `Demo invoice notes. Inspection and exclusion work for ${species}.`,
    safety_flags: ["Wear respirator when handling droppings.", "Check for electrical hazards in attic."],
    legal_or_permit_reminders: ["Verify local wildlife regulations.", "Bat exclusions may have seasonal restrictions."],
    confidence: "medium",
  };
}

type AiProvider = {
  name: string;
  apiKey: string;
  baseUrl: string;
  model: string;
};

async function callAgentFramework(payload: AiRequest) {
  const base = Deno.env.get("AGENT_FRAMEWORK_URL")?.trim();
  if (!base) return null;
  const secret = Deno.env.get("AGENT_FRAMEWORK_SHARED_SECRET")?.trim();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (secret) headers.Authorization = "Bearer " + secret;
  const endpoint = base.replace(/\/+$/, "") + "/v1/fieldops/run";
  const response = await fetch(endpoint, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  });
  const raw = await response.text();
  if (!response.ok) {
    throw new Error("Agent Framework error " + response.status + ": " + raw.slice(0, 600));
  }
  const data = JSON.parse(raw);
  if (!data?.ok || !data?.result) throw new Error("Agent Framework returned no result.");
  return data.result;
}
function getProvider(): AiProvider | null {
  const openrouterKey = Deno.env.get("OPENROUTER_API_KEY");
  if (openrouterKey) {
    return {
      name: "openrouter",
      apiKey: openrouterKey,
      baseUrl: "https://openrouter.ai/api/v1",
      model: Deno.env.get("OPENROUTER_MODEL") || "openai/gpt-4o-mini",
    };
  }

  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  if (geminiKey) {
    return {
      name: "gemini",
      apiKey: geminiKey,
      baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai",
      model: Deno.env.get("GEMINI_MODEL") || "gemini-1.5-flash",
    };
  }

  const openaiKey = Deno.env.get("OPENAI_API_KEY");
  if (openaiKey) {
    return {
      name: "openai",
      apiKey: openaiKey,
      baseUrl: Deno.env.get("OPENAI_BASE_URL") || "https://api.openai.com/v1",
      model: Deno.env.get("OPENAI_MODEL") || "gpt-4o-mini",
    };
  }

  const deepseekKey = Deno.env.get("DEEPSEEK_API_KEY");
  if (deepseekKey) {
    return {
      name: "deepseek",
      apiKey: deepseekKey,
      baseUrl: Deno.env.get("DEEPSEEK_BASE_URL") || "https://api.deepseek.com/v1",
      model: Deno.env.get("DEEPSEEK_MODEL") || "deepseek-chat",
    };
  }

  const moonshotKey = Deno.env.get("MOONSHOT_API_KEY") || Deno.env.get("KIMI_API_KEY");
  if (moonshotKey) {
    return {
      name: "kimi_moonshot",
      apiKey: moonshotKey,
      baseUrl: Deno.env.get("MOONSHOT_BASE_URL") || "https://api.moonshot.ai/v1",
      model: Deno.env.get("KIMI_MODEL") || Deno.env.get("MOONSHOT_MODEL") || "kimi-k2-0905-preview",
    };
  }

  console.log("No AI API key found. Returning demo response.");
  return null;
}

async function callAI(payload: AiRequest) {
  const agentResult = await callAgentFramework(payload).catch((error) => {
    console.warn("Agent Framework unavailable; using existing provider:", error);
    return null;
  });
  if (agentResult) return { result: agentResult, provider: "microsoft-agent-framework" };

  const provider = getProvider();

  if (!provider) {
    return { result: getDemoResult(payload), provider: "demo" };
  }

  const body: Record<string, unknown> = {
    model: provider.model,
    messages: buildMessages(payload),
    temperature: 0.2,
    max_tokens: 1600,
  };

  // Only add response_format if the provider supports it
  if (provider.name !== "deepseek") {
    body.response_format = { type: "json_object" };
  }

  const res = await fetch(`${provider.baseUrl.replace(/\/$/, "")}/chat/completions`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${provider.apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const raw = await res.text();

  if (!res.ok) {
    throw new Error(`${provider.name} error ${res.status}: ${raw.slice(0, 1000)}`);
  }

  const data = JSON.parse(raw);
  const content = data?.choices?.[0]?.message?.content;

  if (!content) {
    throw new Error(`${provider.name} returned no message content.`);
  }

  return { result: extractJson(content), provider: provider.name };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed. Use POST." }, 405);
  }

  try {
    const payload = (await req.json()) as AiRequest;

    if (!payload.mode) payload.mode = "field_plan";
    if (!payload.job && !payload.observation && !payload.species) {
      return jsonResponse(
        { error: "Send at least one of: job, observation, or species." },
        400,
      );
    }

    const ai = await callAI(payload);

    return jsonResponse({
      ok: true,
      provider: ai.provider,
      result: ai.result,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error("ai-assistant failed:", message);
    return jsonResponse({ ok: false, error: message }, 500);
  }
});
