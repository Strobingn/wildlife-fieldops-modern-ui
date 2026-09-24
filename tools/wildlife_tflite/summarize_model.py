#!/usr/bin/env python3
"""Print a graph-level summary of a TFLite flatbuffer.

Stdlib only (no TensorFlow / tflite-runtime). Parses the official Model table
so mixed-precision / Flex / tensor-type questions can be answered from the
compiled graph rather than the Keras layer list.

Default target: app/src/main/assets/models/wildlife_evidence.tflite

Usage:
  python3 tools/wildlife_tflite/summarize_model.py
  python3 tools/wildlife_tflite/summarize_model.py --json
  python3 tools/wildlife_tflite/summarize_model.py --check
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MODEL = ROOT / "app" / "src" / "main" / "assets" / "models" / "wildlife_evidence.tflite"

TENSOR_TYPES = {
    0: "FLOAT32",
    1: "FLOAT16",
    2: "INT32",
    3: "UINT8",
    4: "INT64",
    5: "STRING",
    6: "BOOL",
    7: "INT16",
    8: "COMPLEX64",
    9: "INT8",
    10: "FLOAT64",
    11: "COMPLEX128",
    12: "UINT64",
    13: "RESOURCE",
    14: "VARIANT",
    15: "UINT32",
    16: "UINT16",
    17: "INT4",
}

# tensorflow/lite/schema/schema.fbs BuiltinOperator (codes used by MobileNet-class
# graphs plus CUSTOM / DELEGATE / QUANTIZE). Unknown codes print as BUILTIN_<n>.
BUILTIN_OPS = {
    0: "ADD",
    1: "AVERAGE_POOL_2D",
    2: "CONCATENATION",
    3: "CONV_2D",
    4: "DEPTHWISE_CONV_2D",
    5: "DEPTH_TO_SPACE",
    6: "DEQUANTIZE",
    7: "EMBEDDING_LOOKUP",
    8: "FLOOR",
    9: "FULLY_CONNECTED",
    10: "HASHTABLE_LOOKUP",
    11: "L2_NORMALIZATION",
    12: "L2_POOL_2D",
    13: "LOCAL_RESPONSE_NORMALIZATION",
    14: "LOGISTIC",
    15: "LSH_PROJECTION",
    16: "LSTM",
    17: "MAX_POOL_2D",
    18: "MUL",
    19: "RELU",
    20: "RELU_N1_TO_1",
    21: "RELU6",
    22: "RESHAPE",
    23: "RESIZE_BILINEAR",
    24: "RNN",
    25: "SOFTMAX",
    26: "SPACE_TO_DEPTH",
    27: "SVDF",
    28: "TANH",
    29: "CONCAT_EMBEDDINGS",
    30: "SKIP_GRAM",
    31: "CALL",
    32: "CUSTOM",
    33: "EMBEDDING_LOOKUP_SPARSE",
    34: "PAD",
    35: "UNIDIRECTIONAL_SEQUENCE_RNN",
    36: "GATHER",
    37: "BATCH_TO_SPACE_ND",
    38: "SPACE_TO_BATCH_ND",
    39: "TRANSPOSE",
    40: "MEAN",
    41: "SUB",
    42: "DIV",
    43: "SQUEEZE",
    44: "UNIDIRECTIONAL_SEQUENCE_LSTM",
    45: "STRIDED_SLICE",
    46: "BIDIRECTIONAL_SEQUENCE_RNN",
    47: "EXP",
    48: "TOPK_V2",
    49: "SPLIT",
    50: "LOG_SOFTMAX",
    51: "DELEGATE",
    52: "BIDIRECTIONAL_SEQUENCE_LSTM",
    53: "CAST",
    54: "PRELU",
    55: "MAXIMUM",
    56: "ARG_MAX",
    57: "MINIMUM",
    58: "LESS",
    59: "NEG",
    60: "PADV2",
    61: "GREATER",
    62: "GREATER_EQUAL",
    63: "LESS_EQUAL",
    64: "SELECT",
    65: "SLICE",
    66: "SIN",
    67: "TRANSPOSE_CONV",
    68: "SPARSE_TO_DENSE",
    69: "TILE",
    70: "EXPAND_DIMS",
    71: "EQUAL",
    72: "NOT_EQUAL",
    73: "LOG",
    74: "SUM",
    75: "SQRT",
    76: "RSQRT",
    77: "SHAPE",
    78: "POW",
    79: "ARG_MIN",
    80: "FAKE_QUANT",
    81: "REDUCE_PROD",
    82: "REDUCE_MAX",
    83: "PACK",
    84: "LOGICAL_OR",
    85: "ONE_HOT",
    86: "LOGICAL_AND",
    87: "LOGICAL_NOT",
    88: "UNPACK",
    89: "REDUCE_MIN",
    90: "FLOOR_DIV",
    91: "REDUCE_ANY",
    92: "SQUARE",
    93: "ZEROS_LIKE",
    94: "FILL",
    95: "FLOOR_MOD",
    96: "RANGE",
    97: "RESIZE_NEAREST_NEIGHBOR",
    98: "LEAKY_RELU",
    99: "SQUARED_DIFFERENCE",
    100: "MIRROR_PAD",
    101: "ABS",
    102: "SPLIT_V",
    103: "UNIQUE",
    104: "CEIL",
    105: "REVERSE_V2",
    106: "ADD_N",
    107: "GATHER_ND",
    108: "COS",
    109: "WHERE",
    110: "RANK",
    111: "ELU",
    112: "REVERSE_SEQUENCE",
    113: "MATRIX_DIAG",
    114: "QUANTIZE",
    115: "MATRIX_SET_DIAG",
    116: "ROUND",
    117: "HARD_SWISH",
    118: "IF",
    119: "WHILE",
    120: "NON_MAX_SUPPRESSION_V4",
    121: "NON_MAX_SUPPRESSION_V5",
    122: "SCATTER_ND",
    123: "SELECT_V2",
    124: "DENSIFY",
    125: "SEGMENT_SUM",
    126: "BATCH_MATMUL",
    128: "CUMSUM",
    129: "CALL_ONCE",
    130: "BROADCAST_TO",
    131: "RFFT2D",
    132: "CONV_3D",
    133: "IMAG",
    134: "REAL",
    135: "COMPLEX_ABS",
    136: "HASHTABLE",
    137: "HASHTABLE_FIND",
    138: "HASHTABLE_IMPORT",
    139: "HASHTABLE_SIZE",
    140: "REDUCE_ALL",
    141: "CONV_3D_TRANSPOSE",
    142: "VAR_HANDLE",
    143: "READ_VARIABLE",
    144: "ASSIGN_VARIABLE",
    145: "BROADCAST_ARGS",
    146: "RANDOM_STANDARD_NORMAL",
    147: "BUCKETIZE",
    148: "RANDOM_UNIFORM",
    149: "MULTINOMIAL",
    150: "GELU",
    151: "DYNAMIC_UPDATE_SLICE",
    152: "RELU_0_TO_1",
}


def _u8(buf: bytes, off: int) -> int:
    return buf[off]


def _i8(buf: bytes, off: int) -> int:
    return int.from_bytes(buf[off : off + 1], "little", signed=True)


def _u16(buf: bytes, off: int) -> int:
    return int.from_bytes(buf[off : off + 2], "little")


def _u32(buf: bytes, off: int) -> int:
    return int.from_bytes(buf[off : off + 4], "little")


def _i32(buf: bytes, off: int) -> int:
    return int.from_bytes(buf[off : off + 4], "little", signed=True)


def _i64(buf: bytes, off: int) -> int:
    return int.from_bytes(buf[off : off + 8], "little", signed=True)


def _f32(buf: bytes, off: int) -> float:
    return struct.unpack_from("<f", buf, off)[0]


class Table:
    def __init__(self, buf: bytes, pos: int):
        self.buf = buf
        self.pos = pos
        self.vtable = pos - _i32(buf, pos)
        self.vtable_size = _u16(buf, self.vtable)

    def field(self, idx: int) -> int:
        slot = 4 + idx * 2
        if slot + 2 > self.vtable_size:
            return 0
        rel = _u16(self.buf, self.vtable + slot)
        return 0 if rel == 0 else self.pos + rel

    def u8(self, idx: int, default: int = 0) -> int:
        off = self.field(idx)
        return default if off == 0 else _u8(self.buf, off)

    def i8(self, idx: int, default: int = 0) -> int:
        off = self.field(idx)
        return default if off == 0 else _i8(self.buf, off)

    def u32(self, idx: int, default: int = 0) -> int:
        off = self.field(idx)
        return default if off == 0 else _u32(self.buf, off)

    def i32(self, idx: int, default: int = 0) -> int:
        off = self.field(idx)
        return default if off == 0 else _i32(self.buf, off)

    def bool(self, idx: int, default: bool = False) -> bool:
        off = self.field(idx)
        return default if off == 0 else bool(_u8(self.buf, off))

    def string(self, idx: int) -> str | None:
        off = self.field(idx)
        if off == 0:
            return None
        start = off + _u32(self.buf, off)
        n = _u32(self.buf, start)
        return self.buf[start + 4 : start + 4 + n].decode("utf-8", errors="replace")

    def vector_start(self, idx: int) -> int | None:
        off = self.field(idx)
        if off == 0:
            return None
        return off + _u32(self.buf, off)

    def vector_len(self, idx: int) -> int:
        start = self.vector_start(idx)
        return 0 if start is None else _u32(self.buf, start)

    def table_at_vector(self, idx: int, i: int) -> Table | None:
        start = self.vector_start(idx)
        if start is None:
            return None
        n = _u32(self.buf, start)
        if i >= n:
            return None
        slot = start + 4 + i * 4
        return Table(self.buf, slot + _u32(self.buf, slot))

    def i32_vector(self, idx: int) -> list[int]:
        start = self.vector_start(idx)
        if start is None:
            return []
        n = _u32(self.buf, start)
        base = start + 4
        return [_i32(self.buf, base + i * 4) for i in range(n)]

    def f32_vector(self, idx: int) -> list[float]:
        start = self.vector_start(idx)
        if start is None:
            return []
        n = _u32(self.buf, start)
        base = start + 4
        return [_f32(self.buf, base + i * 4) for i in range(n)]

    def i64_vector(self, idx: int) -> list[int]:
        start = self.vector_start(idx)
        if start is None:
            return []
        n = _u32(self.buf, start)
        base = start + 4
        return [_i64(self.buf, base + i * 8) for i in range(n)]


def _root_table(buf: bytes) -> Table:
    if len(buf) < 8:
        raise ValueError("file too small to be a TFLite flatbuffer")
    ident = buf[4:8]
    if ident not in (b"TFL3", b"TFL2", b"\x00\x00\x00\x00"):
        # Still try: some builders omit the file identifier.
        pass
    return Table(buf, _u32(buf, 0))


def _opcode_name(code: Table) -> str:
    deprecated = code.i8(0, 0)
    custom = code.string(1)
    builtin = code.i32(3, 0)
    resolved = max(deprecated, builtin)
    if resolved == 32 or (custom and resolved == 0):
        return f"CUSTOM:{custom}" if custom else "CUSTOM"
    return BUILTIN_OPS.get(resolved, f"BUILTIN_{resolved}")


def _tensor_type(code: int) -> str:
    return TENSOR_TYPES.get(code, f"TYPE_{code}")


def _quant_summary(tensor: Table) -> dict | None:
    off = tensor.field(4)
    if off == 0:
        return None
    qpos = off + _u32(tensor.buf, off)
    q = Table(tensor.buf, qpos)
    scales = q.f32_vector(2)
    zero_points = q.i64_vector(3)
    qdim = q.i32(6, 0)
    if not scales and not zero_points:
        return None
    return {
        "scale_count": len(scales),
        "scale0": scales[0] if scales else None,
        "zero_point0": zero_points[0] if zero_points else None,
        "quantized_dimension": qdim,
        "per_axis": len(scales) > 1,
    }


def summarize(path: Path) -> dict:
    data = path.read_bytes()
    model = _root_table(data)
    version = model.u32(0)
    description = model.string(3)
    n_opcodes = model.vector_len(1)
    opcodes = [_opcode_name(model.table_at_vector(1, i)) for i in range(n_opcodes)]
    n_subgraphs = model.vector_len(2)
    metadata = []
    for i in range(model.vector_len(6)):
        meta = model.table_at_vector(6, i)
        if meta is None:
            continue
        name = meta.string(0)
        buf_idx = meta.u32(1)
        value = None
        buffers_start = model.vector_start(4)
        if buffers_start is not None:
            n_buf = _u32(data, buffers_start)
            if buf_idx < n_buf:
                btable = model.table_at_vector(4, buf_idx)
                if btable is not None:
                    raw_start = btable.vector_start(0)
                    if raw_start is not None:
                        n = _u32(data, raw_start)
                        raw = data[raw_start + 4 : raw_start + 4 + n]
                        text = raw.decode("utf-8", errors="replace").rstrip("\x00")
                        if name == "CONVERSION_METADATA" or not text.isprintable():
                            versions = re.findall(rb"\d+\.\d+\.\d+", raw)
                            if versions:
                                value = "tensorflow " + versions[-1].decode("ascii")
                            else:
                                value = f"binary:{len(raw)}B"
                        else:
                            value = text
        metadata.append({"name": name, "buffer": buf_idx, "value": value})

    subgraphs = []
    op_counter: Counter[str] = Counter()
    tensor_type_counter: Counter[str] = Counter()
    flex_ops: list[str] = []
    custom_ops: list[str] = []
    quantized_activation_tensors = 0
    quantized_weight_like = 0

    for sg_i in range(n_subgraphs):
        sg = model.table_at_vector(2, sg_i)
        assert sg is not None
        tensors = []
        for t_i in range(sg.vector_len(0)):
            t = sg.table_at_vector(0, t_i)
            assert t is not None
            ttype = _tensor_type(t.i8(1, 0))
            tensor_type_counter[ttype] += 1
            q = _quant_summary(t)
            if q:
                # Weight buffers typically have per-axis scales; activations are scalar.
                if q["per_axis"]:
                    quantized_weight_like += 1
                else:
                    quantized_activation_tensors += 1
            tensors.append(
                {
                    "index": t_i,
                    "name": t.string(3),
                    "shape": t.i32_vector(0),
                    "type": ttype,
                    "buffer": t.u32(2),
                    "is_variable": t.bool(5, False),
                    "quantization": q,
                }
            )
        inputs = sg.i32_vector(1)
        outputs = sg.i32_vector(2)
        operators = []
        for o_i in range(sg.vector_len(3)):
            op = sg.table_at_vector(3, o_i)
            assert op is not None
            opcode_index = op.u32(0)
            name = opcodes[opcode_index] if opcode_index < len(opcodes) else f"OPCODE_{opcode_index}"
            op_counter[name] += 1
            if name.startswith("CUSTOM:"):
                custom_ops.append(name)
                if "Flex" in name:
                    flex_ops.append(name)
            operators.append(
                {
                    "index": o_i,
                    "op": name,
                    "opcode_index": opcode_index,
                    "inputs": op.i32_vector(1),
                    "outputs": op.i32_vector(2),
                }
            )
        subgraphs.append(
            {
                "index": sg_i,
                "name": sg.string(4),
                "inputs": inputs,
                "outputs": outputs,
                "input_tensors": [tensors[i] for i in inputs if 0 <= i < len(tensors)],
                "output_tensors": [tensors[i] for i in outputs if 0 <= i < len(tensors)],
                "tensor_count": len(tensors),
                "operator_count": len(operators),
                "operators": operators,
                "tensors": tensors,
            }
        )

    sha256 = hashlib.sha256(data).hexdigest()
    return {
        "path": str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path),
        "size_bytes": len(data),
        "sha256": sha256,
        "file_identifier": data[4:8].decode("ascii", errors="replace"),
        "tflite_version": version,
        "description": description,
        "metadata": metadata,
        "operator_codes": opcodes,
        "operator_histogram": dict(op_counter.most_common()),
        "tensor_type_histogram": dict(tensor_type_counter),
        "flex_ops": flex_ops,
        "custom_ops": custom_ops,
        "has_flex_ops": bool(flex_ops),
        "quantized_activation_tensors": quantized_activation_tensors,
        "quantized_weight_like_tensors": quantized_weight_like,
        "subgraph_count": n_subgraphs,
        "subgraphs": subgraphs,
    }


def _fmt_tensor(t: dict) -> str:
    q = t.get("quantization")
    qtxt = ""
    if q:
        kind = "per-axis" if q["per_axis"] else "scalar"
        qtxt = f" quant={kind} scale0={q['scale0']} zp0={q['zero_point0']}"
    return f"{t['index']}: {t['name']} {t['type']} {t['shape']}{qtxt}"


def print_text(summary: dict) -> None:
    print(f"path:          {summary['path']}")
    print(f"size_bytes:    {summary['size_bytes']} ({summary['size_bytes'] / (1024 * 1024):.2f} MiB)")
    print(f"sha256:        {summary['sha256']}")
    print(f"identifier:    {summary['file_identifier']}")
    print(f"tflite_ver:    {summary['tflite_version']}")
    print(f"description:   {summary['description'] or '(none)'}")
    print("metadata:")
    if not summary["metadata"]:
        print("  (none)")
    for m in summary["metadata"]:
        print(f"  {m['name']}: {m['value']}")
    print(f"flex_ops:      {summary['flex_ops'] or 'none'}")
    print(f"custom_ops:    {summary['custom_ops'] or 'none'}")
    print(f"tensor_types:  {summary['tensor_type_histogram']}")
    print(
        "quant_tensors: "
        f"activations_or_scalar={summary['quantized_activation_tensors']} "
        f"weight_like_per_axis={summary['quantized_weight_like_tensors']}"
    )
    print("operator_histogram:")
    for name, count in summary["operator_histogram"].items():
        print(f"  {count:4d}  {name}")
    for sg in summary["subgraphs"]:
        print(f"subgraph[{sg['index']}] name={sg['name']!r} tensors={sg['tensor_count']} ops={sg['operator_count']}")
        print("  inputs:")
        for t in sg["input_tensors"]:
            print(f"    {_fmt_tensor(t)}")
        print("  outputs:")
        for t in sg["output_tensors"]:
            print(f"    {_fmt_tensor(t)}")
        print("  operators:")
        for op in sg["operators"]:
            print(f"    {op['index']:3d}  {op['op']}  in={op['inputs']}  out={op['outputs']}")


def check_production_baseline(summary: dict) -> int:
    """Fail if the checked-in wildlife_evidence.tflite no longer matches the CPU baseline."""
    errors: list[str] = []
    sg = summary["subgraphs"][0]
    ins = sg["input_tensors"]
    outs = sg["output_tensors"]
    if summary["size_bytes"] != 2879552:
        errors.append(f"size_bytes {summary['size_bytes']} != 2879552")
    if summary["sha256"] != "cabf6387a3f70f67d42cfadb5b3ce49e5f49fb0b6eba07da1de1eaba751bcb98":
        errors.append(f"sha256 changed: {summary['sha256']}")
    if len(ins) != 1 or ins[0]["shape"] != [1, 224, 224, 3] or ins[0]["type"] != "FLOAT32":
        errors.append(f"unexpected input: {ins}")
    if len(outs) != 1 or outs[0]["shape"] != [1, 12] or outs[0]["type"] != "FLOAT32":
        errors.append(f"unexpected output: {outs}")
    if summary["has_flex_ops"]:
        errors.append(f"Flex ops present: {summary['flex_ops']}")
    if summary["custom_ops"]:
        errors.append(f"CUSTOM ops present: {summary['custom_ops']}")
    if errors:
        print("CHECK FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print("CHECK OK: wildlife_evidence.tflite matches the documented CPU admission baseline.")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", nargs="?", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    parser.add_argument(
        "--check",
        action="store_true",
        help="assert the production wildlife_evidence.tflite identity/shapes",
    )
    args = parser.parse_args(argv)
    path = args.model.resolve()
    if not path.is_file():
        print(f"model not found: {path}", file=sys.stderr)
        return 2
    summary = summarize(path)
    if args.check:
        return check_production_baseline(summary)
    if args.json:
        slim = dict(summary)
        # Keep JSON useful but not huge: drop per-tensor list except I/O.
        slim_subgraphs = []
        for sg in slim["subgraphs"]:
            slim_subgraphs.append(
                {
                    k: v
                    for k, v in sg.items()
                    if k != "tensors"
                }
            )
        slim["subgraphs"] = slim_subgraphs
        json.dump(slim, sys.stdout, indent=2)
        sys.stdout.write("\n")
    else:
        print_text(summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
