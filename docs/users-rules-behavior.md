# Reglas de usuarios (explicación simple)

## ¿Para qué sirve esto?
Estas reglas evitan que un cliente (app/web) se dé permisos de administrador por su cuenta.

En otras palabras: **la app del usuario no puede autoconvertirse en admin** editando Firestore directo.

## Qué NO vas a poder hacer
- Crear o actualizar tu documento en `/users/{userId}` con:
  - `isAdmin: true`
  - `isSuperAdmin: true`
  - `permissions` (inyectadas desde cliente)
- Hacer bootstrap de owner con flags administrativos (`isSuperAdmin`, `isAdmin`) desde cliente.
- Obtener rol admin solo por escribir campos en `users/`.

## Qué SÍ vas a poder hacer
- Crear usuario final (`final_customer`) con rol `viewer` y estado `active` sin campos sensibles.
- Crear owner bootstrap válido (`store_owner`) si sos el owner del tenant y sin flags administrativos.
- Operar normalmente con permisos de admin **si** tu sesión tiene custom claims de Firebase Auth (`admin`/`role=admin`) o mecanismos backend autorizados.

## Impacto para negocio
- Reduce riesgo de escalación de privilegios y acceso indebido a datos.
- Evita incidentes de seguridad que impactan operación, confianza y costos.
- Mantiene onboarding legítimo funcionando, pero bloquea payloads maliciosos.
