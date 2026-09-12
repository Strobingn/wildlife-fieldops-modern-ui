#!/usr/bin/env python3
"""Train a compact NY wildlife+evidence MobileNetV2 classifier and export TFLite.

Phase-2 retrain: maximize Wikimedia Commons (open) photos per class (aim 25–50+),
stronger augmentation, longer freeze-then-fine-tune, early stopping.

Produces:
  app/src/main/assets/models/wildlife_evidence.tflite
  app/src/main/assets/models/labels.txt
  app/src/main/assets/models/model_meta.json
"""
from __future__ import annotations

import io
import json
import os
import random
import time
from pathlib import Path

import numpy as np
import requests
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance, ImageOps

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "models"
WORK = Path("/workspace/wildlife-evidence-train")
DATA = WORK / "data"
CKPT = WORK / "ckpt"
CACHE = WORK / "commons_cache"
IMG_SIZE = 224
BATCH = 16
SEED = 42
TARGET_REAL = 40          # aim for this many usable Commons photos / class
MIN_REAL_SOFT = 25        # soft floor before synthetic fill
MAX_SEARCH_PER_QUERY = 50
SYNTH_FILL_CAP = 8        # only when real photos are scarce
AUG_PER_BASE_TRAIN = 4
AUG_PER_BASE_VAL = 1
HEAD_EPOCHS = 20
FT_EPOCHS = 16
UA = "WildlifeFieldOpsTrainer/2.0 (FieldOps on-device model; contact bot@wildlife-whisperer.local)"

# Keep identical 12 labels/kinds for CustomEvidenceModel compatibility.
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

# Explicit seed files (phase-1 titles) + rich Commons search queries.
COMMONS_SEED_FILES = {
    "raccoon": [
        "File:Raccoon.jpg",
        "File:Procyon lotor.jpg",
        "File:Raccoon (Procyon lotor) 2.jpg",
        "File:Procyon lotor - 04.jpg",
        "File:Raccoon climbing.jpg",
    ],
    "bat": [
        "File:Big brown bat.jpg",
        "File:Myotis myotis.jpg",
        "File:Little brown bat.jpg",
        "File:Eptesicus fuscus.jpg",
        "File:Brown bat.jpg",
    ],
    "squirrel": [
        "File:Sciurus carolinensis.jpg",
        "File:Eastern Grey Squirrel.jpg",
        "File:Sciurus carolinensis eating.jpg",
        "File:Eastern Gray Squirrel.jpg",
    ],
    "woodchuck": [
        "File:Groundhog.jpg",
        "File:Marmota monax UL 04.jpg",
        "File:Marmota monax standing.jpg",
        "File:Groundhog (Marmota monax).jpg",
    ],
    "skunk": [
        "File:Striped skunk.jpg",
        "File:Striped Skunk (Mephitis mephitis) DSC 0030.jpg",
        "File:Striped Skunk not a cat.jpg",
        "File:Mephitis mephitis.jpg",
    ],
    "opossum": [
        "File:Virginia Opossum.jpg",
        "File:Opossum 2.jpg",
        "File:AwesomePossum-AmericanOpossum.jpg",
        "File:Didelphis virginiana.jpg",
    ],
    "bird": [
        "File:Columba livia.jpg",
        "File:European Starling.jpg",
        "File:House Sparrow (Passer domesticus) male.jpg",
        "File:Rock Dove.jpg",
    ],
    "droppings": [
        "File:Guano.jpg",
        "File:Bird droppings on a car.jpg",
        "File:Pigeon droppings.jpg",
        "File:Bat guano.jpg",
    ],
    "hole_entry": [
        "File:Hole in wall.jpg",
        "File:Hole in a brick wall.jpg",
        "File:Damaged brick wall.jpg",
        "File:Mouse hole.jpg",
    ],
    "trap_cage": [
        "File:Louisiana Possum in a catch trap cage - 1.jpg",
        "File:Louisiana Possum in a catch trap cage - 2.jpg",
        "File:Louisiana Possum in a catch trap cage - 3.jpg",
        "File:Have-a-Heart trap.jpg",
    ],
    "nest": [
        "File:Bird nest.jpg",
        "File:American Robin nest.jpg",
        "File:Bird nest with eggs.jpg",
        "File:Squirrel nest.jpg",
    ],
    "other": [
        "File:Red apple.jpg",
        "File:Office-supplies.jpg",
        "File:Bananas.jpg",
        "File:Bicycle.jpg",
        "File:Chair.jpg",
    ],
}

COMMONS_QUERIES = {
    "raccoon": [
        "Procyon lotor",
        "raccoon",
        "northern raccoon",
        "common raccoon",
        "Waschbär Procyon",
    ],
    "bat": [
        "Eptesicus fuscus",
        "Myotis lucifugus",
        "little brown bat",
        "big brown bat",
        "microchiroptera roost",
        "pipistrellus",
    ],
    "squirrel": [
        "Sciurus carolinensis",
        "eastern gray squirrel",
        "grey squirrel",
        "Sciurus",
        "tree squirrel",
    ],
    "woodchuck": [
        "Marmota monax",
        "groundhog",
        "woodchuck",
        "Marmota monax standing",
    ],
    "skunk": [
        "Mephitis mephitis",
        "striped skunk",
        "skunk Mephitis",
        "Conepatus",
    ],
    "opossum": [
        "Didelphis virginiana",
        "Virginia opossum",
        "opossum Didelphis",
        "American opossum",
    ],
    "bird": [
        "Columba livia",
        "rock pigeon",
        "Sturnus vulgaris",
        "Passer domesticus",
        "European starling",
        "house sparrow",
        "feral pigeon",
    ],
    "droppings": [
        "bird droppings",
        "pigeon droppings",
        "bat guano",
        "guano cave",
        "bird feces",
        "sparrow droppings",
    ],
    "hole_entry": [
        "hole in wall",
        "hole in brick wall",
        "damaged brick wall",
        "mouse hole wall",
        "rodent hole",
        "vent damage hole",
        "soffit hole",
    ],
    "trap_cage": [
        "live animal trap",
        "cage trap raccoon",
        "Have-a-Heart trap",
        "catch trap cage",
        "humane animal trap",
        "wire cage trap",
    ],
    "nest": [
        "bird nest",
        "robin nest eggs",
        "squirrel drey nest",
        "pigeon nest",
        "attic bird nest",
        "nest with eggs",
    ],
    "other": [
        "red apple fruit",
        "office desk supplies",
        "banana fruit",
        "bicycle outdoors",
        "wooden chair",
        "garden tools",
        "car keys",
        "coffee mug",
    ],
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
    CACHE.mkdir(parents=True, exist_ok=True)
    for name, _ in LABELS:
        (DATA / "train" / name).mkdir(parents=True, exist_ok=True)
        (DATA / "val" / name).mkdir(parents=True, exist_ok=True)


def _session() -> requests.Session:
    s = requests.Session()
    s.headers.update({"User-Agent": UA})
    return s


SESSION = _session()


def commons_url_for_title(title: str) -> str | None:
    try:
        r = SESSION.get(
            "https://commons.wikimedia.org/w/api.php",
            params={
                "action": "query",
                "titles": title,
                "prop": "imageinfo",
                "iiprop": "url|mime|size|thumburl",
                "iiurlwidth": 800,
                "format": "json",
            },
            timeout=30,
        )
        r.raise_for_status()
        pages = r.json().get("query", {}).get("pages", {})
        for page in pages.values():
            if "missing" in page:
                return None
            info = (page.get("imageinfo") or [{}])[0]
            mime = (info.get("mime") or "").lower()
            if mime and not mime.startswith("image/"):
                return None
            if mime in ("image/svg+xml", "image/gif"):
                return None
            url = info.get("thumburl") or info.get("url")
            if url:
                return url.split("?")[0]
    except Exception as e:
        print(f"  api fail {title}: {e}")
    return None


def commons_search(query: str, limit: int = MAX_SEARCH_PER_QUERY) -> list[tuple[str, str]]:
    """Return list of (title, url) from Commons file search."""
    out: list[tuple[str, str]] = []
    try:
        r = SESSION.get(
            "https://commons.wikimedia.org/w/api.php",
            params={
                "action": "query",
                "generator": "search",
                "gsrsearch": query,
                "gsrnamespace": 6,
                "gsrlimit": min(limit, 50),
                "prop": "imageinfo",
                "iiprop": "url|mime|size|thumburl",
                "iiurlwidth": 800,
                "format": "json",
            },
            timeout=45,
        )
        r.raise_for_status()
        pages = r.json().get("query", {}).get("pages", {})
        for page in pages.values():
            title = page.get("title") or ""
            info = (page.get("imageinfo") or [{}])[0]
            mime = (info.get("mime") or "").lower()
            size = int(info.get("size") or 0)
            url = info.get("thumburl") or info.get("url")
            if not title or not url:
                continue
            if not mime.startswith("image/"):
                continue
            if mime in ("image/svg+xml", "image/gif", "image/tiff"):
                continue
            if size and size < 5000 and not info.get("thumburl"):
                continue
            out.append((title, url.split("?")[0]))
    except Exception as e:
        print(f"  search fail '{query}': {e}")
    return out



def fetch_image(url: str, cache_key: str, timeout: int = 60, retries: int = 4) -> Image.Image | None:
    cache_path = CACHE / f"{cache_key}.jpg"
    try:
        if cache_path.exists() and cache_path.stat().st_size > 1000:
            img = Image.open(cache_path).convert("RGB")
            return img.resize((IMG_SIZE, IMG_SIZE), Image.Resampling.BILINEAR)
        last_err = None
        for attempt in range(retries):
            try:
                r = SESSION.get(url, timeout=timeout, stream=True)
                if r.status_code == 429:
                    wait = 20 * (attempt + 1)
                    print(f"  429 rate-limit; sleeping {wait}s…")
                    time.sleep(wait)
                    continue
                r.raise_for_status()
                data = r.content
                if len(data) < 2000:
                    return None
                img = Image.open(io.BytesIO(data)).convert("RGB")
                if min(img.size) < 64:
                    return None
                img_r = img.resize((IMG_SIZE, IMG_SIZE), Image.Resampling.BILINEAR)
                img_r.save(cache_path, quality=92)
                return img_r
            except Exception as e:
                last_err = e
                time.sleep(2 * (attempt + 1))
        print(f"  fetch fail: {url[:90]}… ({last_err})")
        return None
    except Exception as e:
        print(f"  fetch fail: {url[:90]}… ({e})")
        return None


def openverse_search(query: str, limit: int = 30) -> list[tuple[str, str]]:
    """Open-license photos via Openverse (CC0/PDM/BY/BY-SA)."""
    out: list[tuple[str, str]] = []
    try:
        r = SESSION.get(
            "https://api.openverse.org/v1/images/",
            params={
                "q": query,
                "page_size": min(limit, 40),
                "license": "cc0,pdm,by,by-sa",
                "extension": "jpg,png",
            },
            timeout=45,
        )
        if r.status_code != 200:
            print(f"  openverse '{query}' status {r.status_code}")
            return out
        results = r.json().get("results") or []
        for item in results:
            # Prefer CDN thumbnail to cut bandwidth / rate-limit pressure
            url = item.get("thumbnail") or item.get("url")
            title = item.get("title") or item.get("id") or query
            if url:
                out.append((f"openverse:{title}", url.split("?")[0]))
    except Exception as e:
        print(f"  openverse fail '{query}': {e}")
    return out


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
    """Stronger PIL augmentation for train/val variants from real photos."""
    rng = random.Random(SEED + idx * 17)
    out = img.copy()
    if rng.random() < 0.55:
        out = out.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if rng.random() < 0.15:
        out = out.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    angle = rng.uniform(-28, 28)
    out = out.rotate(angle, resample=Image.Resampling.BILINEAR, fillcolor=(0, 0, 0))
    # Random zoom / crop
    zoom = rng.uniform(0.72, 1.05)
    w = max(32, int(IMG_SIZE * zoom))
    left = rng.randint(0, max(0, IMG_SIZE - w))
    top = rng.randint(0, max(0, IMG_SIZE - w))
    out = out.crop((left, top, left + w, top + w)).resize(
        (IMG_SIZE, IMG_SIZE), Image.Resampling.BILINEAR
    )
    if rng.random() < 0.35:
        # mild perspective-ish via affine
        dx = rng.randint(-18, 18)
        dy = rng.randint(-18, 18)
        out = out.transform(
            out.size,
            Image.Transform.AFFINE,
            (1, rng.uniform(-0.08, 0.08), dx, rng.uniform(-0.08, 0.08), 1, dy),
            resample=Image.Resampling.BILINEAR,
            fillcolor=(0, 0, 0),
        )
    out = ImageEnhance.Brightness(out).enhance(rng.uniform(0.65, 1.4))
    out = ImageEnhance.Contrast(out).enhance(rng.uniform(0.7, 1.45))
    out = ImageEnhance.Color(out).enhance(rng.uniform(0.6, 1.4))
    out = ImageEnhance.Sharpness(out).enhance(rng.uniform(0.6, 1.6))
    if rng.random() < 0.25:
        out = out.filter(ImageFilter.GaussianBlur(radius=rng.uniform(0.3, 1.4)))
    if rng.random() < 0.2:
        out = ImageOps.autocontrast(out, cutoff=rng.randint(0, 2))
    # sensor noise
    if rng.random() < 0.4:
        arr = np.array(out).astype(np.int16)
        noise = np.random.default_rng(SEED + idx).integers(-18, 19, size=arr.shape)
        out = Image.fromarray(np.clip(arr + noise, 0, 255).astype(np.uint8))
    return out




def collect_real_photos(name: str) -> list[Image.Image]:
    """Download as many usable open-license images as possible for a class."""
    seen_urls: set[str] = set()
    bases: list[Image.Image] = []
    fail_streak = 0

    def add_url(title: str, url: str) -> bool:
        """Return False if we should abort the current query (rate-limit streak)."""
        nonlocal bases, fail_streak
        if len(bases) >= TARGET_REAL:
            return False
        if not url or url in seen_urls:
            return True
        seen_urls.add(url)
        key = f"{name}_{abs(hash(url)) % 10_000_000}"
        img = fetch_image(url, key)
        time.sleep(0.85)
        if img is not None:
            bases.append(img)
            fail_streak = 0
            if len(bases) % 5 == 0:
                print(f"  … {len(bases)} real photos")
            return True
        fail_streak += 1
        if fail_streak >= 8:
            print("  aborting query after 8 consecutive fetch failures (cool down)")
            time.sleep(25)
            fail_streak = 0
            return False
        return True

    # 1) Seed titles
    for title in COMMONS_SEED_FILES.get(name, []):
        if len(bases) >= TARGET_REAL:
            break
        url = commons_url_for_title(title)
        if url:
            add_url(title, url)
            print(f"  seed tried: {title} (have {len(bases)})")
        time.sleep(0.4)

    # 2) Commons search
    for q in COMMONS_QUERIES.get(name, []):
        if len(bases) >= TARGET_REAL:
            break
        hits = commons_search(q, limit=MAX_SEARCH_PER_QUERY)
        print(f"  commons search '{q}' → {len(hits)} candidates (have {len(bases)})")
        for title, url in hits:
            if len(bases) >= TARGET_REAL:
                break
            if not add_url(title, url):
                break
        time.sleep(1.0)

    # 3) Openverse supplement
    if len(bases) < TARGET_REAL:
        for q in COMMONS_QUERIES.get(name, [])[:5]:
            if len(bases) >= TARGET_REAL:
                break
            hits = openverse_search(q, limit=30)
            print(f"  openverse '{q}' → {len(hits)} candidates (have {len(bases)})")
            for title, url in hits:
                if len(bases) >= TARGET_REAL:
                    break
                if not add_url(title, url):
                    break
            time.sleep(0.6)

    return bases


def build_dataset():
    ensure_dirs()
    summary = {}
    for name, _kind in LABELS:
        print(f"\n=== Preparing class: {name} ===")
        real = collect_real_photos(name)
        synth_added = 0
        bases = list(real)
        if len(real) < MIN_REAL_SOFT:
            need = min(SYNTH_FILL_CAP, MIN_REAL_SOFT - len(real))
            for i in range(need):
                bases.append(make_synthetic(name, i))
                synth_added += 1
            print(f"  synthetic fill: {synth_added} (real={len(real)} < {MIN_REAL_SOFT})")
        else:
            print(f"  no synthetic needed (real={len(real)})")

        # Shuffle then split real-heavy: ~80% train / 20% val of base photos
        rng = random.Random(SEED + hash(name) % 997)
        order = list(range(len(bases)))
        rng.shuffle(order)
        n_val_bases = max(2, int(round(len(bases) * 0.2)))
        val_idx = set(order[:n_val_bases])
        train_count = 0
        val_count = 0
        for bi, bidx in enumerate(range(len(bases))):
            src = bases[bidx]
            is_val = bidx in val_idx
            n_aug = AUG_PER_BASE_VAL if is_val else AUG_PER_BASE_TRAIN
            for a in range(n_aug):
                aug = augment(src, bi * 31 + a * 7 + (hash(name) % 997))
                split = "val" if is_val else "train"
                path = DATA / split / name / f"{name}_{bidx:03d}_{a:02d}.jpg"
                aug.save(path, quality=90)
                if is_val:
                    val_count += 1
                else:
                    train_count += 1

        summary[name] = {
            "commons_ok": len(real),
            "synthetic_fill": synth_added,
            "base_pool": len(bases),
            "train_images": train_count,
            "val_images": val_count,
        }
        print(
            f"  wrote train={train_count} val={val_count}; "
            f"commons={len(real)} synth={synth_added} pool={len(bases)}"
        )

    (WORK / "dataset_summary.json").write_text(json.dumps(summary, indent=2))
    print("\nDataset summary:", json.dumps(summary, indent=2))
    return summary


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
    # On-the-fly keras augmentation (train only) — stacks on PIL augs
    keras_aug = keras.Sequential(
        [
            layers.RandomFlip("horizontal"),
            layers.RandomRotation(0.12),
            layers.RandomZoom(0.15),
            layers.RandomContrast(0.2),
            layers.RandomTranslation(0.08, 0.08),
        ],
        name="strong_aug",
    )

    autotune = tf.data.AUTOTUNE

    def train_map(x, y):
        x = tf.cast(x, tf.float32) / 255.0
        x = keras_aug(x, training=True)
        x = tf.clip_by_value(x, 0.0, 1.0)
        return x, y

    def val_map(x, y):
        return tf.cast(x, tf.float32) / 255.0, y

    train_ds = train_ds.map(train_map, num_parallel_calls=autotune).prefetch(autotune)
    val_ds = val_ds.map(val_map, num_parallel_calls=autotune).prefetch(autotune)
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
    x = layers.Dropout(0.35)(x)
    x = layers.Dense(128, activation="relu")(x)
    x = layers.Dropout(0.25)(x)
    outputs = layers.Dense(num_classes, activation="softmax")(x)
    model = keras.Model(inputs, outputs)
    model.compile(
        optimizer=keras.optimizers.Adam(1e-3),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model, base


class BestValLogger(keras.callbacks.Callback):
    def __init__(self):
        super().__init__()
        self.best = 0.0
        self.history = []

    def on_epoch_end(self, epoch, logs=None):
        logs = logs or {}
        va = float(logs.get("val_accuracy") or 0.0)
        self.history.append(va)
        if va > self.best:
            self.best = va
        print(f"  [epoch {epoch + 1}] val_accuracy={va:.4f} best={self.best:.4f}")


def train():
    train_ds, val_ds = make_tf_datasets()
    model, base = build_model(len(LABELS))
    best_logger = BestValLogger()
    cb_head = [
        keras.callbacks.EarlyStopping(
            patience=5, restore_best_weights=True, monitor="val_accuracy", mode="max"
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=2, min_lr=1e-6, verbose=1
        ),
        keras.callbacks.ModelCheckpoint(
            str(CKPT / "best_head.keras"),
            save_best_only=True,
            monitor="val_accuracy",
            mode="max",
        ),
        best_logger,
    ]
    print("Phase 1: train head (base frozen)…")
    hist1 = model.fit(
        train_ds, validation_data=val_ds, epochs=HEAD_EPOCHS, callbacks=cb_head
    )

    print("Phase 2: fine-tune top MobileNetV2 layers…")
    base.trainable = True
    # Freeze early layers; fine-tune last ~50
    freeze_until = max(0, len(base.layers) - 50)
    for i, layer in enumerate(base.layers):
        layer.trainable = i >= freeze_until
    model.compile(
        optimizer=keras.optimizers.Adam(5e-5),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    cb_ft = [
        keras.callbacks.EarlyStopping(
            patience=5, restore_best_weights=True, monitor="val_accuracy", mode="max"
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=2, min_lr=1e-7, verbose=1
        ),
        keras.callbacks.ModelCheckpoint(
            str(CKPT / "best_ft.keras"),
            save_best_only=True,
            monitor="val_accuracy",
            mode="max",
        ),
        best_logger,
    ]
    hist2 = model.fit(
        train_ds, validation_data=val_ds, epochs=FT_EPOCHS, callbacks=cb_ft
    )
    model.save(CKPT / "final.keras")

    # Final eval
    loss, acc = model.evaluate(val_ds, verbose=0)
    print(f"FINAL val_accuracy={acc:.4f} val_loss={loss:.4f} best_seen={best_logger.best:.4f}")
    metrics = {
        "final_val_accuracy": float(acc),
        "final_val_loss": float(loss),
        "best_val_accuracy_seen": float(best_logger.best),
        "head_epochs_ran": len(hist1.history.get("val_accuracy", [])),
        "ft_epochs_ran": len(hist2.history.get("val_accuracy", [])),
        "val_accuracy_curve": best_logger.history,
    }
    (WORK / "train_metrics.json").write_text(json.dumps(metrics, indent=2))
    return model, metrics


def export_tflite(model: keras.Model, metrics: dict, summary: dict):
    def rep():
        files = list((DATA / "train").rglob("*.jpg"))
        random.Random(SEED).shuffle(files)
        for p in files[:120]:
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
        "architecture": (
            "MobileNetV2 ImageNet transfer; frozen head then fine-tune last ~50 layers; "
            "dense(128)+softmax; dynamic-range quantized"
        ),
        "size_bytes": len(tflite_model),
        "val_accuracy": metrics.get("final_val_accuracy"),
        "best_val_accuracy": metrics.get("best_val_accuracy_seen"),
        "dataset_summary": summary,
        "source": (
            "Wikimedia Commons photos (maximized search) + minimal synthetic fill; "
            "tools/wildlife_tflite/train_and_export.py"
        ),
        "retrain": "2.3.3-wildlife-retrain",
    }
    (ASSETS / "model_meta.json").write_text(json.dumps(meta, indent=2))
    (WORK / "export_meta.json").write_text(json.dumps(meta, indent=2))
    print(f"Wrote {out} ({len(tflite_model)} bytes / {len(tflite_model)/1024/1024:.2f} MB)")
    return out, len(tflite_model)


def verify_interpreter(path: Path):
    interpreter = tf.lite.Interpreter(model_path=str(path))
    interpreter.allocate_tensors()
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]
    print("Input:", inp["shape"], inp["dtype"])
    print("Output:", out["shape"], out["dtype"])
    assert list(inp["shape"]) == [1, IMG_SIZE, IMG_SIZE, 3]
    assert int(out["shape"][-1]) == len(LABELS)
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
    if DATA.exists():
        import shutil

        shutil.rmtree(DATA)
    summary = build_dataset()
    model, metrics = train()
    path, size = export_tflite(model, metrics, summary)
    verify_interpreter(path)
    print(
        "DONE size_mb=",
        round(size / (1024 * 1024), 2),
        "val_acc=",
        metrics.get("final_val_accuracy"),
        "best=",
        metrics.get("best_val_accuracy_seen"),
    )


if __name__ == "__main__":
    main()
