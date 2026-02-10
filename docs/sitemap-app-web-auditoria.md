# Sitemap funcional + primera auditoría de duplicidades/inconsistencias/usabilidad

## Nota de enfoque
Se tomó una interpretación de **arquitectura de información accionable**: no solo listar rutas, sino mapear cómo llega una persona a cada opción y qué conflictos de UX aparecen para producción.

---

## 1) Sitemap de la app Android (Compose)

### 1.1. Acceso y autenticación
- `SelliaRoot`
  - Login
  - Registro
  - Login Google
  - Registro Google (flujo cliente final)
- Cuando autentica: entra a `SelliaApp`.

### 1.2. Navegación principal (Bottom bar)

#### Perfil **operador/admin**
- Inicio (`home_root`)
- Vender (`sell`)
- Caja (`cash`)
- Más (`more`)

#### Perfil **cliente final (viewer)**
- Inicio (`home_root`)
- Catálogo (`public_product_catalog`)
- Cuenta (`more`)

### 1.3. Árbol funcional (operador/admin)

- Inicio
  - Vender
  - Stock
    - Importar stock
    - Ajuste rápido de stock (por producto)
    - Reposición rápida (por producto)
    - Movimientos de stock
    - Escáner para stock

#### Diferencia clave: Ajuste rápido vs Reposición rápida (NO son iguales)
- **Ajuste rápido de stock (Quick Adjust):** corrige inventario en forma directa (+/- unidades) con **motivo** y **nota**. Se usa para conteo físico, merma, rotura, correcciones y desvíos operativos.
- **Reposición rápida (Quick Reorder):** crea una **orden de compra** al proveedor con cantidad y precio, y opcionalmente permite “recibir” para impactar stock. Se usa para abastecimiento y trazabilidad de compras.
- **Regla operativa recomendada:**
  - Si necesitás corregir una diferencia existente => **Ajuste rápido**.
  - Si necesitás comprar/reponer mercadería => **Reposición rápida**.

  - Clientes
    - Hub clientes
    - Gestionar clientes
    - Compras por cliente
    - Métricas de clientes
  - Proveedores
    - Hub proveedores
    - Gestionar proveedores
    - Facturas de proveedor
    - Pedidos de compra pendientes (reposiciones rápidas)
    - Detalle de factura de proveedor
    - Pagos a proveedor
  - Gastos
    - Hub gastos
    - Plantillas de gasto
    - Carga de gastos
    - Cashflow
  - Reportes
    - Resumen de precios
  - Catálogo público
    - Ver catálogo público
    - Escaneo público de producto
  - Caja rápida
    - Abrir caja
    - Ir al hub de caja

- Flujo de venta (nested graph)
  - Vender (`sell`)
  - Checkout (`pos_checkout`)
  - Resultado POS exitoso (`pos_success?...`)

- Caja
  - Apertura
  - Arqueo
  - Movimientos
  - Cierre
  - Reporte

- Más
  - Stock
  - Historial de stock
  - Clientes
  - Proveedores
  - Gastos
  - Reportes
  - Alertas de uso
  - Configuración
  - Usuarios y roles (si tiene permiso)
  - Sincronizar
  - Cerrar sesión

- Configuración
  - Pricing config
  - Marketing config
  - Seguridad
  - Administración de servicios cloud
  - Opciones de desarrollo
  - Alta de usuario
  - Gestión de usuarios

- Ventas
  - Facturas de venta
  - Detalle de factura de venta

- Gestión de producto y soporte operativo
  - Gestionar productos
  - Alta/edición producto (`add_product`, `add_product/{id}`)
  - QR de producto
  - Sincronización
  - Escáner para venta

### 1.4. Árbol funcional (cliente final)
- Inicio cliente
  - Abrir catálogo público
  - Escanear producto público
  - Perfil
- Catálogo público (in-app)
  - Lista
  - Detalle público de producto
  - Tarjeta pública de producto por QR
- Cuenta
  - Configuración de perfil
  - Cerrar sesión

---

## 2) Sitemap de la web pública (Firebase Hosting)

### 2.1. Páginas públicas
- `/index.html`
  - Header con navegación ancla
    - Historia (`#historia`)
    - Colección (`#productos`)
    - Contacto (`#contacto`)
  - Hero
  - Historia (video)
  - Cómo empezó
  - Somos
  - Productos (grilla)
    - Abre modal con detalle
    - Link sugerido a ficha directa: `/product.html?q=SKU`
  - Contacto (WhatsApp/Instagram/Ubicación)
  - Footer
  - Privacidad (`#privacy`)

- `/product.html`
  - Hero de producto
  - CTA WhatsApp
  - CTA “Abrir en la app” (solo modo owner)
  - CTA volver al sitio
  - Galería
  - Precios
  - Descripción
  - Talles
  - Estado/sincronización

### 2.2. Datos y comportamiento
- Carga config desde `/config.js`.
- Fallback demo desde `/data/products.json`.
- Consulta Firestore REST para producto por `code`, `barcode` o `id`.
- Polling periódico para refresco de datos.

---

## 3) Duplicidades detectadas (primer corte)

## 3.1) Rutas duplicadas eliminadas (aplicado)

- **Eliminada:** `Routes.Sell` (duplicaba `Routes.Pos` con `"sell"`).
  - **Se conserva:** `Routes.Pos` como ruta única del flujo de venta.
- **Eliminada:** `Routes.PosPayment` (duplicaba `Routes.PosCheckout` con `"pos_checkout"`).
  - **Se conserva:** `Routes.PosCheckout` como ruta única de checkout.

Impacto: navegación más mantenible, menor ambigüedad para métricas/eventos y menor riesgo de errores al escalar módulos.

---

1. **Ruta duplicada para venta**
   - `Routes.Pos` y `Routes.Sell` comparten el mismo valor (`"sell"`).
   - Impacto: ambigüedad técnica y mayor probabilidad de bugs al escalar navegación.

2. **Ruta duplicada para checkout**
   - `Routes.PosCheckout` y `Routes.PosPayment` comparten `"pos_checkout"`.
   - Impacto: deuda técnica semántica; difícil mantener métricas/eventos de navegación claros.

3. **Atajo funcionalmente repetido en Home**
   - En “Atajos”, “Ventas” y “Reportes” terminan en `onReports`.
   - Impacto UX: el usuario espera destinos diferentes.

---

## 4) Inconsistencias detectadas

1. **Branding web inconsistente**
   - Landing usa “Valkirja”, ficha de producto arranca como “Sellia”.
   - Impacto: baja confianza, percepción de sitio “mezclado”.

2. **SEO base incompleto/no productivo**
   - `canonical` y `og:url` de la landing apuntan a `example.com`.
   - Impacto: menor valor SEO y metadata social incorrecta.

3. **IA de navegación parcial en landing**
   - Existen secciones relevantes (`#como-empezo`, `#somos`) sin enlace en navegación principal.
   - Impacto: descubribilidad menor de contenido importante de marca.

---

## 5) Problemas de usabilidad (impacto negocio)

1. **Arquitectura de “Más” muy cargada para operación diaria**
   - Hay funciones core de operación (stock/clientes/proveedores/gastos) escondidas dentro de “Más”.
   - Riesgo: más taps y curva de aprendizaje más alta para personal no técnico.

2. **Ambigüedad entre “Stock” e “Historial de stock”**
   - El usuario puede no distinguir rápidamente qué tarea se resuelve en cada opción.
   - Recomendación: renombrar a “Gestión de stock” vs “Movimientos de stock”.

3. **CTA condicional de “Abrir en la app” sin señal clara**
   - Solo se habilita con `mode=owner`; para otros usuarios no hay explicación del porqué no aparece.
   - Riesgo: confusión en usuarios internos compartiendo links.

---

## 6) Backlog propuesto (iteración 1)

### Alta prioridad (semana 1)
1. Normalizar rutas duplicadas de navegación (`Pos/Sell`, `PosCheckout/PosPayment`).
2. Corregir branding y metadata SEO (`canonical`, `og:url`, nombre marca consistente).
3. Corregir atajos Home para que cada botón lleve a un destino distinto y esperado.

### Media prioridad (semana 2)
4. Rediseñar IA de “Más” (mover accesos de alto uso a Home o tabs según rol).
5. Ajustar labels de stock para reducir ambigüedad.
6. Agregar navegación superior a `#como-empezo` y `#somos`.

### Baja prioridad (semana 3)
7. Definir patrón de deep links owner/public con copy explícito en UI.
8. Instrumentar analytics de embudo (Home → acción principal → completitud) para validar mejora UX.

---

## 7) Criterios de validación sugeridos
- Tiempo medio para completar tareas frecuentes (vender, buscar cliente, ver stock) ↓
- Errores de navegación reportados ↓
- CTR de secciones de landing actualmente ocultas ↑
- Tasa de apertura de ficha `/product.html?q=...` con acción de contacto ↑

