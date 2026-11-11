#!/usr/bin/env bash
set -euo pipefail

resource_dir="app/src/main/res/drawable-nodpi"
mkdir -p "$resource_dir"
urls=(
  'https://c.pxhere.com/photos/18/5e/teton_mountains_wyoming_trees_forest_woods_river_water-1119978.jpg!d'
  'https://c.pxhere.com/photos/59/e8/beach_clouds_conifer_daylight_docks_foliage_forest_island-1068766.jpg!d'
  'https://c.pxhere.com/photos/54/6b/trees_sky_mountain_clouds_forest_landscape_outdoor_turquoise-106152.jpg!d'
  'https://c.pxhere.com/photos/00/cb/lake_forest_mountain_foggy_landscape_nature_water_evergreens-595654.jpg!d'
  'https://c.pxhere.com/photos/6b/5a/lake_reflection_mountains_landscape_calm_nature_water_peaceful-676105.jpg!d'
  'https://c.pxhere.com/photos/5f/66/mountain_sky_clouds_outdoors_tranquil_high_weather_meteorology-1015434.jpg!d'
  'https://c.pxhere.com/photos/d4/19/palm_trees_tropical_houses_sky_clouds_city_town_sunset-892422.jpg!d'
  'https://c.pxhere.com/photos/45/0c/mountains_sea_water_buildings_city_vacation_europe_travel-654439.jpg!d'
  'https://c.pxhere.com/photos/a4/fc/photo-111696.jpg!d'
  'https://c.pxhere.com/photos/b0/82/beach_black_and_white_boat_bridge_dawn_dock_dusk_evening-1130756.jpg!d'
)
# Offline vector fallbacks keep every theme resource available even when PxHere
# is unavailable from GitHub-hosted runners. Coil can load XML drawables by ID.
make_fallback() {
  local number="$1" index="$2"
  local -a colors=(
    "#183B52,#55A6A0" "#16445A,#80C7C0" "#253B62,#79A9C8"
    "#263C52,#B5C8CE" "#31566A,#A7D4D4" "#34496C,#A7C7E8"
    "#5A3B58,#E5A77C" "#24506B,#76B6C8" "#38546D,#B8C9D9"
    "#263746,#9AAEBB"
  )
  IFS=',' read -r start end <<< "${colors[$index]}"
  cat > "$resource_dir/theme_wallpaper_${number}.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="135" android:startColor="$start" android:centerColor="$end" android:endColor="$start" />
</shape>
EOF
}

for index in "${!urls[@]}"; do
  number=$(printf '%02d' "$((index + 1))")
  target="$resource_dir/theme_wallpaper_${number}.jpg"
  fallback="$resource_dir/theme_wallpaper_${number}.xml"
  tmp="$RUNNER_TEMP/theme_wallpaper_${number}.jpg"
  rm -f "$target" "$fallback" "$tmp"
  # Network images are optional. A valid download is used when available;
  # a bundled gradient drawable is generated deterministically on any failure.
  if curl --fail --silent --show-error --location \
    --retry 3 --retry-all-errors --retry-delay 3 --retry-max-time 90 \
    --connect-timeout 15 --max-time 45 \
    --user-agent "FotoPoryadok-Android-Build/2.49 (+https://github.com/shevgti/Fotoporyadok)" \
    "${urls[$index]}" --output "$tmp" &&
    test -s "$tmp" && file "$tmp" | grep -Eqi 'JPEG image data|JFIF'; then
    mv "$tmp" "$target"
    echo "Theme wallpaper $number: downloaded."
  else
    rm -f "$tmp"
    make_fallback "$number" "$index"
    echo "::warning::Theme wallpaper $number could not be downloaded; using offline gradient fallback."
  fi
done
