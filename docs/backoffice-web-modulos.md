# Backoffice web: dominios funcionales (alcance inicial)

## Módulos incluidos

1. `configuracion`
2. `usuarios`
3. `mantenimiento`
4. `reportes`

## Fuera de alcance inicial

- `ventas`
- `carrito`
- `checkout/POS`

La UI inicial del backoffice de mantenimiento se publica en `/backoffice.html` y opera desacoplada del flujo POS/checkout para reducir acoplamiento operativo y técnico.

## Política de permisos por módulo

La matriz de permisos se centraliza en `docs/security/ROLE_PERMISSIONS_MATRIX.md` y su objeto canónico de runtime en `functions/src/security/rolePermissionsMatrix.ts`.

### Resumen operativo (backoffice inicial)

| Módulo | Roles permitidos |
|---|---|
| `configuracion` (`pricing`, `marketing`, `cloudServices`) | `owner`, `admin` (+ `manager` solo en `marketing`) |
| `usuarios` | `owner`, `admin` |
| `mantenimiento` | `owner`, `admin` (lectura/escritura por rol base) |
| `reportes/dashboard` | `owner`, `admin`, `manager` |
