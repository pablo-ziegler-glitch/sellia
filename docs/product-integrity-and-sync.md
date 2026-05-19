# Product Integrity And Sync

## Resumen

Este cambio separa identidad local y remota de productos:

- `products.id` sigue siendo PK local Room.
- `products.productUuid` es la identidad global estable.
- Firestore usa `tenants/{tenantId}/products/{productUuid}`.
- Tombstones usan `tenants/{tenantId}/product_deletions/{productUuid}`.

Además:

- Se elimina el camino automático destructivo ante conflictos de sync.
- Se registran conflictos en `product_sync_conflicts`.
- El outbox de productos soporta operación explícita: `UPSERT`, `MARK_DELETED`.
- El delete local pasa a soft-delete (`deletedAtEpochMs`) + tombstone remoto idempotente.

## Modelo de identidad

### Local (Room)

`ProductEntity` agrega:

- `productUuid`
- `legacyLocalId`
- `createdAtEpochMs`
- `updatedAtEpochMs`
- `deletedAtEpochMs`
- `syncVersion`
- `syncStatus`

`id` no se usa más como identidad remota.

### Remoto (Firestore)

`ProductFirestoreMappers`:

- escribe `productUuid` y metadata de sync.
- para docs legacy sin `productUuid`:
  - si `docId` es UUID: usa ese UUID.
  - si `docId` es numérico: deriva UUID legacy estable.
  - si no: deriva UUID determinístico desde claves legacy.

## Flujo de sync

`syncDownIncremental`:

1. match por `productUuid`
2. fallback por `legacyLocalId`
3. fallback por `code`
4. fallback por `barcode`
5. si hay múltiples candidatos duplicados: conserva automáticamente el más reciente por `updatedAtEpochMs`, archiva lógicamente los demás y guarda snapshot histórico.
6. compara por `updatedAtEpochMs`.
7. remoto más nuevo: actualiza local preservando `id` y guarda snapshot histórico del estado anterior.
8. local más nuevo: encola outbox (no write-back agresivo).

Conflictos quedan en `product_sync_conflicts`.

## Flujo de delete

1. `deleteById` marca local `deletedAtEpochMs` y `syncStatus=PENDING_DELETE`.
2. encola outbox `MARK_DELETED`.
3. publica tombstone remoto por `productUuid`.
4. confirma local como `DELETED`.

No borra físicamente producto/historial.

## Outbox

`sync_outbox` agrega:

- `entityUuid`
- `operation`

`SyncRepositoryImpl.pushPendingProducts` procesa por operación:

- `UPSERT`: upsert de producto activo.
- `MARK_DELETED`: tombstone remoto idempotente.

## Normalización remota

Servicio: `ProductDataNormalizationService`.

Entrada:

- `tenantId`
- `runId`
- `simulateOnly`
- `applyChanges`

Salida:

- reporte en `tenants/{tenantId}/migration_reports/{runId}`
- auditoría por grupo en `tenants/{tenantId}/product_merge_audit/*`

En `simulateOnly=true` no aplica merges/archivo lógico en productos.

Regla de consolidación:

- ante duplicados remotos o locales siempre queda el producto más vigente (más reciente `updatedAtEpochMs`);
- antes de consolidar se guarda snapshot histórico de los estados anteriores.

Script operativo para Firestore:

```bash
# Simulación (no escribe cambios)
npm --prefix functions run products:normalize:dry -- --tenant <TENANT_ID> --run-id <RUN_ID_OPCIONAL>

# Aplicación real
npm --prefix functions run products:normalize:apply -- --tenant <TENANT_ID> --run-id <RUN_ID_OPCIONAL>
```

El script archiva lógicamente duplicados, conserva el más reciente y guarda snapshots en `tenants/{tenantId}/product_state_history`.

## Migración Room

Migración `48 -> 49`:

- agrega columnas de identidad/sync en `products`.
- backfill `productUuid` legacy (`legacy-{id}`) y `legacyLocalId=id`.
- agrega columnas de outbox (`entityUuid`, `operation`).
- agrega `productUuid`/legacy fields a `invoice_items` y `stock_movements`.
- crea tabla `product_sync_conflicts`.

## Validación manual

1. Verificar que productos viejos tienen `productUuid` no vacío.
2. Ejecutar sync con conflicto intencional de `code` o `barcode` y confirmar:
   - no hay `deleteAll`.
   - se crea fila en `product_sync_conflicts`.
3. Eliminar producto y verificar:
   - `deletedAtEpochMs` local.
   - outbox `MARK_DELETED`.
   - tombstone remoto por `productUuid`.
4. Confirmar que facturas y movimientos históricos siguen visibles.

## Tests / build

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Si necesitás validar migración Room en dispositivo/emulador, ejecutar también:

```bash
./gradlew :app:connectedDebugAndroidTest
```
