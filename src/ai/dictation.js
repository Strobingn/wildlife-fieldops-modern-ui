import { Toast } from '../ui/toast.js'; // adjust import to your toast system

let recognition = null;
let isListening = false;

export function initDictation(onResult, onError) {
  const SpeechRecognitionAPI = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognitionAPI) {
    onError('Speech recognition not supported on this device');
    return null;
  }

  recognition = new SpeechRecognitionAPI();
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.lang = 'en-US';

  recognition.onresult = event => {
    let finalTranscript = '';
    for (let i = event.resultIndex; i < event.results.length; ++i) {
      if (event.results[i].isFinal) {
        finalTranscript += event.results[i][0].transcript;
      }
    }
    if (finalTranscript) {
      onResult(finalTranscript.trim());
    }
    isListening = false;
  };

  recognition.onerror = event => {
    isListening = false;
    let message = 'Dictation error';
    if (event.error === 'not-allowed' || event.error === 'permission-denied') {
      message = 'Dictation error: Microphone permission denied. Please allow mic access.';
    } else if (event.error === 'no-speech') {
      message = 'No speech detected. Try again.';
    } else {
      message = `Dictation error: ${event.error}`;
    }
    onError(message);
  };

  recognition.onend = () => {
    isListening = false;
  };

  return recognition;
}

export async function startDictation(onResult, onError) {
  if (isListening) return;

  // Request microphone permission (Capacitor or native bridge if available)
  try {
    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Permissions) {
      const perm = await window.Capacitor.Plugins.Permissions.requestPermission({ permission: 'microphone' });
      if (perm.state !== 'granted') {
        onError('Dictation error: Microphone permission not granted');
        return;
      }
    } else {
      // Fallback for pure web - browser will prompt on start
    }
  } catch (e) {
    console.warn('Permission plugin not available, falling back to browser prompt');
  }

  if (!recognition) {
    recognition = initDictation(onResult, onError);
  }

  try {
    recognition.start();
    isListening = true;
  } catch (err) {
    isListening = false;
    onError('Dictation error: Could not start recognition');
  }
}

export function stopDictation() {
  if (recognition && isListening) {
    recognition.stop();
    isListening = false;
  }
}
