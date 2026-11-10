#!/usr/bin/env bash
# Downloads the face recognition model (MobileFaceNet "w600k_mbf", 512-d,
# from InsightFace's buffalo_s pack) into the app's assets, verifying hashes.
#
# LICENSE: InsightFace pretrained models are for NON-COMMERCIAL research use
# only. This app is for personal use. The weights are downloaded at build time
# and are not stored in this repository.
set -euo pipefail
URL="https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_s.zip"
ZIP_SHA256="d85a87f503f691807cd8bb97128bdf7a0660326cd9cd02657127fa978bab8b5e"
MODEL_SHA256="9cc6e4a75f0e2bf0b1aed94578f144d15175f357bdc05e815e5c4a02b319eb4f"
OUT="${1:-android/app/src/main/assets/models/face}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL --retry 5 -o "$tmp/buffalo_s.zip" "$URL"
echo "$ZIP_SHA256  $tmp/buffalo_s.zip" | sha256sum -c -
unzip -o -j "$tmp/buffalo_s.zip" w600k_mbf.onnx -d "$tmp"
echo "$MODEL_SHA256  $tmp/w600k_mbf.onnx" | sha256sum -c -
mkdir -p "$OUT"
cp "$tmp/w600k_mbf.onnx" "$OUT/w600k_mbf.onnx"
ls -la "$OUT"
