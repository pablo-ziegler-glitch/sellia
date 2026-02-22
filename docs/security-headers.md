# Seguridad en Firebase Hosting: qué hace cada header y por qué

Este documento explica **para qué sirve cada política** definida en `firebase.json` y **por qué se configuró así** para un entorno productivo con Firebase Hosting.

## 1) Política global (`source: "**"`)

Se aplica a **todo el sitio público y rutas auxiliares** para tener una base segura por defecto.

### `Content-Security-Policy` (CSP)

CSP controla desde qué orígenes el navegador puede cargar recursos.

Directivas configuradas:

- `default-src 'self'`  
  Bloquea cualquier tipo de recurso no declarado explícitamente.
- `base-uri 'self'`  
  Evita que un atacante cambie la base URL del documento para manipular rutas.
- `form-action 'self'`  
  Impide envío de formularios a dominios externos no autorizados.
- `frame-ancestors 'none'`  
  Evita clickjacking: nadie puede embeber tu sitio en un iframe.
- `object-src 'none'`  
  Deshabilita plugins embebidos (`object`, `embed`, `applet`), vector histórico de ataques.
- `script-src 'self' https://www.gstatic.com`  
  Solo permite scripts propios y SDK de Firebase cargado desde `gstatic`.
- `style-src 'self' https://fonts.googleapis.com`  
  Habilita estilos locales y hoja de Google Fonts usada por el sitio.
- `font-src 'self' https://fonts.gstatic.com`  
  Permite cargar archivos de fuentes de Google Fonts.
- `img-src 'self' data: https://i.ytimg.com https://firebasestorage.googleapis.com https://storage.googleapis.com`  
  Permite imágenes locales, `data:` (íconos/placeholders), miniaturas de YouTube y assets/imágenes en Firebase Storage.
- `connect-src 'self' https://firestore.googleapis.com`  
  Autoriza llamadas `fetch/XHR` al API REST de Firestore que usa el frontend.
- `frame-src https://www.youtube-nocookie.com`  
  Autoriza embed de video en landing usando modo de privacidad reforzada.
- `manifest-src 'self'`  
  Limita manifest al mismo origen.
- `upgrade-insecure-requests`  
  Fuerza upgrade de recursos HTTP a HTTPS cuando sea posible.

**Por qué:** enfoque deny-by-default (mínimo privilegio), reduciendo XSS, inyección de recursos externos y clickjacking sin romper los flujos actuales del sitio.

### `X-Content-Type-Options: nosniff`

Evita que el navegador “adivine” MIME types.

**Por qué:** reduce riesgos de ejecución de archivos servidos con tipo incorrecto (MIME sniffing).

### `Referrer-Policy: strict-origin-when-cross-origin`

Controla qué info del referrer se comparte al navegar.

**Por qué:** balance entre analítica útil y privacidad; evita filtrar rutas completas hacia terceros.

### `Permissions-Policy: geolocation=(), microphone=(), camera=()`

Bloquea APIs sensibles del navegador para todo el sitio.

**Por qué:** principio de mínimo privilegio; reduce superficie de abuso de APIs de dispositivo.

### `X-Frame-Options: DENY`

Bloquea render en `iframe`.

**Por qué:** defensa en profundidad anti-clickjacking (además de `frame-ancestors 'none'` en CSP).

---

## 2) CSP específica para admin (`source: "/admin/**"`)

Las páginas de admin suelen requerir endpoints adicionales por autenticación.

### Diferencias con la CSP global

- Mantiene núcleo estricto: `default-src`, `base-uri`, `form-action`, `frame-ancestors`, `object-src`.
- `script-src 'self' https://www.gstatic.com` para SDK Firebase modular.
- `style-src 'self'` (admin no depende de Google Fonts).
- `img-src 'self' data:` (mínimo necesario en admin).
- `connect-src` agrega:
  - `https://identitytoolkit.googleapis.com`
  - `https://securetoken.googleapis.com`
  además de Firestore.
- `frame-src https://accounts.google.com` para popup/flujo OAuth cuando aplica.

**Por qué:** habilitar solo lo indispensable para Auth/Admin sin abrir permisos del sitio público completo.

---

## 3) Validación explícita de orígenes externos actuales

Se declararon explícitamente los orígenes usados hoy en el proyecto:

- `https://www.gstatic.com` (Firebase Web SDK)
- `https://fonts.googleapis.com` (CSS de fuentes)
- `https://fonts.gstatic.com` (archivos de fuentes)

**Por qué:** dejar trazabilidad y evitar roturas por CSP cuando el browser bloquee recursos no listados.

---

## 4) Impacto de negocio y operación

- **Seguridad:** menor superficie de ataque, útil para producción y compliance básico.
- **Costo operativo:** menos incidentes por inyección/clickjacking => menos tiempo de soporte.
- **Escalabilidad:** política base reutilizable; excepciones solo por ruta (`/admin/**`) para no degradar seguridad global.
- **UX:** se preservan fuentes, videos y Firebase actuales evitando bloqueos inesperados.

---

## 5) Qué revisar antes de cada cambio frontend

Si se agrega un nuevo servicio externo (analytics, cdn, chat, mapas, etc.), actualizar CSP en `firebase.json`:

1. identificar recurso (`script`, `connect`, `img`, `frame`, etc.),
2. agregar el host exacto al directive correcto,
3. validar en navegador (Console CSP + pruebas de flujo).

Recomendación productiva: en una etapa futura, habilitar pipeline con verificación CSP automatizada en preview channels antes de deploy a producción.
