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

## Política de permisos de mantenimiento

- Permiso lectura: `MAINTENANCE_READ`
- Permiso escritura: `MAINTENANCE_WRITE`

### Mapeo por rol (política actual)

| Rol | MAINTENANCE_READ | MAINTENANCE_WRITE |
|---|---|---|
| owner | ✅ | ✅ |
| admin | ✅ | ✅ |
| manager | ❌* | ❌* |
| cashier | ❌ | ❌ |
| viewer | ❌ | ❌ |

`*` manager puede habilitarse más adelante por permiso explícito, pero no por defecto para minimizar riesgo operacional.
