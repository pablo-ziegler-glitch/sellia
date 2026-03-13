# Guía de Integridad de Administradores

## Problema

Los administradores pueden perder acceso en producción cuando hay inconsistencias entre:

1. **JWT Custom Claims** (Firebase Auth) - Claims personalizados en el token
2. **Documento /users/{uid}** (Firestore) - Perfil del usuario
3. **Documento /tenant_users/{tenantId}_{uid}** (Firestore) - Membership en el tenant
4. **Documento /tenants/{tenantId}** (Firestore) - Información del tenant

Un admin "roto" es aquel que tiene permisos en un lugar pero no en otro, causando fallos aleatoria en la aplicación.

## Orígenes del Problema

### 1. Token JWT Desactualizado
El usuario cambió de rol pero no refrescó el token de sesión.

**Síntoma**: El usuario ve errores de "Acceso Denegado" aunque debería ser admin.
**Solución**: Hacer logout/login para refrescar el token.

### 2. Documento de Usuario Incompleto
El documento `/users/{uid}` falta o tiene campos incompletos.

**Síntoma**: El backend valida JWT claims pero faltan datos en Firestore.
**Solución**: Ejecutar `npm --prefix functions run admin:grant` para reconstruir.

### 3. Inconsistencia de Datos
Los campos tienen valores diferentes entre Auth y Firestore (ej: role="Admin" en Firestore pero "admin" en Auth).

**Síntoma**: Fallos aleatorios en la validación de permisos.
**Solución**: Normalizar valores y validar consistencia.

### 4. Usuario Inactivo
El status del usuario es "inactive" o faltan validaciones de estado.

**Síntoma**: El usuario está "congelado" aunque tenga claims válidos.
**Solución**: Actualizar `status = 'active'` en `/users/{uid}`.

## Cómo Diagnosticar

### Quick Health Check (⚡ Rápido)

```bash
cd functions
npm run admin:health-check
```

Muestra un resumen de todos los admins en el sistema y su estado.

**Ejemplo de salida**:
```
EMAIL | ROLE | TENANT | STATUS | HEALTH
admin@example.com | owner | abc123... | active | ✅
broken@example.com | admin | (vacío) | active | ❌
```

### Diagnóstico Detallado

```bash
cd functions
npm run admin:diagnose -- --email broken@example.com
```

Analiza un admin específico y proporciona sugerencias de reparación.

**Qué valida**:
- JWT claims (admin, role, tenantId)
- /users/{uid} (isAdmin, role, status, tenantId)
- /tenant_users/{tenantId}_{uid} (isActive, role)
- Consistencia entre los tres documentos

### Auditoría Completa

```bash
cd functions
npm run admin:audit
```

Escanea TODOS los admins en el sistema y genera un reporte JSON con:
- Errores críticos (documentos faltantes)
- Advertencias (inconsistencias)
- Detalles de cada admin

**Salida**:
```
audit-report-<timestamp>.json
```

## Cómo Reparar

### Opción 1: Script Automático (Recomendado)

Para un admin individual:

```bash
cd functions
npm run admin:grant -- --email admin@example.com --tenant <TENANT_ID> --role owner
```

Flags:
- `--dry-run`: Vista previa sin hacer cambios
- `--role`: owner|admin (default: admin)
- `--super-admin`: Otorgar superadmin claims

**¿Qué hace?**
1. Actualiza JWT custom claims
2. Crea/actualiza `/users/{uid}`
3. Crea/actualiza `/tenant_users/{tenantId}_{uid}`
4. Crea entrada de hash para auditoría

### Opción 2: Script de Reparación Específico

```bash
cd functions
node scripts/fix-admin-account.js \
  --email admin@example.com \
  --tenant <TENANT_ID> \
  --role owner \
  --dry-run
```

Si el preview se ve bien, ejecutar sin `--dry-run`.

### Opción 3: Manual via CLI

Si necesitas un control más granular:

```bash
# 1. Actualizar status
firebase firestore:update users/<UID> status=active

# 2. Actualizar rol
firebase firestore:update users/<UID> role=owner

# 3. Asegurar isAdmin=true
firebase firestore:update users/<UID> isAdmin=true

# 4. Recrear tenant_users si es necesario
firebase firestore:set "tenant_users/<TENANT_ID>_<UID>" \
  '{"tenantId":"<TENANT_ID>","uid":"<UID>","role":"owner","isActive":true}' \
  --merge
```

## Estructura Esperada de un Admin Sano

### Firebase Auth Custom Claims
```json
{
  "admin": true,
  "role": "owner",
  "tenantId": "abc123xyz",
  "superAdmin": false,
  "email": "admin@example.com"
}
```

### /users/{uid}
```json
{
  "email": "admin@example.com",
  "tenantId": "abc123xyz",
  "role": "owner",
  "status": "active",
  "accountType": "store_owner",
  "isActive": true,
  "isAdmin": true,
  "isSuperAdmin": false,
  "permissions": [
    "MANAGE_USERS",
    "MANAGE_CLOUD_SERVICES",
    ...
  ]
}
```

### /tenant_users/{tenantId}_{uid}
```json
{
  "tenantId": "abc123xyz",
  "uid": "user-uid-123",
  "email": "admin@example.com",
  "role": "owner",
  "isActive": true,
  "permissions": [...]
}
```

## Reglas de Firestore Actualizadas

Las reglas ahora incluyen validaciones más estrictas:

### Nuevas Funciones Helper

```firestore
function isValidAdminUser(userId)
```
Valida que `/users/{uid}` sea un documento admin válido.

```firestore
function hasValidTenantIdForAdmin(userId)
```
Valida que el admin tenga un `tenantId` definido.

```firestore
function hasTenantUserDoc(tenantId, userId)
```
Valida que exista el documento de membership.

```firestore
function isValidAdminWithIntegrity(tenantId, userId)
```
Validación multi-fuente completa de integridad de admin.

### Validación Mejorada en isTenantAdmin()

La función ahora:
1. ✅ Verifica JWT claims
2. ✅ Verifica consistency en tenantId
3. ✅ Rechaza admins con tenantId inconsistente
4. ✅ Requiere que el admin sea miembro activo del tenant

## Checklist para Producción

- [ ] Ejecutar `npm run admin:health-check` y verificar que todos los admins sean ✅
- [ ] Si hay ❌, ejecutar diagnóstico: `npm run admin:diagnose -- --email <EMAIL>`
- [ ] Ejecutar reparación: `npm run admin:grant -- --email <EMAIL> --tenant <ID> --dry-run`
- [ ] Revisar preview y ejecutar sin `--dry-run`
- [ ] Pedir al usuario que haga logout/login para refrescar token
- [ ] Verificar acceso nuevamente en la aplicación
- [ ] Ejecutar auditoría completa: `npm run admin:audit`

## Troubleshooting

### "No se encontraron credenciales"

Asegurar que tienes auth válida:

```bash
# Opción 1: ADC (Application Default Credentials)
gcloud auth application-default login

# Opción 2: Service Account
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/service-account.json

# Opción 3: Especificar en el comando
npm run admin:health-check -- --service-account /ruta/service-account.json
```

### "Usuario NO encontrado en Firebase Auth"

El email no existe en Firebase Auth. Verificar:
- ¿Está el email bien escrito?
- ¿Es el email correcto para el proyecto?
- ¿El proyecto está activado?

### "Documento /users/{uid} NO existe"

El usuario existe en Auth pero no en Firestore. Usar:
```bash
npm run admin:grant -- --email <EMAIL> --tenant <ID>
```

### "Inconsistencia de role"

El role tiene valores diferentes. Verificar:
- `Auth.token.role`: ¿Está normalizado? (lowercase)
- `/users.role`: ¿Es consistente?
- `/tenant_users.role`: ¿Coincide?

**Solución**:
```bash
# Normalizar el role a lowercase en Firestore
firebase firestore:update users/<UID> role=owner
```

## Monitoreo Continuo

Considerar agregar en CI/CD:

```bash
# En pre-deployment checks
npm --prefix functions run admin:health-check
npm --prefix functions run admin:audit

# Fallar si hay errores críticos
if [ $? -ne 0 ]; then
  echo "❌ Admin integrity check failed"
  exit 1
fi
```

## Referencias

- [Firestore Security Rules](./firestore.rules)
- [Admin Access Management](./manage-admin-access.js)
- [Firebase Auth Custom Claims](https://firebase.google.com/docs/auth/admin-sdk-set-claims)
