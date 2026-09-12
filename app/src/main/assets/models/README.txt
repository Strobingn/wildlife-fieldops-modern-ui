wildlife_evidence.tflite — MobileNetV2 transfer classifier for NY wildlife + field evidence
(labels: raccoon, bat, squirrel, woodchuck, skunk, opossum, bird, droppings, hole_entry,
trap_cage, nest, other). Companion labels.txt maps index → label → Kind.
Trained via tools/wildlife_tflite/train_and_export.py. App falls back to ML Kit + lexicon
if the asset is missing or Interpreter init fails.
