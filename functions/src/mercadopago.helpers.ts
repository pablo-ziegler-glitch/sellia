import * as admin from "firebase-admin";

export type PaymentStatus = "PENDING" | "APPROVED" | "REJECTED" | "FAILED";

export type ResolveTenantInput = {
  tenantIdFromMetadata: string;
  orderId: string;
  paymentId: string;
};

export const normalizeString = (value: unknown): string => {
  if (typeof value === "string") {
    return value.trim();
  }
  if (typeof value === "number") {
    return String(value);
  }
  return "";
};

export const extractTenantId = (payment: unknown): string => {
  const source = (payment ?? {}) as Record<string, unknown>;
  const metadata = (source.metadata ?? {}) as Record<string, unknown>;
  const additionalInfo = (source.additional_info ?? {}) as Record<string, unknown>;

  return normalizeString(
    metadata.tenantId ??
      metadata.tenant_id ??
      source.tenantId ??
      source.tenant_id ??
      additionalInfo.tenantId ??
      additionalInfo.tenant_id
  );
};

export const resolveTenantId = async (
  db: FirebaseFirestore.Firestore,
  { tenantIdFromMetadata, orderId, paymentId }: ResolveTenantInput
): Promise<string> => {
  if (tenantIdFromMetadata) {
    return tenantIdFromMetadata;
  }

  if (orderId) {
    const orderLookup = await db
      .collectionGroup("orders")
      .where(admin.firestore.FieldPath.documentId(), "==", orderId)
      .limit(1)
      .get();

    if (!orderLookup.empty) {
      return orderLookup.docs[0].ref.parent.parent?.id ?? "";
    }
  }

  const paymentLookup = await db
    .collectionGroup("payments")
    .where(admin.firestore.FieldPath.documentId(), "==", paymentId)
    .limit(1)
    .get();

  if (!paymentLookup.empty) {
    return paymentLookup.docs[0].ref.parent.parent?.id ?? "";
  }

  return "";
};

export const mapPaymentStatus = (status: unknown): PaymentStatus => {
  const normalized = normalizeString(status).toLowerCase();
  if (normalized === "approved") {
    return "APPROVED";
  }
  if (normalized === "pending" || normalized === "in_process") {
    return "PENDING";
  }
  if (normalized === "rejected" || normalized === "cancelled" || normalized === "charged_back") {
    return "REJECTED";
  }
  return "FAILED";
};
