#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 /path/to/cat-assets" >&2
  exit 2
fi

source_dir="$1"
project_root="$(cd "$(dirname "$0")/../.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

web_root="$project_root/public/mascots/cats"
design_root="$project_root/design/mascots"
mkdir -p "$web_root" "$design_root/psd" "$design_root/originals" "$design_root/sheets"

for sheet in a b c; do
  test -f "$source_dir/sheet-$sheet.png"
  cp "$source_dir/sheet-$sheet.png" "$design_root/sheets/sheet-$sheet.png"
done

# sheet|cell-x|cell-y|slug|head ellipse|tail polygon
specs=(
  'a|0|0|presets|ellipse 284,156 145,142 0,360|polygon 0,82 154,82 178,232 132,352 0,352'
  'a|1|0|five-ar|ellipse 310,166 147,146 0,360|polygon 390,28 512,28 512,300 396,300 370,176'
  'a|0|1|transdermal-e2|ellipse 154,170 150,152 0,360|polygon 330,44 512,44 512,340 358,340 324,204'
  'a|1|1|drug-settings|ellipse 242,258 145,142 0,360|polygon 384,0 512,0 512,224 404,224 360,100'
  'b|0|0|model-selection|ellipse 246,146 148,150 0,360|polygon 0,214 120,214 158,414 0,414'
  'b|1|0|bayesian|ellipse 260,158 148,148 0,360|polygon 392,42 512,42 512,316 402,316 372,176'
  'b|0|1|progestogen|ellipse 304,220 160,150 0,360|polygon 0,108 190,108 204,430 0,430'
  'b|1|1|interactions|ellipse 294,164 154,148 0,360|polygon 398,0 512,0 512,254 414,254 382,112'
  'c|0|0|monte-carlo|ellipse 242,226 165,158 0,360|polygon 0,0 214,0 208,174 42,190 0,144'
  'c|1|0|pk-parameters|ellipse 238,170 155,150 0,360|polygon 390,20 512,20 512,286 404,286 370,144'
  'c|0|1|simulation-controls|ellipse 230,176 154,150 0,360|polygon 0,0 170,0 174,246 24,254 0,188'
  'c|1|1|results|ellipse 188,206 158,158 0,360|polygon 340,0 512,0 512,298 370,298 330,142'
)

make_layer() {
  local source_png="$1"
  local mask_png="$2"
  local output_png="$3"
  local alpha_png="$work_dir/alpha-$(basename "$output_png")"

  convert "$source_png" -alpha extract "$mask_png" -compose multiply -composite "$alpha_png"
  convert "$source_png" "$alpha_png" -alpha off -compose CopyOpacity -composite \
    -strip -define png:compression-level=9 "PNG32:$output_png"
}

for spec in "${specs[@]}"; do
  IFS='|' read -r sheet cell_x cell_y slug head_draw tail_draw <<< "$spec"
  source_png="$design_root/originals/$slug.png"
  cat_dir="$web_root/$slug"
  mkdir -p "$cat_dir"

  offset_x=$((cell_x * 512))
  offset_y=$((cell_y * 512))
  convert "$source_dir/sheet-$sheet.png" -crop "512x512+$offset_x+$offset_y" +repage "$source_png"

  head_mask="$work_dir/$slug-head-mask.png"
  tail_raw="$work_dir/$slug-tail-raw.png"
  tail_mask="$work_dir/$slug-tail-mask.png"
  union_mask="$work_dir/$slug-union-mask.png"
  body_mask="$work_dir/$slug-body-mask.png"

  convert -size 512x512 xc:black +antialias -fill white -draw "$head_draw" "$head_mask"
  convert -size 512x512 xc:black +antialias -fill white -draw "$tail_draw" "$tail_raw"
  convert "$tail_raw" "$head_mask" -fx 'u*(1-v)' "$tail_mask"
  convert "$head_mask" "$tail_mask" -evaluate-sequence max "$union_mask"
  convert "$union_mask" -negate "$body_mask"

  make_layer "$source_png" "$body_mask" "$cat_dir/body.png"
  make_layer "$source_png" "$tail_mask" "$cat_dir/tail.png"
  make_layer "$source_png" "$head_mask" "$cat_dir/head.png"

  convert \
    \( "$cat_dir/body.png" -set label body \) \
    \( "$cat_dir/tail.png" -set label tail \) \
    \( "$cat_dir/head.png" -set label head \) \
    "$design_root/psd/$slug.psd"
done

echo "Built ${#specs[@]} layered cats."
