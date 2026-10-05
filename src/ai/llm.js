import { supabase } from "../auth/supabaseClient.js";

export async function getSuggestionsFromLLM(species, season, observations) {
  const prompt = `You are an expert wildlife and pest control field inspector specializing in raccoons in attics, chimneys, and structures.
Species: ${species}
Season: ${season}
Observations: ${observations || "No additional observations provided."}

Provide a concise, actionable inspection hint covering attic/chimney/latrine patterns, rub marks, nesting zones, insulation damage, entry points, and safety notes. Keep it under 180 words, professional and field-ready.`;

  try {
    const { data, error } = await supabase.functions.invoke("ai-assistant", {
      body: {
        action: "complete",
        system: "You are an expert wildlife and pest control field inspector.",
        user: prompt,
        maxTokens: 400,
        temperature: 0.4,
      },
    });
    if (error) {
      return `Cloud AI unavailable (${error.message || "request failed"}). Enter the inspection notes manually.`;
    }
    if (!data?.ok || !data?.text) {
      return data?.error || "Cloud AI unavailable. Enter the inspection notes manually.";
    }
    return String(data.text).trim();
  } catch (error) {
    console.error("LLM call failed", error);
    return "Failed to reach cloud AI. Enter the inspection notes manually.";
  }
}
