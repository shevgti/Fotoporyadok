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

# Offline gradient fallbacks keep the release build deterministic when PxHere is unavailable.
fallback_gradients=(
  '0B1D35 4D8A9B' '091A32 286B86' '062A3A 18A6B8' '233C4A A9C4C7'
  '4A242A F0A66D' '071B35 1A806F' '174B43 D3B96F' '173A59 78B8D0'
  '472A34 D58A70' '101820 737F89'
)

for index in "${!urls[@]}"; do
  number=$(printf '%02d' "$((index + 1))")
  image="$resource_dir/theme_wallpaper_${number}.jpg"
  fallback="$resource_dir/theme_wallpaper_${number}.xml"
  rm -f "$image" "$fallback"

  if curl --fail --silent --show-error --location \
      --retry 3 --retry-all-errors --retry-delay 3 --retry-max-time 45 \
      --connect-timeout 15 --max-time 60 \
      --user-agent "FotoPoryadok-Android-Build/2.49 (+https://github.com/shevgti/Fotoporyadok)" \
      "${urls[$index]}" --output "$image" && test -s "$image" \
      && file "$image" | grep -q 'JPEG image data'; then
    echo "Downloaded theme wallpaper $number."
    rm -f "$fallback"
  else
    rm -f "$image"
    read -r start_color end_color <<< "${fallback_gradients[$index]}"
    cat > "$fallback" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="135" android:startColor="#${start_color}" android:endColor="#${end_color}" android:type="linear" />
</shape>
EOF
    test -s "$fallback"
    echo "::warning::PxHere image $number unavailable; using bundled gradient fallback."
  fi
done
