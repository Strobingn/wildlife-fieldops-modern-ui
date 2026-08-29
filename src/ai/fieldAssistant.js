// src/ai/fieldAssistant.js
// Frontend adapter for the Supabase Edge Function.
// Import in src/main.js:
//   import { runFieldAI, formatFieldAIResult } from "./ai/fieldAssistant.js";

export async function runFieldAI(supabase, payload) {
  const { data, error } = await supabase.functions.invoke('ai-assistant', {
    body: payload
  });

  if (error) throw error;
  if (!data?.ok) throw new Error(data?.error || 'AI assistant failed.');

  return data.result;
}

export function formatFieldAIResult(result) {
  if (!result) return '';

  const lines = [];

  if (result.summary) {
    lines.push('SUMMARY');
    lines.push(result.summary);
    lines.push('');
  }

  if (Array.isArray(result.recommended_next_steps) && result.recommended_next_steps.length) {
    lines.push('RECOMMENDED NEXT STEPS');
    result.recommended_next_steps.forEach(step => lines.push(`• ${step}`));
    lines.push('');
  }

  const estimate = result.estimate_guidance;
  if (estimate) {
    lines.push('ESTIMATE GUIDANCE');

    if (Array.isArray(estimate.suggested_line_items)) {
      estimate.suggested_line_items.forEach(item => {
        lines.push(`• ${item.service}: ${item.qty} × $${Number(item.unit_price || 0).toFixed(2)} — ${item.rationale}`);
      });
    }

    lines.push(
      `Suggested subtotal range: $${Number(estimate.subtotal_low || 0).toFixed(2)}–$${Number(
        estimate.subtotal_high || 0
      ).toFixed(2)}`
    );

    if (estimate.pricing_notes) lines.push(estimate.pricing_notes);
    lines.push('');
  }

  if (result.customer_message) {
    lines.push('CUSTOMER MESSAGE');
    lines.push(result.customer_message);
    lines.push('');
  }

  if (result.invoice_notes) {
    lines.push('INVOICE NOTES');
    lines.push(result.invoice_notes);
    lines.push('');
  }

  if (Array.isArray(result.safety_flags) && result.safety_flags.length) {
    lines.push('SAFETY FLAGS');
    result.safety_flags.forEach(flag => lines.push(`• ${flag}`));
    lines.push('');
  }

  if (Array.isArray(result.legal_or_permit_reminders) && result.legal_or_permit_reminders.length) {
    lines.push('LEGAL / PERMIT REMINDERS');
    result.legal_or_permit_reminders.forEach(flag => lines.push(`• ${flag}`));
    lines.push('');
  }

  lines.push(`Confidence: ${result.confidence || 'medium'}`);

  return lines.join('\n');
}
