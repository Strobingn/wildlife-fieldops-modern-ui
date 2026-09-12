#!/usr/bin/env python3
"""Train a compact NY wildlife+evidence MobileNetV2 classifier and export TFLite.

Produces:
  app/src/main/assets/models/wildlife_evidence.tflite
  app/src/main/assets/models/labels.txt
"""
from __future__ import annotations

import io
import json
import os
import random
from pathlib import Path
from urllib.parse import unquote

import numpy as np
import requests
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "models"
WORK = Path("/workspace/wildlife-evidence-train")
DATA = WORK / "data"
CKPT = WORK / "ckpt"
IMG_SIZE = 224
BATCH = 16
SEED = 42
UA = "WildlifeFieldOpsTrainer/1.0 (FieldOps on-device model; contact bot@wildlife-whisperer.local)"

LABELS = [
    ("raccoon", "SPECIES"),
    ("bat", "SPECIES"),
    ("squirrel", "SPECIES"),
    ("woodchuck", "SPECIES"),
    ("skunk", "SPECIES"),
    ("opossum", "SPECIES"),
    ("bird", "SPECIES"),
    ("droppings", "DAMAGE"),
    ("hole_entry", "ENTRY"),
    ("trap_cage", "EQUIPMENT"),
    ("nest", "DAMAGE"),
    ("other", "OTHER"),
]

COMMONS_FILES = {
    "raccoon": ["File:Raccoon.jpg", "File:Procyon lotor.jpg", "File:Raccoon (Procyon lotor) 2.jpg"],
    "bat": ["File:Big brown bat.jpg", "File:Myotis myotis.jpg", "File:Little brown bat.jpg"],
    "squirrel": ["File:Sciurus carolinensis.jpg", "File:Eastern Grey Squirrel.jpg", "File:Sciurus carolinensis eating.jpg"],
    "woodchuck": ["File:Groundhog.jpg", "File:Marmota monax UL 04.jpg", "File:Marmota monax standing.jpg"],
    "skunk": ["File:Striped skunk.jpg", "File:Striped Skunk (Mephitis mephitis) DSC 0030.jpg", "File:Striped Skunk not a cat.jpg"],
    "opossum": ["File:Virginia Opossum.jpg", "File:Opossum 2.jpg", "File:AwesomePossum-AmericanOpossum.jpg"],
    "bird": ["File:Columba livia.jpg", "File:European Starling.jpg", "File:House Sparrow (Passer domesticus) male.jpg"],
    "droppings": ["File:Guano.jpg", "File:Bird droppings on a car.jpg", "File:Pigeon droppings.jpg"],
    "hole_entry": ["File:Hole in wall.jpg", "File:Hole in a brick wall.jpg", "File:Damaged brick wall.jpg"],
    "trap_cage": [
        "File:Louisiana Possum in a catch trap cage - 1.jpg",
        "File:Louisiana Possum in a catch trap cage - 2.jpg",
        "File:Louisiana Possum in a catch trap cage - 3.jpg",
    ],
    "nest": ["File:Bird nest.jpg", "File:American Robin nest.jpg", "File:Bird nest with eggs.jpg"],
    "other": ["File:Red apple.jpg", "File:Office-supplies.jpg", "File:Bananas.jpg"],
}

SYNTH_COLORS = {
    "raccoon": [(90, 90, 90), (40, 40, 40), (200, 200, 200)],
    "bat": [(30, 20, 40), (80, 60, 90), (10, 10, 20)],
    "squirrel": [(160, 100, 50), (120, 70, 30), (200, 160, 100)],
    "woodchuck": [(110, 90, 60), (70, 55, 35), (150, 130, 90)],
    "skunk": [(20, 20, 20), (240, 240, 240), (15, 15, 15)],
    "opossum": [(180, 170, 150), (100, 90, 80), (220, 210, 190)],
    "bird": [(70, 130, 180), (220, 180, 40), (40, 90, 40)],
    "droppings": [(60, 45, 30), (90, 70, 40), (40, 30, 20)],
    "hole_entry": [(80, 50, 30), (30, 20, 15), (120, 90, 60)],
    "trap_cage": [(120, 120, 130), (60, 60, 70), (180, 180, 190)],
    "nest": [(140, 110, 60), (90, 70, 40), (180, 150, 90)],
    "other": [(200, 220, 255), (255, 200, 200), (200, 255, 200)],
}


def ensure_dirs():
    ASSETS.mkdir(parents=True, exist_ok=True)
    CKPT.mkdir(parents=True, exist_ok=True)
    for name, _ in LABELS:
        (DATA / "train" / name).mkdir(parents=True, exist_ok=True)
        (DATA / "val" / name).mkdir(parents=True, exist_ok=True)


def commons_url(title: str) -> str | None:
    try:
        r = requests.get(
            "https://commons.wikimedia.org/w/api.php",
            params={
                "action": "query",
                "titles": title,
                "prop": "imageinfo",
                "iiprop": "url",
                "format": "json",
            },
            headers={"User-Agent": UA},
            timeout=30,
        )
        r.raise_for_status()
        pages = r.json().get("query", {}).get("pages", {})
        for page in pages.values():
            if "missing" in page:
                return None
            info = (page.get("imageinfo") or [{}])[0]
            url = info.get("url")
            if url:
                return url.split("?")[0]
    except Exception as e:
        print(f"  api fail {title}: {e}")
    return None


def fetch_image(url: str, timeout: int = 45) -> Image.Image | None:
    try:
        r = requests.get(url, timeout=timeout, headers={"User-Agent": UA}, stream=True)
        r.raise_for_status()
        data = r.content
        if len(data) < 1000:
            return None
        img = Image.open(io.BytesIO(data)).convert("RGB")
        return img.resize((IMG_SIZE, IMG_SIZE), Image.Resampling.BILINEAR)
    except Exception as e:
        print(f"  fetch fail: {url[:90]}… ({e})")
        return None


def make_synthetic(label: str, idx: int) -> Image.Image:
    rng = random.Random(SEED + hash(label) % 10000 + idx)
    colors = SYNTH_COLORS[label]
    img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), colors[0])
    draw = ImageDraw.Draw(img)
    for _ in range(8 + idx % 5):
        c = colors[rng.randint(0, len(colors) - 1)]
        x0, y0 = rng.randint(0, IMG_SIZE - 40), rng.randint(0, IMG_SIZE - 40)
        x1, y1 = x0 + rng.randint(20, 100), y0 + rng.randint(20, 100)
        shape = rng.choice(["ellipse", "rect", "line"])
        if shape == "ellipse":
            draw.ellipse([x0, y0, x1, y1], fill=c)
        elif shape == "rect":
            draw.rectangle([x0, y0, x1, y1], fill=c)
        else:
            draw.line([x0, y0, x1, y1], fill=c, width=rng.randint(2, 8))
    if label == "trap_cage":
        for x in range(10, IMG_SIZE, 14):
            draw.line([x, 10, x, IMG_SIZE - 10], fill=colors[1], width=2)
        for y in range(10, IMG_SIZE, 14):
            draw.line([10, y, IMG_SIZE - 10, y], fill=colors[1], width=2)
    if label == "hole_entry":
        draw.ellipse([70, 70, 154, 154], fill=(10, 10, 10))
    if label == "droppings":
        for _ in range(12):
            x, y = rng.randint(20, 200), rng.randint(20, 200)
            draw.ellipse([x, y, x + 12, y + 8], fill=colors[1])
    if label == "nest":
        for _ in range(40):
            x, y = rng.randint(40, 180), rng.randint(40, 180)
            draw.line(
                [x, y, x + rng.randint(-20, 20), y + rng.randint(-20, 20)],
                fill=colors[1],
                width=1,
            )
    img = img.filter(ImageFilter.GaussianBlur(radius=rng.uniform(0.2, 1.2)))
    img = ImageEnhance.Brightness(img).enhance(rng.uniform(0.7, 1.3))
    img = ImageEnhance.Contrast(img).enhance(rng.uniform(0.8, 1.4))
    arr = np.array(img).astype(np.int16)
    noise = np.random.default_rng(SEED + idx).integers(-12, 13, size=arr.shape)
    arr = np.clip(arr + noise, 0, 255).astype(np.uint8)
    return Image.fromarray(arr)


def augment(img: Image.Image, idx: int) -> Image.Image:
    rng = random.Random(SEED + idx)
    out = img.copy()
    if rng.random() < 0.5:
        out = out.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    angle = rng.uniform(-18, 18)
    out = out.rotate(angle, resample=Image.Resampling.BILINEAR, fillcolor=(0, 0, 0))
    zoom = rng.uniform(0.82, 1.0)
    w = int(IMG_SIZE * zoom)
    left = rng.randint(0, max(0, IMG_SIZE - w))
    top = rng.randint(0, max(0, IMG_SIZE - w))
    out = out.crop((left, top, left + w, top + w)).resize(
        (IMG_SIZE, IMG_SIZE), Image.Resampling.BILINEAR
    )
    out = ImageEnhance.Brightness(out).enhance(rng.uniform(0.75, 1.25))
    out = ImageEnhance.Color(out).enhance(rng.uniform(0.8, 1.2))
    return out


def build_dataset():
    ensure_dirs()
    summary = {}
    for name, _kind in LABELS:
        print(f"Preparing class: {name}")
        bases: list[Image.Image] = []
        fetched = 0
        for title in COMMONS_FILES.get(name, []):
            url = commons_url(title)
            if not url:
                print(f"  missing: {title}")
                continue
            img = fetch_image(url)
            if img is not None:
                bases.append(img)
                fetched += 1
                print(f"  got {title}")
        for i in range(24):
            bases.append(make_synthetic(name, i))
        summary[name] = {"commons_ok": fetched, "base_pool": len(bases)}
        n = len(bases)
        for i in range(48):
            src = bases[i % n]
            aug = augment(src, i + (hash(name) % 997))
            split = "val" if i % 6 == 0 else "train"
            path = DATA / split / name / f"{name}_{i:03d}.jpg"
            aug.save(path, quality=90)
        print(f"  wrote 48 images for {name}; commons={fetched} pool={n}")
    (WORK / "dataset_summary.json").write_text(json.dumps(summary, indent=2))


def make_tf_datasets():
    train_ds = tf.keras.utils.image_dataset_from_directory(
        DATA / "train",
        labels="inferred",
        label_mode="categorical",
        class_names=[n for n, _ in LABELS],
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH,
        shuffle=True,
        seed=SEED,
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        DATA / "val",
        labels="inferred",
        label_mode="categorical",
        class_names=[n for n, _ in LABELS],
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH,
        shuffle=False,
        seed=SEED,
    )
    autotune = tf.data.AUTOTUNE
    train_ds = train_ds.map(
        lambda x, y: (tf.cast(x, tf.float32) / 255.0, y), num_parallel_calls=autotune
    ).prefetch(autotune)
    val_ds = val_ds.map(
        lambda x, y: (tf.cast(x, tf.float32) / 255.0, y), num_parallel_calls=autotune
    ).prefetch(autotune)
    return train_ds, val_ds


def build_model(num_classes: int):
    base = keras.applications.MobileNetV2(
        input_shape=(IMG_SIZE, IMG_SIZE, 3),
        include_top=False,
        weights="imagenet",
    )
    base.trainable = False
    inputs = keras.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
    x = base(inputs, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.25)(x)
    outputs = layers.Dense(num_classes, activation="softmax")(x)
    model = keras.Model(inputs, outputs)
    model.compile(
        optimizer=keras.optimizers.Adam(1e-3),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model, base


def train():
    train_ds, val_ds = make_tf_datasets()
    model, base = build_model(len(LABELS))
    cb = [
        keras.callbacks.EarlyStopping(
            patience=3, restore_best_weights=True, monitor="val_accuracy"
        ),
        keras.callbacks.ModelCheckpoint(
            str(CKPT / "best.keras"), save_best_only=True, monitor="val_accuracy"
        ),
    ]
    print("Phase 1: train head…")
    model.fit(train_ds, validation_data=val_ds, epochs=8, callbacks=cb)
    print("Phase 2: fine-tune…")
    base.trainable = True
    for layer in base.layers[:-40]:
        layer.trainable = False
    model.compile(
        optimizer=keras.optimizers.Adam(1e-4),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.fit(train_ds, validation_data=val_ds, epochs=6, callbacks=cb)
    model.save(CKPT / "final.keras")
    return model


def export_tflite(model: keras.Model):
    def rep():
        files = list((DATA / "train").rglob("*.jpg"))[:80]
        for p in files:
            img = tf.io.read_file(str(p))
            img = tf.image.decode_jpeg(img, channels=3)
            img = tf.image.resize(img, [IMG_SIZE, IMG_SIZE])
            img = tf.cast(img, tf.float32) / 255.0
            yield [tf.expand_dims(img, 0)]

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = rep
    tflite_model = converter.convert()
    out = ASSETS / "wildlife_evidence.tflite"
    out.write_bytes(tflite_model)
    labels_path = ASSETS / "labels.txt"
    lines = [f"{i}\t{name}\t{kind}" for i, (name, kind) in enumerate(LABELS)]
    labels_path.write_text("\n".join(lines) + "\n")
    meta = {
        "input_size": IMG_SIZE,
        "labels": [{"index": i, "label": n, "kind": k} for i, (n, k) in enumerate(LABELS)],
        "normalization": "float32 /255.0 NHWC",
        "architecture": "MobileNetV2 ImageNet transfer + softmax head (dynamic-range quantized)",
        "size_bytes": len(tflite_model),
        "source": "Wikimedia Commons photos + synthetic augmentation; tools/wildlife_tflite/train_and_export.py",
    }
    (ASSETS / "model_meta.json").write_text(json.dumps(meta, indent=2))
    (WORK / "export_meta.json").write_text(json.dumps(meta, indent=2))
    print(f"Wrote {out} ({len(tflite_model)} bytes)")
    return out, len(tflite_model)


def verify_interpreter(path: Path):
    interpreter = tf.lite.Interpreter(model_path=str(path))
    interpreter.allocate_tensors()
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]
    print("Input:", inp["shape"], inp["dtype"])
    print("Output:", out["shape"], out["dtype"])
    x = np.random.rand(1, IMG_SIZE, IMG_SIZE, 3).astype(np.float32)
    interpreter.set_tensor(inp["index"], x)
    interpreter.invoke()
    y = interpreter.get_tensor(out["index"])
    print("Smoke softmax sum:", float(y.sum()), "argmax:", int(y.argmax()))


def main():
    random.seed(SEED)
    np.random.seed(SEED)
    tf.random.set_seed(SEED)
    print("TF", tf.__version__)
    # clean prior images for a fresh split
    if DATA.exists():
        import shutil

        shutil.rmtree(DATA)
    build_dataset()
    model = train()
    path, size = export_tflite(model)
    verify_interpreter(path)
    print("DONE size_mb=", round(size / (1024 * 1024), 2))


if __name__ == "__main__":
    main()
