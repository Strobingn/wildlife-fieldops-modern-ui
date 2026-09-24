# CPU wildlife model graph / delegate admission baseline

Engineering brief 2026-09-24 (RAMP): mixed-precision must be validated as a
**compiled graph**, not a Keras layer list. This note records how the current
production CPU fallback artifact actually runs. It does **not** change weights,
converter settings, or any release flag.

Companion dump: `python3 tools/wildlife_tflite/summarize_model.py`
(`--check` pins identity/shapes; `--json` is machine-readable).

---

## Artifact identity

| Field | Value |
| --- | --- |
| Asset path | `app/src/main/assets/models/wildlife_evidence.tflite` |
| Runtime load path | `assets/models/wildlife_evidence.tflite` → copied to `filesDir/wildlife_evidence.tflite` for mmap |
| Labels | `app/src/main/assets/models/labels.txt` (12 classes) |
| Sidecar meta | `app/src/main/assets/models/model_meta.json` (`retrain`: `2.3.3-wildlife-retrain`) |
| Size | **2,879,552** bytes (2.75 MiB) |
| SHA-256 | `cabf6387a3f70f67d42cfadb5b3ce49e5f49fb0b6eba07da1de1eaba751bcb98` |
| Git blob | `d501a86aee63b67c1a563cd3de84ac8b247a42b3` |
| Last model commit | `70c7f735` (2026-09-12) `feat(cv): stronger wildlife evidence TFLite retrain (2.3.3-wildlife-retrain)` |
| Flatbuffer | identifier `TFL3`, schema version 3, description `MLIR Converted.` |
| `min_runtime_version` | `1.14.0` |
| Converter | TensorFlow **2.20.0** (from `CONVERSION_METADATA`) |
| App runtime | `org.tensorflow:tensorflow-lite:2.14.0` (`minSdk` 29) |

`OnDeviceSpeciesClassifier.modelHash` is **not** the SHA-256 of the
flatbuffer (ADR 0002). It hashes `length:lastModified` of the `filesDir` copy,
or the asset-path string if that copy is missing. Treat the table above as the
canonical identity for admission work.

---

## Compiled graph (not the layer list)

`model_meta.json` / `README.txt` describe the export as “dynamic-range
quantized.” The **checked-in flatbuffer disagrees**.
`train_and_export.py` sets `Optimize.DEFAULT` **and** a representative
dataset; TF 2.20 emitted an integer interior with float I/O wrappers.

Single subgraph `main`: **178** tensors, **68** operators. No Flex ops, no
CUSTOM ops, no DELEGATE nodes in the file.

| Role | Name | Type | Shape | Quantization |
| --- | --- | --- | --- | --- |
| Graph input | `serving_default_keras_tensor_160:0` | FLOAT32 | `[1, 224, 224, 3]` | none |
| After op 0 | `tfl.quantize` | INT8 | `[1, 224, 224, 3]` | scale `1/255` (`0.00392156886`), zp `-128` |
| Before last op | `StatefulPartitionedCall_1:01` | INT8 | `[1, 12]` | scale `1/256` (`0.00390625`), zp `-128` |
| Graph output | `StatefulPartitionedCall_1:0` | FLOAT32 | `[1, 12]` | none |

Tensor-type histogram: `INT8=121`, `INT32=55`, `FLOAT32=2`.
INT32 is biases / axis constants. Weights are per-axis int8
(`tfl.pseudo_qconst*`). Activations are scalar-quantized int8 after the
leading `QUANTIZE`.

Operator histogram (contiguous int8 body between the wrappers):

| Count | Op |
| ---: | --- |
| 1 | `QUANTIZE` (float NHWC → int8) |
| 35 | `CONV_2D` |
| 17 | `DEPTHWISE_CONV_2D` |
| 10 | `ADD` (MobileNetV2 residuals) |
| 1 | `MEAN` (global pool) |
| 2 | `FULLY_CONNECTED` (dense 1280→128, then 128→12) |
| 1 | `SOFTMAX` (int8) |
| 1 | `DEQUANTIZE` (int8 → float32 scores) |

This is the RAMP-relevant fact: the production CPU artifact is already
**mixed-type** (float32 I/O + int8 compute). Admitting GPU / NNAPI / a new
quant scheme on a MobileNetV2 layer list would miss the `QUANTIZE` /
`DEQUANTIZE` partition edges.

App preprocess matches the float I/O: resize to 224², RGB `/255.0` into a
direct `float32` `ByteBuffer` (`CustomEvidenceModel.INPUT_SIZE = 224`).
Output is `Array(1) { FloatArray(labelCount) }`. The app never reads
`Interpreter.getInputTensor` / `getOutputTensor` types at runtime.

12 labels (index / name / kind): raccoon, bat, squirrel, woodchuck, skunk,
opossum, bird (SPECIES); droppings, nest (DAMAGE); hole_entry (ENTRY);
trap_cage (EQUIPMENT); other (OTHER).

---

## How the app actually runs the artifact

### Admitted path (production CPU fallback)

`CustomEvidenceModel.ensureInterpreter` is the only `Interpreter` construction
in the repo:

```kotlin
val opts = Interpreter.Options().apply {
    setNumThreads(2)
}
val interp = Interpreter(model, opts)
```

That is the admitted CPU path:

- **No** `addDelegate(GpuDelegate)`
- **No** `addDelegate(NnApiDelegate)`
- **No** `setUseNNAPI(true)` (NNAPI stays off)
- **No** `setUseXNNPACK(...)` — TFLite 2.14 Java documents XNNPACK as
  **enabled by default**, so this is implicit CPU/XNNPACK, not an explicit
  admission decision
- Threads: 2
- mmap via `FileChannel.map` of the `filesDir` copy
- Init failure is sticky (`initFailed`): later frames skip TFLite and callers
  keep ML Kit + lexicon

Call sites (all best-effort; empty hits are non-fatal):

| Caller | When |
| --- | --- |
| `LiveCameraAnalyzer` | Live CameraX frames (bitmap downscaled to max side 320, then 224² in preprocess) |
| `PhotoAIHelper.analyzePhotoForFormFilling` | Still photos |
| `WalkthroughVideoAnalyzer` | Sparse video frames |

`WildlifeEvidenceDetector` merges custom hits with the ML Kit lexicon.
Hits are tagged `source = "tflite"`.

Application-level fallback (not graph-partition fallback): missing asset,
empty labels, `Interpreter` ctor failure, or `tryInfer` exception → empty
list → ML Kit + lexicon. `PhotoAIHelper` still reports `source = "offline_ml"`
even when TFLite contributed; `OnDeviceSpeciesClassifier.suggestFromAnalysis`
therefore tags still-photo backends as `mlkit` unless the source string
contains `"tflite"`. Live / evidence paths use `suggestFromEvidence` and can
tag `tflite` from `customHits`.

`ObservationEventFactory.DEFAULT_QUANTIZER` is `"none"`. Inference events
do not record `int8` even though the compiled interior is int8.

### Delegates present vs used

| Artifact | Gradle | Kotlin usage |
| --- | --- | --- |
| `org.tensorflow:tensorflow-lite:2.14.0` | yes | `Interpreter` only |
| `org.tensorflow:tensorflow-lite-support:0.4.4` | yes | **no imports** |
| `org.tensorflow:tensorflow-lite-gpu:2.14.0` | yes (“optional GPU delegate — safe no-op when unsupported”) | **never constructed**; `GpuDelegate` / `CompatibilityList` do not appear |
| NNAPI | in the Interpreter API | never enabled |
| XNNPACK | bundled in the 2.14 AAR | implicit default, not logged or gated |
| Flex | not on the classpath | graph has no Flex ops |

There is no release flag that flips GPU / NNAPI / XNNPACK. Do not add one
until a compiled execution plan exists for the device matrix below.

---

## Gaps vs RAMP-style contiguous-subgraph admission

RAMP-style admission asks: after the runtime partitions the **compiled**
graph, is the accelerated set a contiguous subgraph whose residual CPU
edges are known and acceptable?

Current baseline vs that bar:

1. **No partition map.** The app never dumps which of the 68 ops land on
   XNNPACK vs TFLite reference kernels, and never tries GPU/NNAPI.
2. **Application fallback ≠ subgraph fallback.** “Interpreter failed →
   ML Kit” hides a broken delegate. It does not record CPU islands inside
   an otherwise accelerated graph.
3. **Layer-list / sidecar drift.** `model_meta.json` says dynamic-range;
   the flatbuffer is int8-interior + float I/O. Mixed-precision work that
   trusts the sidecar will mis-admit.
4. **Unused GPU AAR.** Shipping `tensorflow-lite-gpu` without
   `CompatibilityList` + `addDelegate` is not GPU admission.
5. **Identity / provenance gaps.** Event `modelHash` / `quantizerTag` do
   not describe this flatbuffer (see above).
6. **CI cannot exercise device delegates.** GitHub Actions and the Cloud
   Agent assemble APKs; they have no S24 Ultra GPU, Hexagon NNAPI, or
   Tab A9+ driver stack.

Cloud Agent / CI *can* re-dump the compiled graph (stdlib script below).
That is graph evidence, not an execution plan.

---

## How a developer dumps the execution plan on device

Device GPU / NNAPI cannot be exercised in this Cloud Agent or in
`.github/workflows/build-android.yml`. Use a physical device.

### 1. Confirm the production CPU path (no extra flags)

Install the debug APK, open live camera or analyze a still, then:

```bash
adb logcat -s CustomEvidenceModel:* LiveCameraAnalyzer:* PhotoAIHelper:* WalkthroughVideo:* tflite:* TfLiteXNNPackDelegate:* TfLiteGpuDelegateV2:* NNAPI:*
```

Expect on the admitted path:

- `CustomEvidenceModel: TFLite Interpreter ready labels=12 input=224`
- `CustomEvidenceModel: tflite hits=...` when a frame scores ≥ 0.18
- **No** `TfLiteGpuDelegateV2` / NNAPI attach lines (delegates are not added)

If init fails:

- `Interpreter init failed (falling back): ...` then silence (sticky)

Verbose TFLite (optional):

```bash
adb shell setprop log.tag.tflite VERBOSE
adb shell setprop log.tag.tflitejni VERBOSE
adb shell setprop debug.tflite.trace 1
```

Restart the app after `setprop`. Look for `Applied XNNPACK delegate` /
`NnApiDelegate` / `TfLiteGpuDelegate` and any
`Didn't apply ... will run on CPU` partition notes.

### 2. Benchmark Model binary / APK (required for a plan)

Push the **checked-in** asset (do not reconvert):

```bash
adb push app/src/main/assets/models/wildlife_evidence.tflite /data/local/tmp/wildlife_evidence.tflite
```

Use the matching TFLite 2.14 `benchmark_model` (or Benchmark Model APK
from the TensorFlow Lite performance docs) with the **same thread count
as the app**:

```bash
# Admitted CPU / implicit-XNNPACK analogue
adb shell /data/local/tmp/benchmark_model \
  --graph=/data/local/tmp/wildlife_evidence.tflite \
  --num_threads=2 \
  --enable_op_profiling=true \
  --use_xnnpack=true \
  --use_gpu=false \
  --use_nnapi=false

# GPU delegate (do not ship; measurement only)
adb shell /data/local/tmp/benchmark_model \
  --graph=/data/local/tmp/wildlife_evidence.tflite \
  --num_threads=2 \
  --enable_op_profiling=true \
  --use_gpu=true \
  --gpu_precision_loss_allowed=true

# NNAPI (do not ship; measurement only)
adb shell /data/local/tmp/benchmark_model \
  --graph=/data/local/tmp/wildlife_evidence.tflite \
  --num_threads=2 \
  --enable_op_profiling=true \
  --use_nnapi=true
```

APK form (`org.tensorflow.lite.benchmark/.BenchmarkModelActivity`) takes
the same flags in `--es args`. Save the op-profiling table: it is the
execution plan (delegate vs CPU per node).

Record, per device:

- `Applied XNNPACK/GPU/NNAPI delegate` and node counts
- First/last delegated node index (contiguous or not)
- Residual CPU ops, especially `QUANTIZE`, `DEQUANTIZE`, `SOFTMAX`, `MEAN`
- Latency p50/p95 vs the 250 ms live-analyzer result-age budget
- Whether GPU `CompatibilityList.isDelegateSupportedOnThisDevice` is true
  (only relevant if/when the app constructs `GpuDelegate`)

### 3. Device matrix

| Tier | Example | What to prove |
| --- | --- | --- |
| High | Galaxy S24 Ultra | GPU `CompatibilityList` + NNAPI/Hexagon plan vs CPU+XNNPACK. Int8 interiors often **do not** take a single GPU subgraph; do not infer admission from FP16 MobileNet lore. |
| Mid | Galaxy Tab A9+ | Mid-tier GPU/NNAPI completeness. Expect more CPU islands at the float/int8 edges. |
| Low | entry Android 10+ (`minSdk` 29) | Treat CPU + default XNNPACK as the only realistic plan. GPU `CompatibilityList` is often empty; NNAPI may register and then partition badly. |

Use the same `--num_threads=2` as `CustomEvidenceModel`. A 1-thread or
4-thread bench is a different plan.

---

## Recommended next harness steps (no weight / flag changes)

1. Keep `python3 tools/wildlife_tflite/summarize_model.py --check` as the
   graph-identity lock (stdlib; safe in CI later without a TF install).
2. On S24 Ultra / Tab A9+ / one low-tier device, capture the three
   `benchmark_model` plans above against this SHA-256. Store the op
   profiles next to this note.
3. Admit a delegate only if the profile is a **contiguous** accelerated
   subgraph whose residual CPU ops are the known float wrappers (or
   empty). Reject “N of 68 layers are supported” spreadsheets.
4. After a plan exists: decide whether to construct `GpuDelegate` behind
   `CompatibilityList`, enable NNAPI, or leave CPU+XNNPACK as the
   shipped path. That is a later PR; this baseline does not flip it.
5. Separate cleanup (still not this baseline): align
   `model_meta.json` wording with the int8 interior; consider recording
   `quantizerTag=int8` and a real flatbuffer SHA-256 on
   `ObservationEvent`. Do not retune or requantize to make the sidecar
   true.

---

## Out of scope (explicit)

- No production weight rewrite, converter re-run, or quant scheme change
- No Gradle / TFLite version upgrades
- No GPU / NNAPI / XNNPACK flag flip in `CustomEvidenceModel`
- ASR / llama.cpp / ML Kit labeler internals (only as TFLite fallback)
