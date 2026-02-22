# Deep Links seguros (explicación simple)

## ¿Para qué sirve esto?
Este endurecimiento evita que terceros manden links maliciosos o manipulados para forzar comportamientos no deseados en la app.

En términos simples:
- **Baja riesgo de abuso** (links truchos con parámetros raros).
- **Protege navegación** (solo rutas permitidas).
- **Mejora observabilidad** (se registran intentos inválidos en logs).

## ¿Qué vas a poder hacer?
- Abrir la app con links HTTPS controlados por la marca:
  - `https://sellia1993.web.app/product?q=...`
  - `https://sellia1993.firebaseapp.com/product?q=...`
- Seguir usando el esquema custom legado:
  - `sellia://product?q=...`
- Enviar parámetros permitidos y validados:
  - `q` (obligatorio)
  - `sku` (opcional)
  - `tenantId` (opcional)

## ¿Qué NO vas a poder hacer?
- Usar hosts o dominios no permitidos.
- Usar rutas fuera de `/product`.
- Enviar query params no permitidos.
- Enviar valores con formato inválido (por ejemplo, `q` con caracteres no aceptados).
- Forzar navegación con payloads malformados: el intent se rechaza y se redirige de forma segura.

## Impacto en negocio (real)
- **Menos riesgo operativo** por abuso o links externos manipulados.
- **Mejor soporte**: cuando algo falla, queda evidencia en logs.
- **Mejor UX en producción**: los links válidos siguen funcionando, los inválidos fallan de forma controlada.

## Limitación importante
Para que `android:autoVerify="true"` funcione al 100% en Android, el dominio debe publicar correctamente `/.well-known/assetlinks.json` apuntando al package y SHA-256 del certificado de firma.

Sin ese archivo bien configurado:
- el link HTTPS puede seguir abriendo,
- pero Android puede no marcarlo como "verificado" automáticamente en todos los dispositivos.
