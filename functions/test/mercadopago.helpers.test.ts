import { describe, expect, it } from "vitest";
import { extractTenantId, mapPaymentStatus } from "../src/mercadopago.helpers";

describe("extractTenantId", () => {
  it("returns tenantId from metadata.tenantId", () => {
    expect(extractTenantId({ metadata: { tenantId: "tenant-a" } })).toBe("tenant-a");
  });

  it("returns tenantId from tenant_id", () => {
    expect(extractTenantId({ tenant_id: "tenant-b" })).toBe("tenant-b");
  });

  it("returns tenantId from additional_info", () => {
    expect(extractTenantId({ additional_info: { tenant_id: "tenant-c" } })).toBe("tenant-c");
  });

  it("returns empty string when not found", () => {
    expect(extractTenantId({})).toBe("");
    expect(extractTenantId(null)).toBe("");
    expect(extractTenantId(undefined)).toBe("");
  });
});

describe("mapPaymentStatus", () => {
  it("maps approved to APPROVED", () => {
    expect(mapPaymentStatus("approved")).toBe("APPROVED");
  });

  it("maps pending to PENDING", () => {
    expect(mapPaymentStatus("pending")).toBe("PENDING");
  });

  it("maps in_process to PENDING", () => {
    expect(mapPaymentStatus("in_process")).toBe("PENDING");
  });

  it("maps rejected to REJECTED", () => {
    expect(mapPaymentStatus("rejected")).toBe("REJECTED");
  });

  it("maps cancelled to REJECTED", () => {
    expect(mapPaymentStatus("cancelled")).toBe("REJECTED");
  });

  it("maps charged_back to REJECTED", () => {
    expect(mapPaymentStatus("charged_back")).toBe("REJECTED");
  });

  it("maps unknown statuses to FAILED", () => {
    expect(mapPaymentStatus("foo")).toBe("FAILED");
    expect(mapPaymentStatus(null)).toBe("FAILED");
  });
});
