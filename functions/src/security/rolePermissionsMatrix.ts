export const ROLE_PERMISSIONS_MATRIX_VERSION = "2026-03-13";

/**
 * ═══════════════════════════════════════════════════════════════════
 *  ARQUITECTURA DE ROLES — 3 ROLES CANÓNICOS
 * ═══════════════════════════════════════════════════════════════════
 *
 *  1. viewer  — Cliente Final
 *     - Ve catálogos públicos de tiendas
 *     - Ve el cartel de novedades de las tiendas
 *     - Sin operaciones de caja, stock ni administración
 *
 *  2. owner   — Dueño de Tienda
 *     - Incluye todo lo del viewer (puede usar vista cliente)
 *     - Vista de gestión de tienda:
 *       · Stock: gestión completa, reportes, ajustes
 *       · Ventas: POS, reportes de ventas
 *       · Clientes: gestión de clientes de la tienda
 *       · Proveedores: gestión de proveedores
 *       · Configuración de tienda: precios, cloud, users/roles
 *       · Cargas masivas: importación bulk de datos
 *       · Backups: copia de seguridad y restauración
 *       · Sincronización: sync con datos de la nube
 *     - También puede tener staff: manager (encargado) y cashier (vendedor)
 *
 *  3. admin   — Administrador de Plataforma
 *     - Acceso completo sobre la plataforma
 *     - Por tienda: puede ver y habilitar/deshabilitar funcionalidades
 *       (por default todas están activas). NO ve datos internos de la tienda
 *       (productos, clientes, ventas, stock de cada tienda).
 *     - Funciones de backoffice de plataforma:
 *       · Gestión de tenants (lifecycle, creación, suspensión)
 *       · Feature flags por tienda
 *       · Analytics de uso de la plataforma
 *       · Anuncios globales de plataforma
 *       · Logs de auditoría
 *       · Herramientas de soporte técnico
 *       · Gestión de planes y precios de plataforma
 *
 *  Roles de staff (asignados por el owner):
 *    manager  — Encargado: ventas, caja, stock. Sin admin de tienda.
 *    cashier  — Vendedor/cajero: solo operaciones de caja y venta.
 * ═══════════════════════════════════════════════════════════════════
 */

// ─────────────────────────────────────────────────────────────────────────────
//  CANALES Y CAPACIDADES
// ─────────────────────────────────────────────────────────────────────────────

export const CHANNEL_CAPABILITIES = Object.freeze({

  /**
   * storefront — Vista pública/cliente
   * Accesible por cualquier usuario autenticado (incluso viewer).
   * Es el punto de entrada del cliente final.
   */
  storefront: Object.freeze([
    "storefront.catalog.read",      // Ver catálogo público de productos de una tienda
    "storefront.novedades.read",    // Ver cartel de novedades / banners de la tienda
    "storefront.store.read",        // Ver información pública de la tienda (nombre, horario, etc.)
  ]),

  /**
   * mobile_ops — Operaciones de alta frecuencia (app móvil POS)
   * Solo para staff operativo de la tienda (owner, manager, cashier).
   * El admin de plataforma NO opera la caja de ninguna tienda.
   */
  mobile_ops: Object.freeze([
    "sales.checkout",               // Cerrar una venta
    "cash.open",                    // Abrir caja
    "cash.audit",                   // Arqueo de caja
    "cash.movement",                // Movimiento de caja (entrada/salida)
    "cash.close",                   // Cerrar caja
    "cash.report.read",             // Ver reporte de caja
    "stock.adjust",                 // Ajuste de stock
    "stock.movement.read",          // Ver movimientos de stock
  ]),

  /**
   * web_bo_store — Gestión de tienda (backoffice del dueño)
   * Solo para owner. El dueño gestiona su propia tienda.
   * Manager puede acceder a un subconjunto (ver CHANNEL_CAPABILITY_ROLE_POLICIES).
   */
  web_bo_store: Object.freeze([
    "stock.manage",                 // Gestión completa de stock (ABM productos, categorías)
    "stock.report",                 // Reportes de stock e inventario
    "sales.manage",                 // Gestión de ventas (historial, notas, devoluciones)
    "sales.report",                 // Reportes de ventas y estadísticas
    "customers.manage",             // Gestión de clientes de la tienda (ABM, historial)
    "suppliers.manage",             // Gestión de proveedores (ABM, órdenes de compra)
    "store.config",                 // Configuración general de la tienda
    "bulk.import",                  // Carga masiva de datos (productos, clientes, stock)
    "backups.manage",               // Backup y restauración de datos de la tienda
    "cloud.sync",                   // Sincronización de datos con la nube
  ]),

  /**
   * web_bo_admin — Configuración administrativa de la tienda (owner + admin)
   * El owner gestiona la config de su tienda. El admin puede acceder para asistencia.
   */
  web_bo_admin: Object.freeze([
    "pricing.read",                 // Ver configuración de precios de la tienda
    "pricing.write",                // Modificar configuración de precios
    "cloud.config.read",            // Ver configuración de servicios cloud
    "cloud.config.write",           // Modificar configuración de servicios cloud
    "users.roles.read",             // Ver usuarios y roles de la tienda
    "users.roles.write",            // Gestionar usuarios y roles de la tienda
    "tenant.lifecycle.read",        // Ver estado del ciclo de vida del tenant
    "tenant.lifecycle.write",       // Gestionar ciclo de vida del tenant (suspender, etc.)
    "tenant.backups.read",          // Ver backups del tenant
    "tenant.backups.write",         // Crear/restaurar backups del tenant
  ]),

  /**
   * web_bo_platform — Administración de plataforma (solo admin de plataforma)
   * El admin puede ver y gestionar funcionalidades de cada tienda SIN acceder
   * a los datos internos de las tiendas (productos, clientes, ventas, stock).
   */
  web_bo_platform: Object.freeze([
    "platform.tenants.read",        // Ver listado de todos los tenants/tiendas
    "platform.tenants.lifecycle",   // Crear, suspender y eliminar tenants
    "platform.features.read",       // Ver funcionalidades habilitadas por tienda
    "platform.features.write",      // Habilitar/deshabilitar funcionalidades por tienda
    "platform.analytics.read",      // Ver analytics de uso de la plataforma
    "platform.announcements.manage",// Gestionar anuncios y banners globales de plataforma
    "platform.audit.read",          // Ver logs de auditoría de la plataforma
    "platform.support.read",        // Herramientas de soporte y diagnóstico
    "platform.plans.read",          // Ver planes y precios de la plataforma
    "platform.plans.write",         // Modificar planes y precios de la plataforma
  ]),

} as const);

export type ChannelKey = keyof typeof CHANNEL_CAPABILITIES;

// ─────────────────────────────────────────────────────────────────────────────
//  POLÍTICAS: CAPACIDAD → ROLES PERMITIDOS
// ─────────────────────────────────────────────────────────────────────────────

export const CHANNEL_CAPABILITY_ROLE_POLICIES = Object.freeze({

  storefront: Object.freeze({
    // La vista storefront es accesible para todos los roles autenticados
    "storefront.catalog.read":   Object.freeze(["viewer", "owner", "admin", "manager", "cashier"]),
    "storefront.novedades.read": Object.freeze(["viewer", "owner", "admin", "manager", "cashier"]),
    "storefront.store.read":     Object.freeze(["viewer", "owner", "admin", "manager", "cashier"]),
  }),

  mobile_ops: Object.freeze({
    // El admin de plataforma NO opera la caja de tiendas
    "sales.checkout":        Object.freeze(["owner", "manager", "cashier"]),
    "cash.open":             Object.freeze(["owner", "manager", "cashier"]),
    "cash.audit":            Object.freeze(["owner", "manager"]),
    "cash.movement":         Object.freeze(["owner", "manager", "cashier"]),
    "cash.close":            Object.freeze(["owner", "manager"]),
    "cash.report.read":      Object.freeze(["owner", "manager", "cashier"]),
    "stock.adjust":          Object.freeze(["owner", "manager"]),
    "stock.movement.read":   Object.freeze(["owner", "manager", "cashier"]),
  }),

  web_bo_store: Object.freeze({
    // Gestión de tienda: solo owner (y manager para subconjunto operativo)
    "stock.manage":          Object.freeze(["owner", "manager"]),
    "stock.report":          Object.freeze(["owner", "manager"]),
    "sales.manage":          Object.freeze(["owner", "manager"]),
    "sales.report":          Object.freeze(["owner", "manager"]),
    "customers.manage":      Object.freeze(["owner", "manager"]),
    "suppliers.manage":      Object.freeze(["owner"]),            // solo owner gestiona proveedores
    "store.config":          Object.freeze(["owner"]),            // configuración crítica: solo owner
    "bulk.import":           Object.freeze(["owner"]),            // cargas masivas: solo owner
    "backups.manage":        Object.freeze(["owner"]),            // backup/restore: solo owner
    "cloud.sync":            Object.freeze(["owner"]),            // sincronización cloud: solo owner
  }),

  web_bo_admin: Object.freeze({
    // Config administrativa: owner de la tienda y admin de plataforma
    "pricing.read":             Object.freeze(["owner", "admin"]),
    "pricing.write":            Object.freeze(["owner", "admin"]),
    "cloud.config.read":        Object.freeze(["owner", "admin"]),
    "cloud.config.write":       Object.freeze(["owner", "admin"]),
    "users.roles.read":         Object.freeze(["owner", "admin"]),
    "users.roles.write":        Object.freeze(["owner", "admin"]),
    "tenant.lifecycle.read":    Object.freeze(["owner", "admin"]),
    "tenant.lifecycle.write":   Object.freeze(["owner", "admin"]),
    "tenant.backups.read":      Object.freeze(["owner", "admin"]),
    "tenant.backups.write":     Object.freeze(["owner", "admin"]),
  }),

  web_bo_platform: Object.freeze({
    // Administración de plataforma: exclusivo del admin
    // IMPORTANTE: el admin ve funcionalidades de tiendas pero NO sus datos internos
    "platform.tenants.read":           Object.freeze(["admin"]),
    "platform.tenants.lifecycle":      Object.freeze(["admin"]),
    "platform.features.read":          Object.freeze(["admin"]),
    "platform.features.write":         Object.freeze(["admin"]),
    "platform.analytics.read":         Object.freeze(["admin"]),
    "platform.announcements.manage":   Object.freeze(["admin"]),
    "platform.audit.read":             Object.freeze(["admin"]),
    "platform.support.read":           Object.freeze(["admin"]),
    "platform.plans.read":             Object.freeze(["admin"]),
    "platform.plans.write":            Object.freeze(["admin"]),
  }),

} as const);

// ─────────────────────────────────────────────────────────────────────────────
//  POLÍTICAS DE SCOPE DE TENANT
// ─────────────────────────────────────────────────────────────────────────────

export type TenantScopeKey = "sameTenant" | "crossTenant" | "platform";

export const TENANT_SCOPE_ROLE_POLICIES: Readonly<Record<ChannelKey, Readonly<Record<TenantScopeKey, readonly string[]>>>> = Object.freeze({
  storefront: Object.freeze({
    // Storefront es público: se puede ver cualquier tienda sin ser miembro
    sameTenant:   Object.freeze(["viewer", "owner", "admin", "manager", "cashier"]),
    crossTenant:  Object.freeze(["viewer", "admin"]),  // viewer puede navegar tiendas, admin también
    platform:     Object.freeze(["admin", "superadmin"]),
  }),
  mobile_ops: Object.freeze({
    sameTenant:   Object.freeze(["owner", "manager", "cashier"]),
    crossTenant:  Object.freeze([]),
    platform:     Object.freeze(["superadmin"]),
  }),
  web_bo_store: Object.freeze({
    sameTenant:   Object.freeze(["owner", "manager"]),
    crossTenant:  Object.freeze([]),
    platform:     Object.freeze(["superadmin"]),
  }),
  web_bo_admin: Object.freeze({
    sameTenant:   Object.freeze(["owner", "admin"]),
    crossTenant:  Object.freeze([]),
    platform:     Object.freeze(["superadmin"]),
  }),
  web_bo_platform: Object.freeze({
    sameTenant:   Object.freeze([]),                   // admin opera a nivel plataforma, no dentro de un tenant
    crossTenant:  Object.freeze([]),
    platform:     Object.freeze(["admin", "superadmin"]),
  }),
});

// ─────────────────────────────────────────────────────────────────────────────
//  MÓDULOS DE BACKOFFICE (web UI)
// ─────────────────────────────────────────────────────────────────────────────

export const MODULE_ROLE_POLICIES = Object.freeze({
  // ── Storefront (vista pública/cliente) ──
  storefrontCatalog:       Object.freeze(["viewer", "owner", "admin", "manager", "cashier"]),
  storefrontNovedades:     Object.freeze(["viewer", "owner", "admin", "manager", "cashier"]),

  // ── Gestión de tienda (owner + manager limitado) ──
  stock:                   Object.freeze(["owner", "manager"]),
  sales:                   Object.freeze(["owner", "manager", "cashier"]),
  customers:               Object.freeze(["owner", "manager"]),
  suppliers:               Object.freeze(["owner"]),
  storeConfig:             Object.freeze(["owner"]),
  bulkImport:              Object.freeze(["owner"]),
  backupsStore:            Object.freeze(["owner"]),
  cloudSync:               Object.freeze(["owner"]),

  // ── Configuración administrativa de tienda (owner + admin) ──
  dashboard:               Object.freeze(["owner", "admin", "manager"]),
  pricing:                 Object.freeze(["owner", "admin"]),
  usersRoles:              Object.freeze(["owner", "admin"]),
  cloudConfig:             Object.freeze(["owner", "admin"]),
  tenantLifecycle:         Object.freeze(["owner", "admin"]),
  maintenanceRead:         Object.freeze(["owner", "admin"]),
  maintenanceWrite:        Object.freeze(["owner", "admin"]),
  backupsRead:             Object.freeze(["owner", "admin"]),
  backupsWrite:            Object.freeze(["owner", "admin"]),

  // ── Administración de plataforma (solo admin) ──
  // RESTRICCIÓN: el admin NO accede a datos internos de tiendas
  platformTenants:         Object.freeze(["admin"]),
  platformFeatures:        Object.freeze(["admin"]),
  platformAnalytics:       Object.freeze(["admin"]),
  platformAnnouncements:   Object.freeze(["admin"]),
  platformAudit:           Object.freeze(["admin"]),
  platformSupport:         Object.freeze(["admin"]),
  platformPlans:           Object.freeze(["admin"]),
} as const);

export type ModulePolicyKey = keyof typeof MODULE_ROLE_POLICIES;

// ─────────────────────────────────────────────────────────────────────────────
//  FEATURE FLAGS DE TIENDA (gestionados por admin de plataforma)
//  Por defecto todas las funcionalidades están ACTIVAS al crear una tienda.
// ─────────────────────────────────────────────────────────────────────────────

export const STORE_FEATURE_FLAGS = Object.freeze({
  "feature.storefront":       { defaultEnabled: true,  description: "Catálogo público y vista de tienda" },
  "feature.pos":              { defaultEnabled: true,  description: "Punto de venta (POS mobile)" },
  "feature.stock":            { defaultEnabled: true,  description: "Gestión de stock e inventario" },
  "feature.cash":             { defaultEnabled: true,  description: "Apertura y cierre de caja" },
  "feature.customers":        { defaultEnabled: true,  description: "Gestión de clientes" },
  "feature.suppliers":        { defaultEnabled: true,  description: "Gestión de proveedores" },
  "feature.reports":          { defaultEnabled: true,  description: "Reportes y estadísticas" },
  "feature.bulk_import":      { defaultEnabled: true,  description: "Cargas masivas de datos" },
  "feature.backups":          { defaultEnabled: true,  description: "Backup y restauración" },
  "feature.cloud_sync":       { defaultEnabled: true,  description: "Sincronización con la nube" },
  "feature.pricing_config":   { defaultEnabled: true,  description: "Configuración de precios y descuentos" },
  "feature.cloud_services":   { defaultEnabled: true,  description: "Servicios cloud (integraciones externas)" },
  "feature.novedades":        { defaultEnabled: true,  description: "Cartel de novedades / banners de tienda" },
} as const);

export type StoreFeatureFlag = keyof typeof STORE_FEATURE_FLAGS;

// ─────────────────────────────────────────────────────────────────────────────
//  HELPERS
// ─────────────────────────────────────────────────────────────────────────────

const MODULE_POLICY_ROLE_SET: Record<ModulePolicyKey, Set<string>> = Object.freeze(
  Object.entries(MODULE_ROLE_POLICIES).reduce((acc, [module, roles]) => {
    acc[module as ModulePolicyKey] = new Set(roles as readonly string[]);
    return acc;
  }, {} as Record<ModulePolicyKey, Set<string>>)
);

const TENANT_SCOPE_ROLE_SET = Object.freeze(
  Object.fromEntries(
    Object.entries(TENANT_SCOPE_ROLE_POLICIES).map(([channel, scopePolicies]) => [
      channel,
      Object.fromEntries(
        Object.entries(scopePolicies).map(([scope, roles]) => [scope, new Set(roles)])
      ),
    ])
  ) as Record<ChannelKey, Record<TenantScopeKey, Set<string>>>
);

export const hasRoleForModule = (role: unknown, module: ModulePolicyKey): boolean => {
  const normalizedRole = String(role ?? "").trim().toLowerCase();
  if (!normalizedRole) return false;
  return MODULE_POLICY_ROLE_SET[module].has(normalizedRole);
};

export const hasRoleForChannelInTenantScope = (
  role: unknown,
  channel: ChannelKey,
  tenantScope: TenantScopeKey
): boolean => {
  const normalizedRole = String(role ?? "").trim().toLowerCase();
  if (!normalizedRole) return false;
  return TENANT_SCOPE_ROLE_SET[channel][tenantScope].has(normalizedRole);
};

/**
 * Verifica si un admin de plataforma intenta acceder a datos internos de una tienda.
 * El admin SOLO puede acceder a feature flags y metadatos, no a datos operativos.
 */
export const ADMIN_STORE_DATA_RESTRICTED_MODULES: ReadonlySet<ModulePolicyKey> = new Set([
  "stock", "sales", "customers", "suppliers",
  "backupsStore", "cloudSync", "bulkImport",
  "dashboard",  // el dashboard de una tienda específica tiene datos internos
]);

export const adminCanAccessModule = (module: ModulePolicyKey): boolean => {
  return !ADMIN_STORE_DATA_RESTRICTED_MODULES.has(module);
};

// ─────────────────────────────────────────────────────────────────────────────
//  CONTRATO EXPORTADO
// ─────────────────────────────────────────────────────────────────────────────

export const PERMISSIONS_CONTRACT = Object.freeze({
  version: ROLE_PERMISSIONS_MATRIX_VERSION,
  channels: CHANNEL_CAPABILITIES,
  channelCapabilityRolePolicies: CHANNEL_CAPABILITY_ROLE_POLICIES,
  tenantScopeRolePolicies: TENANT_SCOPE_ROLE_POLICIES,
  moduleRolePolicies: MODULE_ROLE_POLICIES,
  storeFeatureFlags: STORE_FEATURE_FLAGS,
});
