# Reglas de seguridad y tenantId

## Regla aplicada en `users/{userId}`

La colección `users` queda restringida de la siguiente forma:

- **Create**: solo si el usuario autenticado coincide con el `userId` del documento y el `tenantId` existe como documento en `tenants/{tenantId}`.
- **Update**: solo si el usuario autenticado coincide con el `userId` y **no cambia** el `tenantId` (se compara el valor almacenado con el enviado).
- **Read/Delete**: solo si el usuario autenticado coincide con el `userId`.

Esto evita que un usuario se asigne o migre de tenant manualmente desde el cliente.

## Recomendación de seguridad avanzada (opcional)

Si se requiere máxima seguridad y trazabilidad, mover la asignación inicial de `tenantId` a:

- **Cloud Functions** (trigger post-Auth o endpoint administrativo), o
- **Custom Claims** con validación en reglas.

Esto evita cualquier dependencia del cliente para asignar pertenencia de tenant.

## Validación en el cliente (Android)

El cliente **no debe intentar modificar** `tenantId` en documentos de `users`. Actualmente el cliente solo lee `tenantId` desde Firestore para construir la sesión, sin realizar escrituras en `users`.

Si en el futuro se implementan pantallas de perfil o edición de usuario, se debe excluir `tenantId` del payload de actualización.
