#!/usr/bin/env node
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");

const readJson = (relativePath) => {
  const absolute = path.join(root, relativePath);
  return JSON.parse(fs.readFileSync(absolute, "utf8"));
};

const contract = readJson("docs/config-params-contract.json");
const androidMap = readJson("app/config/runtime-config-map.json");
const webMap = readJson("public/config/runtime-config-map.json");

const contractKeys = new Set((contract.parameters || []).map((item) => item.name));

const assertPlatformMap = (name, mappings) => {
  const keys = Object.keys(mappings || {});
  const errors = [];

  for (const param of contractKeys) {
    const entry = mappings[param];
    if (!entry) {
      errors.push(`[${name}] missing mapping for contract param: ${param}`);
      continue;
    }
    const hasEquivalent = typeof entry.equivalent === "string" && entry.equivalent.trim().length > 0;
    const hasNotApplicable = entry.notApplicable === true;
    if (!hasEquivalent && !hasNotApplicable) {
      errors.push(`[${name}] invalid mapping for ${param}: expected 'equivalent' or 'notApplicable: true'`);
    }
  }

  for (const key of keys) {
    if (!contractKeys.has(key)) {
      errors.push(`[${name}] mapping has unknown param not present in contract: ${key}`);
    }
  }

  return errors;
};

const errors = [
  ...assertPlatformMap("android", androidMap.mappings),
  ...assertPlatformMap("web", webMap.mappings),
];

if (errors.length > 0) {
  console.error("[config-consistency] FAILED");
  for (const error of errors) {
    console.error(` - ${error}`);
  }
  process.exit(1);
}

console.log("[config-consistency] OK");
