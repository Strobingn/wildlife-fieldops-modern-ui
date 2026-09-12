# Wildlife evidence TFLite trainer

Builds `app/src/main/assets/models/wildlife_evidence.tflite` + `labels.txt`.

```bash
python3 -m venv /workspace/tflite-venv
/workspace/tflite-venv/bin/pip install tensorflow==2.20.0 pillow requests
/workspace/tflite-venv/bin/python tools/wildlife_tflite/train_and_export.py
```

Uses MobileNetV2 (ImageNet) transfer learning on a compact NY wildlife + field-evidence
label set, with Wikimedia Commons photos where available and synthetic augmentation.
