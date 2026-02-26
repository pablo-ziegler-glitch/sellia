#!/usr/bin/env bash
set -euo pipefail

file="app/build.gradle.kts"

if [[ ! -f "$file" ]]; then
  echo "No se encontró $file"
  exit 1
fi

duplicates=$(awk '
  match($0, /^[[:space:]]*([a-zA-Z]+Implementation|kapt|implementation|debugImplementation)\(([^)]+)\)/, m) {
    key = m[1] "(" m[2] ")"
    count[key]++
  }
  END {
    for (k in count) {
      if (count[k] > 1) {
        print count[k] "x " k
      }
    }
  }
' "$file" | sort)

if [[ -n "$duplicates" ]]; then
  echo "Dependencias duplicadas detectadas en $file:"
  echo "$duplicates"
  exit 1
fi

echo "OK: no se detectaron duplicados obvios en dependencies de $file"
