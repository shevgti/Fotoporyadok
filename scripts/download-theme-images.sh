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
for index in "${!urls[@]}"; do
  number=$(printf '%02d' "$((index + 1))")
  rm -f "$resource_dir/theme_wallpaper_${number}.xml"
  curl --fail --location --retry 3 --retry-delay 2 "${urls[$index]}" \
    --output "$resource_dir/theme_wallpaper_${number}.jpg"
  test -s "$resource_dir/theme_wallpaper_${number}.jpg"
  file "$resource_dir/theme_wallpaper_${number}.jpg"
done
