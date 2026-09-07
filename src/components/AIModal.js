import { startDictation, stopDictation } from '../ai/dictation.js';
import { getSuggestionsFromLLM } from '../ai/llm.js';

// Assume you have a simple state/store or use your existing one
// For demo, using module-level or you can adapt to your framework
let currentState = {
  species: 'Raccoon',
  season: 'Spring',
  observations: '',
  suggestions: '',
  isLoading: false,
  isListening: false
};

function updateUI() {
  // In real app, trigger re-render or update DOM
  // Example: document.getElementById('suggestions').innerText = currentState.suggestions;
  console.log('UI updated', currentState);
}

export function initAIModal() {
  // Wire Species/Season pickers to update currentState
  // Example listeners (adapt to your actual elements):
  // document.getElementById('species-select').onchange = (e) => { currentState.species = e.target.value; };
  // document.getElementById('season-select').onchange = (e) => { currentState.season = e.target.value; };
  // document.getElementById('observations').oninput = (e) => { currentState.observations = e.target.value; };

  // Dictate button
  const dictateBtn = document.getElementById('dictate-btn'); // or your actual id
  if (dictateBtn) {
    dictateBtn.onclick = async () => {
      if (currentState.isListening) {
        stopDictation();
        currentState.isListening = false;
        dictateBtn.textContent = 'Dictate';
        return;
      }

      currentState.isListening = true;
      dictateBtn.textContent = 'Stop';

      await startDictation(
        text => {
          currentState.observations = text;
          // Update your observations input field
          const obsField = document.getElementById('observations');
          if (obsField) obsField.value = text;
          updateUI();
        },
        errorMsg => {
          alert(errorMsg); // or your toast
          currentState.isListening = false;
          dictateBtn.textContent = 'Dictate';
        }
      );
    };
  }

  // Get Suggestions button - NOW CALLS REAL LLM
  const suggestBtn = document.getElementById('get-suggestions-btn');
  if (suggestBtn) {
    suggestBtn.onclick = async () => {
      currentState.isLoading = true;
      suggestBtn.disabled = true;
      suggestBtn.textContent = 'Thinking...';

      try {
        const result = await getSuggestionsFromLLM(
          currentState.species,
          currentState.season,
          currentState.observations
        );
        currentState.suggestions = result;
        // Update the suggestions display area
        const suggestionsBox = document.getElementById('suggestions-box');
        if (suggestionsBox) {
          suggestionsBox.innerHTML = `<pre style="white-space: pre-wrap; word-wrap: break-word;">${result}</pre>`;
        }
        updateUI();
      } catch (err) {
        currentState.suggestions = 'Error getting suggestions: ' + err.message;
      } finally {
        currentState.isLoading = false;
        suggestBtn.disabled = false;
        suggestBtn.textContent = 'Get Suggestions';
      }
    };
  }

  // Initial render
  updateUI();
}

// Call this on page/modal open
// initAIModal();
