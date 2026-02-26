export type PiiDomain = "users" | "customers" | "logs" | "exports";

export type PiiSensitivity = "direct" | "quasi" | "sensitive" | "operational";

export type PiiVisibility = "full" | "partial" | "masked";

export type PiiRole = "owner" | "admin" | "manager" | "cashier" | "support" | "auditor";

type FieldRule = {
  sensitivity: PiiSensitivity;
  defaultVisibility: PiiVisibility;
  byRole?: Partial<Record<PiiRole, PiiVisibility>>;
};

export const PII_FIELD_RULES: Record<PiiDomain, Record<string, FieldRule>> = {
  users: {
    email: {
      sensitivity: "direct",
      defaultVisibility: "partial",
      byRole: { owner: "full", admin: "full", auditor: "partial" },
    },
    phone: {
      sensitivity: "direct",
      defaultVisibility: "masked",
      byRole: { owner: "partial", admin: "partial", auditor: "masked" },
    },
    displayName: {
      sensitivity: "quasi",
      defaultVisibility: "partial",
      byRole: { owner: "full", admin: "full" },
    },
    documentNumber: {
      sensitivity: "sensitive",
      defaultVisibility: "masked",
      byRole: { owner: "partial", admin: "partial", auditor: "masked" },
    },
  },
  customers: {
    fullName: {
      sensitivity: "direct",
      defaultVisibility: "partial",
      byRole: { owner: "full", admin: "full", manager: "partial" },
    },
    email: {
      sensitivity: "direct",
      defaultVisibility: "masked",
      byRole: { owner: "partial", admin: "partial", manager: "masked" },
    },
    phoneE164: {
      sensitivity: "direct",
      defaultVisibility: "masked",
      byRole: { owner: "partial", admin: "partial" },
    },
    documentNumber: {
      sensitivity: "sensitive",
      defaultVisibility: "masked",
      byRole: { owner: "masked", admin: "masked", auditor: "masked" },
    },
  },
  logs: {
    actorUid: {
      sensitivity: "operational",
      defaultVisibility: "partial",
      byRole: { owner: "partial", admin: "partial", auditor: "partial" },
    },
    requesterIp: {
      sensitivity: "sensitive",
      defaultVisibility: "masked",
      byRole: { auditor: "partial" },
    },
    payerEmail: {
      sensitivity: "direct",
      defaultVisibility: "masked",
      byRole: { owner: "partial", admin: "partial", auditor: "masked" },
    },
  },
  exports: {
    storagePath: {
      sensitivity: "operational",
      defaultVisibility: "masked",
      byRole: { owner: "partial", admin: "partial", auditor: "masked" },
    },
    signedUrl: {
      sensitivity: "sensitive",
      defaultVisibility: "masked",
    },
  },
};

export type RetentionRule = {
  description: string;
  maxAgeDays: number;
};

export const PII_RETENTION_RULES: Record<PiiDomain, RetentionRule> = {
  users: {
    description: "Eventos de auditoría de cambios de usuario",
    maxAgeDays: 365,
  },
  customers: {
    description: "Eventos de CRM y customer lifecycle",
    maxAgeDays: 365,
  },
  logs: {
    description: "Logs operativos/auditoría para investigación y compliance",
    maxAgeDays: 180,
  },
  exports: {
    description: "Metadatos y artefactos de exportación/backup",
    maxAgeDays: 30,
  },
};

export const resolveFieldVisibility = (
  domain: PiiDomain,
  fieldName: string,
  role: PiiRole | null | undefined
): PiiVisibility => {
  const domainRules = PII_FIELD_RULES[domain];
  const fieldRule = domainRules[fieldName];
  if (!fieldRule) {
    return "full";
  }
  if (role && fieldRule.byRole?.[role]) {
    return fieldRule.byRole[role] as PiiVisibility;
  }
  return fieldRule.defaultVisibility;
};
