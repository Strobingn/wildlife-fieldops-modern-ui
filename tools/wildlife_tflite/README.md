# Wildlife evidence TFLite trainer

Builds `app/src/main/assets/models/wildlife_evidence.tflite` + `labels.txt` + `model_meta.json`.

```bash
python3 -m venv /workspace/tflite-venv
/workspace/tflite-venv/bin/pip install tensorflow==2.20.0 pillow requests
/workspace/tflite-venv/bin/python tools/wildlife_tflite/train_and_export.py
```

Uses MobileNetV2 (ImageNet) transfer learning on a fixed 12-class NY wildlife + field-evidence
label set. Maximizes Wikimedia Commons open photos (search + seed files), uses synthetic fill
only when downloads are scarce, then freeze-head → fine-tune top layers with strong augmentation.

Do **not** re-export to “document” the CPU baseline. The checked-in flatbuffer is
the source of truth; sidecar text in `model_meta.json` can drift (see
[docs/cpu-model-admission-baseline.md](../../docs/cpu-model-admission-baseline.md)).

## Graph dump (stdlib, no TensorFlow)

Prints operator list, tensor I/O, Flex/CUSTOM presence, and SHA-256 of the
production asset. Safe to run in CI or locally; not wired into Gradle.

```bash
python3 tools/wildlife_tflite/summarize_model.py
python3 tools/wildlife_tflite/summarize_model.py --check
python3 tools/wildlife_tflite/summarize_model.py --json
```
