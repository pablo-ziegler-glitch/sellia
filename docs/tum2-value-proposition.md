# TuM2: Definición de Propuesta de Valor

**Versión:** 1.0
**Fecha:** 2026-03-19
**Estado:** Aprobado para ejecución

---

## Propuesta de Valor (una oración)

> TuM2 es la app que le dice al vecino, antes de salir de su casa, qué está pasando en el comercio de su barrio: quién está abierto, cuál es la farmacia de turno, quién tiene stock de lo que busca y quién acepta delivery hoy. La información es real porque viene directo de los comerciantes, actualizada automáticamente desde su propio sistema de gestión.

**Versión corta (uso interno):**

> TuM2 = el panel de señales del comercio de barrio, en tiempo real, conectado directamente al POS del comerciante.

**Tagline de marca:**

> "TuM2: antes de salir, sabé lo que pasa en tu barrio."

---

## Posicionamiento

TuM2 no es un marketplace transaccional. No compite con Mercado Libre, Rappi ni PedidosYa.

TuM2 resuelve el problema de **información operativa hiper-local** que ninguna plataforma existente resuelve:

| Plataforma | Qué resuelve | Qué NO resuelve |
|-----------|-------------|-----------------|
| Google Maps | Ubicación, horarios estáticos | Estado operativo en tiempo real, stock, señales del comerciante |
| WhatsApp | Contacto bilateral | Información pública sin necesidad de interacción |
| Mercado Libre | E-commerce | Comercio de barrio, información pre-salida |
| Instagram | Publicaciones del comerciante | Datos operativos automáticos, sin trabajo manual |
| **TuM2** | **Señales operativas en tiempo real del comercio local** | - |

El diferencial defensible: los datos vienen automáticamente del POS (Sellia). El comerciante no publica manualmente. Ningún competidor puede replicar ese canal sin primero adoptar el POS.

---

## Segmentos

### CUSTOMER (Vecino/Consumidor)

**Problema:** No sabe si la farmacia de turno es la de 2 cuadras o la de 10, si el almacén está abierto hoy, si la ferretería tiene lo que necesita antes de salir.

**Valor:** "Sé si la farmacia de turno es la que está a 2 cuadras antes de salir. Lo vi en TuM2."

**Habito de uso:** Consulta antes de salir de casa. Alta recurrencia diaria.

**Onboarding:** Sin registro obligatorio para ver información pública. Geolocalización en primer uso.

### OWNER (Comerciante)

**Problema:** Su presencia pública está fragmentada. Tiene que publicar manualmente en Instagram/WhatsApp cada vez que cambia algo operativo.

**Valor:** "Mis vecinos saben que estoy abierto sin que yo tenga que publicar en Instagram cada mañana."

**Esfuerzo requerido:** Configurar señales operativas una vez desde Sellia. La sincronización es automática.

### ADMIN (Operador de Plataforma)

**Responsabilidades:** Gestión del directorio de farmacias de turno por región/fecha, validación de señales reportadas como incorrectas, dashboard de salud de la plataforma por zona.

---

## Las 3 Capacidades Centrales

### 1. Catálogo público con geolocalización (YA EXISTE)

- StoreDiscovery con filtros y radio de 5km
- Catálogo de productos actualizado automáticamente desde Sellia
- QR por tienda y por producto como canal de entrada orgánica

**Acción inmediata:** Pulir UX y copy para reflejar la propuesta de valor en < 10 segundos.

### 2. Señales operativas en tiempo real (A CONSTRUIR - P0)

Datos que el comerciante configura en Sellia y se sincronizan automáticamente a TuM2:

- `isOpen`: abierto/cerrado ahora
- `acceptsDelivery`: acepta delivery hoy
- `operatingHours`: horario por día de la semana
- `customSignals`: mensajes configurables (ej: "Sin aceite hoy", "Efectivo solamente")

**Modelo de datos nuevo requerido:**

```
tenants/{tenantId}/operational_signals/
- isOpen: boolean
- acceptsDelivery: boolean
- operatingHours: { [dayOfWeek]: { open: string, close: string } }
- customSignals: { [key]: { label: string, value: string, active: boolean } }
- updatedAt: timestamp
- updatedBy: uid
```

Campos a agregar en `public_tenant_directory`:
- `isOpen: boolean`
- `acceptsDelivery: boolean`
- `isPharmacyOnDuty: boolean`
- `operationalSignalsUpdatedAt: timestamp`

Sincronización: Cloud Function trigger igual que `public_products`.

### 3. Farmacia de turno (A CONSTRUIR - P0 por impacto en hábito)

Entidad separada gestionada por admin. Es el caso de uso de mayor urgencia percibida por el usuario y el principal driver de adquisición orgánica.

**Modelo de datos:**

```
platform/pharmacies_on_duty/{region}/{date}
- pharmacyTenantId: string
- pharmacyName: string
- address: string
- phone: string
- validFrom: timestamp
- validUntil: timestamp
- region: string
- createdBy: uid
```

---

## Alcance: Dentro y Fuera

### DENTRO de TuM2

- Descubrimiento de comercios locales por geolocalización
- Catálogo público de productos con precios actualizados desde Sellia
- Señales operativas: abierto/cerrado, horario, acepta delivery, mensajes del comerciante
- Directorio de farmacias de turno por zona/barrio
- QR de entrada a tienda/producto
- Vista de tienda con banner, contacto, catálogo
- Contacto vía WhatsApp al comerciante
- Notificaciones opcionales de cambio de estado en tiendas seguidas
- Participación comunitaria básica (Fase 3, no Fase 1)

### FUERA de TuM2 (en esta etapa)

- Marketplace transaccional con gestión de envíos
- Delivery operado por TuM2
- Red social general del barrio
- Reseñas y calificaciones de productos
- Gestión operativa del comerciante (eso es Sellia)
- Publicidad de grandes cadenas
- Integración con sistemas de terceros

---

## Roadmap de Fases

| Fase | Contenido | Estado |
|------|-----------|--------|
| Fase 1 | Catálogo + geolocalización + QR (ya existe, pulir UX) | Lanzable ahora |
| Fase 2 | Señales operativas básicas (abierto/cerrado, delivery) | Sprint 1 post-esta tarjeta |
| Fase 3 | Farmacia de turno como entidad gestionada | Sprint 2 |
| Fase 4 | Participación comunitaria: confirmar/reportar señales | Post-adopción inicial |

---

## Backlog de Tarjetas Desbloqueadas

En orden de prioridad:

| # | ID | Tarjeta | Prioridad |
|---|-----|---------|-----------|
| 1 | TuM2-005 | Modelar farmacia de turno con rotación programada | P0 |
| 2 | TuM2-002 | Modelar señales operativas en Firestore + Cloud Function sync | P0 |
| 3 | TuM2-009 | Pulir StoreDiscovery web para reflejar propuesta de valor | P0 |
| 4 | TuM2-003 | Pantalla de configuración de señales operativas en Sellia (OWNER) | P0 |
| 5 | TuM2-008 | Onboarding consumer: selección de barrio/zona | P1 |
| 6 | TuM2-004 | Mostrar señales operativas en app y web (consumer) | P1 |
| 7 | TuM2-006 | Vista de farmacia de turno en TuM2 (app y web) | P1 |
| 8 | TuM2-007 | Panel admin de gestión de farmacias de turno | P1 |
| 9 | TuM2-010 | Sistema de diseño de TuM2 diferenciado de Sellia | P1 |
| 10 | TuM2-011 | Tagline y copy en todos los touchpoints | P2 |

---

## Analytics: Eventos Críticos a Instrumentar

| Evento | Por qué |
|--------|---------|
| `store_viewed` | Cuántos vecinos ven tiendas reales |
| `geo_requested` | Adopción del discovery por ubicación |
| `operational_signal_viewed` | Validar que las señales generan engagement |
| `pharmacy_on_duty_viewed` | KPI más importante del diferencial |
| `qr_entry` | Tráfico orgánico por QR del comerciante |
| `store_request_tapped` | Intención de comerciante nuevo |
| `catalog_product_viewed` | Profundidad de exploración |
| `whatsapp_contact_tapped` | Conversión a contacto con comerciante |

---

## Criterios de Aceptación (BDD)

### Escenario 1: El vecino encuentra la farmacia de turno

```gherkin
Given que soy un vecino con la app TuM2 abierta
And tengo la geolocalización activada
When busco "farmacia de turno" o navego a la sección correspondiente
Then veo el nombre, dirección y teléfono de la farmacia de turno más cercana
And la información tiene fecha/hora de vigencia visible
```

### Escenario 2: El vecino verifica si un comercio está abierto

```gherkin
Given que soy un vecino con la app TuM2 abierta
And existe una tienda con señales operativas configuradas
When abro el detalle de esa tienda
Then veo claramente si está abierta o cerrada en este momento
And si acepta delivery hoy, veo esa señal destacada
And la información tiene un indicador de "actualizado hace X tiempo"
```

### Escenario 3: El comerciante configura señales desde Sellia

```gherkin
Given que soy un comerciante usando Sellia
When activo la señal "Abierto hoy" en Configuración > Señales operativas
Then esa señal se propaga a TuM2 en menos de 2 minutos
And cuando la desactivo, TuM2 la refleja en menos de 2 minutos
```

### Escenario 4: El vecino descubre TuM2 vía QR

```gherkin
Given que soy un vecino que escanea el QR de una tienda física
When el QR me lleva al catálogo público
Then veo productos, precios y señales operativas
And veo un acceso claro para "ver más tiendas en mi barrio"
```

### Escenario 5: Propuesta de valor comunicable en 10 segundos

```gherkin
Given que un nuevo usuario abre TuM2 por primera vez
When ve la pantalla de inicio
Then puede entender para qué sirve en menos de 10 segundos
And puede ver contenido útil sin registrarse
And la farmacia de turno y el estado de tiendas son visibles sin hacer scroll
```

---

## Archivos Clave para Implementación

- `web/src/app/StoreDiscovery.tsx` — Discovery web; primer archivo a modificar para señales operativas
- `app/.../customer/CustomerHomeScreen.kt` — Pantalla consumer Android; evolucionar a panel de señales
- `app/.../storefront/StorefrontScreen.kt` — Vidriera del comerciante; agregar controles de señales operativas
- `docs/firestore-schema.md` — Extender con `operational_signals` y `pharmacies_on_duty`
- `docs/tenant-directory-classification.md` — Ampliar con campos de señales operativas públicas

---

## Definición de Done de Esta Tarjeta

- [x] Propuesta de valor documentada y accesible al equipo
- [x] Enunciado de una oración ratificado
- [x] Las 3 capacidades centrales tienen definición funcional inicial
- [x] Segmentos OWNER, CUSTOMER y ADMIN documentados
- [x] Criterios de aceptación BDD definidos
- [x] Tarjetas derivadas listadas y priorizadas
- [x] Alcance (dentro/fuera) sin ambigüedad
