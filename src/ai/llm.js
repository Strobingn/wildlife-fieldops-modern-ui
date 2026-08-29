import { getApiKey } from '../config.js'; // your config that reads from env or local

export async function getSuggestionsFromLLM(species, season, observations) {
  const apiKey = getApiKey('LLM_API_KEY'); // implement this to read from .env or BuildConfig equivalent
  if (!apiKey) {
    return 'Add your LLM_API_KEY (xAI or OpenAI) in your config/.env';
  }

  const prompt = `You are an expert wildlife and pest control field inspector specializing in raccoons in attics, chimneys, and structures.
Species: ${species}
Season: ${season}
Observations: ${observations || 'No additional observations provided.'}

Provide a concise, actionable inspection hint covering attic/chimney/latrine patterns, rub marks, nesting zones, insulation damage, entry points, and safety notes. Keep it under 180 words, professional and field-ready.`;

  try {
    const response = await fetch('https://api.x.ai/v1/chat/completions', {
      // or https://api.openai.com/v1/chat/completions
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        model: 'grok-3', // or 'gpt-4o-mini'
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 400,
        temperature: 0.4
      })
    });

    if (!response.ok) {
      const err = await response.text();
      return `LLM error: ${response.status} - ${err}`;
    }

    const data = await response.json();
    return data.choices?.[0]?.message?.content?.trim() || 'No suggestion generated.';
  } catch (error) {
    console.error('LLM call failed', error);
    return 'Failed to get suggestions from LLM. Check your API key and internet.';
  }
}
