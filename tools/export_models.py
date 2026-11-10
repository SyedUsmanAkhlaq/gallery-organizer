#!/usr/bin/env python3
"""Export the clothing classifier for the Android app.

Produces, in --out (default android/app/src/main/assets/models/clothing):
  image_encoder.onnx    MobileCLIP2-S0 image tower; output is the
                        L2-normalized embedding (normalization is in the graph)
  text_embeddings.json  one ensembled, L2-normalized text embedding per
                        category, plus preprocessing config and versions

Both come from the SAME open_clip model, which zero-shot classification
requires. Run:  python tools/export_models.py
CI runs this automatically when this file or categories.yaml changes.
Colab: tools/export_models_colab.ipynb
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

import numpy as np
import torch
import yaml

# MobileCLIP2-S0: ~11M-param image tower (FastViT MCi0), 512-d, 256 px input.
# (open_clip 4.x replaced MobileCLIP-S0 with this better successor.)
MODEL_NAME = "MobileCLIP2-S0"
PRETRAINED = "dfndr2b"
OPSET = 17
# Bump when the export procedure changes (forces a full clothing re-scan).
EXPORT_REVISION = 1
# CLIP-style temperature used by the app: softmax(100 * cosine).
LOGIT_SCALE = 100.0

ROOT = Path(__file__).resolve().parent.parent


def l2(x: torch.Tensor) -> torch.Tensor:
    return x / x.norm(dim=-1, keepdim=True)


def load_model():
    import open_clip

    model, _, _ = open_clip.create_model_and_transforms(MODEL_NAME, pretrained=PRETRAINED)
    model.eval()
    # MobileCLIP uses re-parameterizable blocks; fold them for inference.
    try:
        from timm.utils import reparameterize_model

        model = reparameterize_model(model)
    except Exception as e:  # pragma: no cover - older timm
        print(f"note: reparameterize_model unavailable ({e}); exporting as-is", file=sys.stderr)
    tokenizer = open_clip.get_tokenizer(MODEL_NAME)
    # Preprocessing must match the pretrained weights (MobileCLIP2 uses
    # mean 0 / std 1, not CLIP's mean/std): take it from open_clip.
    cfg = dict(open_clip.get_pretrained_cfg(MODEL_NAME, PRETRAINED) or {})
    cfg.update({k: v for k, v in (getattr(model.visual, "preprocess_cfg", {}) or {}).items() if k not in cfg})
    if "size" not in cfg:
        cfg["size"] = open_clip.get_model_config(MODEL_NAME)["vision_cfg"]["image_size"]
    return model, tokenizer, cfg


class ImageEncoder(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, pixels: torch.Tensor) -> torch.Tensor:
        return l2(self.model.encode_image(pixels))


def labels_version(categories: list[dict]) -> str:
    canon = json.dumps(
        [[c["key"], bool(c.get("clothing", True)), list(c["prompts"])] for c in categories],
        sort_keys=True,
    )
    return hashlib.sha256(canon.encode()).hexdigest()[:12]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--categories", default=str(ROOT / "tools" / "categories.yaml"))
    ap.add_argument("--out", default=str(ROOT / "android/app/src/main/assets/models/clothing"))
    ap.add_argument("--skip-image", action="store_true", help="only rebuild text_embeddings.json")
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    categories = yaml.safe_load(Path(args.categories).read_text())["categories"]
    keys = [c["key"] for c in categories]
    assert len(keys) == len(set(keys)), "duplicate category keys"
    assert any(not c.get("clothing", True) for c in categories), "need at least one non-clothing bucket"

    torch.manual_seed(0)
    model, tokenizer, cfg = load_model()
    size = cfg.get("size", 224)
    size = [size, size] if isinstance(size, int) else list(size)
    mean = list(cfg.get("mean", (0.48145466, 0.4578275, 0.40821073)))
    std = list(cfg.get("std", (0.26862954, 0.26130258, 0.27577711)))
    print(f"preprocess: size={size} mean={mean} std={std} resize_mode={cfg.get('resize_mode')}")

    # ---- text: prompt ensembling ------------------------------------------
    entries = []
    with torch.no_grad():
        for c in categories:
            tokens = tokenizer(list(c["prompts"]))
            per_prompt = l2(model.encode_text(tokens).float())  # encode, L2
            emb = l2(per_prompt.mean(dim=0, keepdim=True))[0]    # average, L2
            entries.append(
                {
                    "key": c["key"],
                    "label": c.get("label", c["key"]),
                    "clothing": bool(c.get("clothing", True)),
                    "embedding": [round(float(v), 6) for v in emb],
                }
            )
    dim = len(entries[0]["embedding"])

    # ---- image encoder ------------------------------------------------------
    encoder_version = f"{MODEL_NAME}-{PRETRAINED}-op{OPSET}-r{EXPORT_REVISION}"
    onnx_path = out / "image_encoder.onnx"
    if not args.skip_image:
        enc = ImageEncoder(model).eval()
        dummy = torch.rand(1, 3, size[0], size[1])
        export_kwargs = dict(
            input_names=["pixels"],
            output_names=["embedding"],
            dynamic_axes={"pixels": {0: "batch"}, "embedding": {0: "batch"}},
            opset_version=OPSET,
            do_constant_folding=True,
        )
        try:
            torch.onnx.export(enc, dummy, str(onnx_path), dynamo=False, **export_kwargs)
        except TypeError:  # older torch without the dynamo flag
            torch.onnx.export(enc, dummy, str(onnx_path), **export_kwargs)
        # Keep weights inside the single .onnx file (no external data).
        import onnx

        m = onnx.load(str(onnx_path), load_external_data=True)
        onnx.save_model(m, str(onnx_path), save_as_external_data=False)
        for f in out.glob("*.data"):
            f.unlink()

        # Parity check against PyTorch.
        import onnxruntime as ort

        sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
        x = torch.rand(2, 3, size[0], size[1])
        with torch.no_grad():
            ref = enc(x).numpy()
        got = sess.run(None, {"pixels": x.numpy()})[0]
        err = float(np.abs(ref - got).max())
        print(f"onnx parity max abs err = {err:.2e}")
        assert err < 1e-3, "ONNX output differs from PyTorch"
        assert got.shape[1] == dim, f"image dim {got.shape[1]} != text dim {dim}"
        print(f"wrote {onnx_path} ({onnx_path.stat().st_size / 1e6:.1f} MB)")

    meta = {
        "format": 1,
        "model": MODEL_NAME,
        "pretrained": PRETRAINED,
        "encoder_version": encoder_version,
        "labels_version": labels_version(categories),
        "dim": dim,
        "image_size": size,
        "mean": mean,
        "std": std,
        "logit_scale": LOGIT_SCALE,
        "input_name": "pixels",
        "categories": entries,
    }
    (out / "text_embeddings.json").write_text(json.dumps(meta, separators=(",", ":")))
    print(f"wrote {out / 'text_embeddings.json'}: {len(entries)} categories, dim {dim}, "
          f"labels_version {meta['labels_version']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
