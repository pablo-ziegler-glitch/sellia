#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const repoRoot = path.resolve(__dirname, "..", "..");
const backendMatrixFile = path.join(repoRoot, "functions", "src", "security", "rolePermissionsMatrix.ts");
const frontendPermissionsFile = path.join(repoRoot, "public", "admin", "permissions.js");
const docsMatrixFile = path.join(repoRoot, "docs", "security", "ROLE_PERMISSIONS_MATRIX.md");

const parseVersion = (content) => {
  const match = content.match(/ROLE_PERMISSIONS_MATRIX_VERSION\s*=\s*"([^"]+)"/);
  return match ? match[1] : null;
};

const parseDocsVersion = (content) => {
  const match = content.match(/- Versión de matriz:\s*`([^`]+)`/);
  return match ? match[1] : null;
};

const parseObjectLiteral = (content, declarationName) => {
  const declarationIndex = content.indexOf(`${declarationName} = Object.freeze({`);
  if (declarationIndex < 0) {
    throw new Error(`No se encontró ${declarationName}`);
  }

  const start = content.indexOf("{", declarationIndex);
  let depth = 0;
  let end = -1;
  for (let i = start; i < content.length; i += 1) {
    const char = content[i];
    if (char === "{") depth += 1;
    if (char === "}") {
      depth -= 1;
      if (depth === 0) {
        end = i;
        break;
      }
    }
  }
  if (end < 0) {
    throw new Error(`No se pudo parsear ${declarationName}`);
  }

  const objectBody = content.slice(start + 1, end);
  const result = {};
  for (const rawLine of objectBody.split("\n")) {
    const line = rawLine.trim();
    if (!line || line.startsWith("//")) continue;
    const match = line.match(/^([A-Za-z][A-Za-z0-9_]*)\s*:\s*Object\.freeze\(\[(.*?)\]\)|^([A-Za-z][A-Za-z0-9_]*)\s*:\s*\[(.*?)\]/);
    if (!match) continue;
    const key = match[1] || match[3];
    const valuesRaw = match[2] || match[4] || "";
    const values = valuesRaw
      .split(",")
      .map((value) => value.trim().replace(/^"|"$/g, ""))
      .filter(Boolean);
    result[key] = values;
  }
  return result;
};

const parseDocsModuleTable = (content) => {
  const lines = content.split("\n");
  const start = lines.findIndex((line) => line.trim() === "| Módulo | owner | admin | manager | cashier | viewer |");
  if (start < 0) {
    throw new Error("No se encontró tabla de módulos en docs/security/ROLE_PERMISSIONS_MATRIX.md");
  }

  const rows = {};
  for (let i = start + 2; i < lines.length; i += 1) {
    const line = lines[i].trim();
    if (!line.startsWith("|")) break;
    const cells = line.split("|").map((cell) => cell.trim()).filter(Boolean);
    if (cells.length < 6) continue;
    const [module, owner, admin, manager, cashier, viewer] = cells;
    const granted = [];
    if (owner.includes("✅")) granted.push("owner");
    if (admin.includes("✅")) granted.push("admin");
    if (manager.includes("✅")) granted.push("manager");
    if (cashier.includes("✅")) granted.push("cashier");
    if (viewer.includes("✅")) granted.push("viewer");
    rows[module] = granted;
  }
  return rows;
};

const parseDocsRouteTable = (content) => {
  const lines = content.split("\n");
  const start = lines.findIndex((line) => line.trim() === "| Ruta | Módulo |");
  if (start < 0) {
    throw new Error("No se encontró tabla de rutas en docs/security/ROLE_PERMISSIONS_MATRIX.md");
  }

  const map = {};
  for (let i = start + 2; i < lines.length; i += 1) {
    const line = lines[i].trim();
    if (!line.startsWith("|")) break;
    const cells = line.split("|").map((cell) => cell.trim()).filter(Boolean);
    if (cells.length < 2) continue;
    const [route, module] = cells;
    map[route.replace(/`/g, "")] = module.replace(/`/g, "");
  }
  return map;
};

const sortValues = (obj) =>
  Object.fromEntries(
    Object.entries(obj)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, values]) => [key, [...values].sort()])
  );

const deepEqual = (a, b) => JSON.stringify(sortValues(a)) === JSON.stringify(sortValues(b));

const backendContent = fs.readFileSync(backendMatrixFile, "utf8");
const frontendContent = fs.readFileSync(frontendPermissionsFile, "utf8");
const docsContent = fs.readFileSync(docsMatrixFile, "utf8");

const backendVersion = parseVersion(backendContent);
const frontendVersion = parseVersion(frontendContent);
const docsVersion = parseDocsVersion(docsContent);

const backendModules = parseObjectLiteral(backendContent, "MODULE_ROLE_POLICIES");
const frontendModules = parseObjectLiteral(frontendContent, "MODULE_ROLE_POLICIES");
const docsModules = parseDocsModuleTable(docsContent);

const parseFrontendRoutePolicies = (content, modulePolicies) => {
  const declarationIndex = content.indexOf("ROUTE_POLICIES = Object.freeze({");
  if (declarationIndex < 0) {
    throw new Error("No se encontró ROUTE_POLICIES");
  }
  const start = content.indexOf("{", declarationIndex);
  let depth = 0;
  let end = -1;
  for (let i = start; i < content.length; i += 1) {
    const char = content[i];
    if (char === "{") depth += 1;
    if (char === "}") {
      depth -= 1;
      if (depth === 0) {
        end = i;
        break;
      }
    }
  }
  const body = content.slice(start + 1, end);
  const routes = {};
  for (const rawLine of body.split("\n")) {
    const line = rawLine.trim();
    if (!line) continue;
    const match = line.match(/^"([^"]+)"\s*:\s*MODULE_ROLE_POLICIES\.([A-Za-z][A-Za-z0-9_]*)/);
    if (!match) continue;
    routes[match[1]] = modulePolicies[match[2]] || [];
  }
  return routes;
};

const frontendRoutes = parseFrontendRoutePolicies(frontendContent, frontendModules);
const docsRouteToModule = parseDocsRouteTable(docsContent);
const expectedRouteRoles = {};
for (const [route, module] of Object.entries(docsRouteToModule)) {
  expectedRouteRoles[route] = frontendModules[module] || [];
}

const failures = [];

if (!backendVersion || !frontendVersion || !docsVersion) {
  failures.push("No se pudo leer ROLE_PERMISSIONS_MATRIX_VERSION en una de las capas");
} else if (!(backendVersion === frontendVersion && frontendVersion === docsVersion)) {
  failures.push(`ROLE_PERMISSIONS_MATRIX_VERSION inconsistente (functions=${backendVersion}, public=${frontendVersion}, docs=${docsVersion})`);
}

if (!deepEqual(backendModules, frontendModules)) {
  failures.push("MODULE_ROLE_POLICIES difiere entre functions/src y public/admin");
}

if (!deepEqual(backendModules, docsModules)) {
  failures.push("MODULE_ROLE_POLICIES difiere entre functions/src y docs/security");
}

if (!deepEqual(frontendRoutes, expectedRouteRoles)) {
  failures.push("ROUTE_POLICIES no coincide con el mapeo de rutas en docs/security");
}

if (failures.length > 0) {
  console.error("❌ Drift detectado en matriz de permisos:");
  for (const failure of failures) {
    console.error(` - ${failure}`);
  }
  process.exit(1);
}

console.log("✅ Matriz de permisos consistente entre backend, frontend y documentación.");
