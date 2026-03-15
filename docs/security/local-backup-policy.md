# Política de backup local (Android)

## Decisión vigente

La app opera con **`android:allowBackup="false"`** en producción. Esto deshabilita backup automático (cloud backup y device transfer) para evitar exposición de datos sensibles locales en restauraciones fuera del control de negocio.

## Clasificación de datos y política

| Tipo de dato | Ejemplos en app | Sensibilidad | Política de backup local |
|---|---|---|---|
| **Sensible** | tokens/sesiones, credenciales, secretos de integración, estado de autenticación, datos financieros y comerciales persistidos localmente (DB/SharedPreferences) | Alta | **No respaldar** |
| **Operativo regenerable** | cachés, código cache, artefactos temporales, colas de sync rehidratables | Media/Baja | **No respaldar** (se regenera) |
| **Configuración no sensible** | flags de UI sin impacto de seguridad (si existieran) | Baja | Evaluar caso a caso; por defecto no respaldar mientras `allowBackup=false` |

## Reglas técnicas implementadas

Aunque el backup global está desactivado, se mantienen reglas explícitas defensivas:

- `@xml/data_extraction_rules`: excluye `database`, `sharedpref`, rutas de `file` asociadas a auth/tokens/credentials/sync y rutas `root` de cache/no_backup.
- `@xml/backup_rules`: exclusiones equivalentes para full backup legado.

Esto protege frente a cambios futuros de configuración o variaciones por API level/flavors.

## Compatibilidad con soporte operativo y recuperación

### Impacto esperado

- **No hay restauración automática** de estado local al reinstalar/cambiar dispositivo.
- Reduce riesgo de fuga de datos por mecanismos de copia/transferencia del sistema.

### Estrategia de recuperación recomendada

1. **Fuente de verdad remota** para datos de negocio críticos (sync backend/cloud).
2. **Reautenticación** obligatoria tras reinstalación.
3. **Rehidratación controlada** de datos operativos desde backend (catálogo, clientes, precios, etc.).
4. **Runbook de soporte** para incidencias de recovery:
   - verificar conectividad/sync,
   - validar tenant y permisos,
   - forzar resync inicial,
   - escalar si hay divergencia entre backend y dispositivo.

## Criterio para futuras excepciones

Si en el futuro se requiere backup parcial:

- mantener `allowBackup=true` solo si existe justificación de negocio/auditoría,
- usar allowlist estricta por ruta (nunca DB ni secretos),
- aprobar en revisión de seguridad,
- validar mediante pruebas de restore en API 31+ y APIs legadas.
