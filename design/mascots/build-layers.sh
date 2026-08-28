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
pixel_master=96
palette_limit=32
outline_color='#170f2d'
mkdir -p "$web_root" "$design_root/psd" "$design_root/originals" "$design_root/sheets"

for sheet in a b c d; do
  test -f "$source_dir/sheet-$sheet.png"
  cp "$source_dir/sheet-$sheet.png" "$design_root/sheets/sheet-$sheet.png"
done

# sheet|cell-x|cell-y|slug|head ellipse|tail polygon
specs=(
  'a|0|0|presets|ellipse 260,215 154,142 0,360|polygon 0,0 150,0 168,238 130,384 0,384'
  'a|1|0|five-ar|ellipse 302,172 150,142 0,360|polygon 390,24 512,24 512,318 396,318 370,172'
  'a|0|1|transdermal-e2|ellipse 154,174 150,145 0,360|polygon 330,80 512,80 512,386 360,386 324,202'
  'a|1|1|drug-settings|ellipse 248,270 150,142 0,360|polygon 386,0 512,0 512,242 404,242 360,88'
  'b|0|0|model-selection|ellipse 270,166 152,146 0,360|polygon 0,0 172,0 174,274 40,324 0,238'
  'b|1|0|bayesian|ellipse 270,176 150,145 0,360|polygon 396,40 512,40 512,324 402,324 374,176'
  'b|0|1|progestogen|ellipse 302,260 164,150 0,360|polygon 0,74 190,74 208,432 0,432'
  'b|1|1|interactions|ellipse 300,206 156,145 0,360|polygon 394,0 512,0 512,248 412,248 378,104'
  'c|0|0|monte-carlo|ellipse 242,210 166,154 0,360|polygon 0,0 216,0 210,176 34,198 0,148'
  'c|1|0|pk-parameters|ellipse 236,182 158,150 0,360|polygon 390,18 512,18 512,290 404,290 370,144'
  'c|0|1|simulation-controls|ellipse 236,184 156,150 0,360|polygon 0,0 170,0 176,254 24,266 0,190'
  'c|1|1|results|ellipse 198,192 162,154 0,360|polygon 340,0 512,0 512,310 372,310 330,144'
  'd|0|0|stats-summary|ellipse 240,190 154,145 0,360|polygon 320,184 512,184 512,472 344,472 306,326'
  'd|1|0|time-curves|ellipse 266,156 154,144 0,360|polygon 392,20 512,20 512,326 400,326 372,170'
  'd|0|1|event-log|ellipse 182,190 154,144 0,360|polygon 280,160 512,160 512,438 304,438 276,270'
  'd|1|1|research-footer|ellipse 252,178 160,152 0,360|polygon 372,124 512,124 512,430 384,430 342,260'
)

make_layer() {
  local source_png="$1"
  local mask_png="$2"
  local output_png="$3"
  local alpha_png="$work_dir/alpha-$(basename "$output_png")"
  local staged_png="$work_dir/staged-$(basename "$output_png")-$RANDOM.png"

  convert "$source_png" -alpha extract "$mask_png" -compose multiply -composite "$alpha_png"
  convert "$source_png" "$alpha_png" -alpha off -compose CopyOpacity -composite \
    -strip -define png:compression-level=9 "PNG32:$staged_png"
  mv "$staged_png" "$output_png"
}

for spec in "${specs[@]}"; do
  IFS='|' read -r sheet cell_x cell_y slug head_draw tail_draw <<< "$spec"
  source_png="$design_root/originals/$slug.png"
  cat_dir="$web_root/$slug"
  mkdir -p "$cat_dir"

  sheet_width="$(identify -format '%w' "$source_dir/sheet-$sheet.png")"
  sheet_height="$(identify -format '%h' "$source_dir/sheet-$sheet.png")"
  test "$sheet_width" -eq "$sheet_height"
  test $((sheet_width % 2)) -eq 0
  cell_size=$((sheet_width / 2))
  offset_x=$((cell_x * cell_size))
  offset_y=$((cell_y * cell_size))
  master_png="$work_dir/$slug-master.png"
  outline_mask="$work_dir/$slug-outline-mask.png"
  outline_png="$work_dir/$slug-outline.png"
  base_png="$work_dir/$slug-base.png"
  staged_source="$work_dir/staged-source-$slug.png"

  # Preserve the generated composition but rebuild it as a deliberately
  # low-resolution 32-bit sprite.  The one-master-pixel outer contour becomes
  # a stepped four-to-six CSS-pixel outline after the final nearest-neighbour
  # export, making every character silhouette readable at small module sizes.
  convert "$source_dir/sheet-$sheet.png" -crop "${cell_size}x${cell_size}+$offset_x+$offset_y" +repage \
    -filter point -resize "${pixel_master}x${pixel_master}!" +dither -colors "$palette_limit" "PNG32:$master_png"
  convert "$master_png" -alpha extract -morphology Dilate 'Octagon:1' \
    -filter point -resize '512x512!' "$outline_mask"
  convert -size 512x512 "xc:$outline_color" "$outline_mask" \
    -alpha off -compose CopyOpacity -composite "PNG32:$outline_png"
  convert "$master_png" -filter point -resize '512x512!' "PNG32:$base_png"
  convert "$outline_png" "$base_png" -compose over -composite "PNG32:$staged_source"
  mv "$staged_source" "$source_png"

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

  staged_psd="$work_dir/staged-$slug.psd"
  convert \
    \( "$cat_dir/body.png" -set label body \) \
    \( "$cat_dir/tail.png" -set label tail \) \
    \( "$cat_dir/head.png" -set label head \) \
    "$staged_psd"
  mv "$staged_psd" "$design_root/psd/$slug.psd"
done

echo "Built ${#specs[@]} layered cats."
