#!/usr/bin/env bash
set -euo pipefail

settings_file="settings.gradle.kts"

if [[ ! -f "$settings_file" ]]; then
  echo "ERROR: $settings_file no existe" >&2
  exit 1
fi

# Repositorios explícitamente permitidos en settings.gradle.kts.
allowed_patterns=(
  'google\('
  'mavenCentral\('
  'gradlePluginPortal\('
  'maven\(url = "https://jitpack\.io"\)'
)

# Extrae líneas de repositorio dentro de bloques repositories { ... }
repo_lines=$(awk '
  /repositories[[:space:]]*\{/ { in_repo=1; depth=1; next }
  in_repo {
    if ($0 ~ /\{/) depth++
    if ($0 ~ /\}/) {
      depth--
      if (depth==0) { in_repo=0; next }
    }
    if ($0 ~ /(google\(|mavenCentral\(|gradlePluginPortal\(|maven\(url[[:space:]]*=)/) print
  }
' "$settings_file")

if [[ -z "$repo_lines" ]]; then
  echo "ERROR: No se detectaron repositorios en $settings_file" >&2
  exit 1
fi

status=0
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  approved=0
  for pattern in "${allowed_patterns[@]}"; do
    if [[ "$line" =~ $pattern ]]; then
      approved=1
      break
    fi
  done

  if [[ $approved -eq 0 ]]; then
    echo "ERROR: repositorio no aprobado detectado: $line" >&2
    status=1
  fi
done <<< "$repo_lines"

if [[ $status -ne 0 ]]; then
  cat >&2 <<'MSG'
Fallo de gobernanza de repositorios Gradle.
Solo están permitidos: google(), mavenCentral(), gradlePluginPortal() y jitpack.io (si está justificado).
Si necesitas agregar otro repositorio, requiere revisión de seguridad explícita.
MSG
  exit 1
fi

echo "OK: repositorios Gradle dentro de allowlist aprobada."
