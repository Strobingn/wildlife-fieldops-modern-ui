wildlife_evidence.tflite — MobileNetV2 transfer classifier for NY wildlife + field evidence
(12 classes: raccoon, bat, squirrel, woodchuck, skunk, opossum, bird, droppings, hole_entry,
trap_cage, nest, other). Companion labels.txt maps index → label → Kind.

Retrain 2.3.3: maximized Wikimedia Commons / open-license photos (~40 real/class), strong
augmentation, freeze-head then fine-tune top MobileNetV2 layers; dynamic-range quantized.
