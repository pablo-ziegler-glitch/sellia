import { describe, expect, it } from "vitest";
import {
  buildRoleScopedOwnershipResponse,
  maskEmail,
  maskIdentifier,
  maskPhone,
  redactObject,
} from "../src/redaction";

describe("PII redaction utilities", () => {
  it("masks email preserving minimal domain context", () => {
    expect(maskEmail("owner@sellia.app")).toBe("ow***@sellia.app");
  });

  it("masks phone keeping only suffix", () => {
    expect(maskPhone("+54 9 11 2233-4455")).toBe("***********55");
  });

  it("masks identifier with short and long inputs", () => {
    expect(maskIdentifier("abc")).toBe("a***");
    expect(maskIdentifier("user-12345")).toBe("us***45");
  });

  it("redacts only sensitive keys recursively", () => {
    const payload = {
      status: "bad_request",
      payer: {
        email: "client@example.com",
        phone: "+54 9 11 2222 3333",
      },
      metadata: {
        ownerUid: "owner-uid-001",
        orderId: "order-123",
      },
    };

    expect(redactObject(payload)).toEqual({
      status: "bad_request",
      payer: {
        email: "cl***@example.com",
        phone: "***********33",
      },
      metadata: {
        ownerUid: "ow***01",
        orderId: "order-123",
      },
    });
  });
});

describe("ownership payload role-based visibility", () => {
  const responseInput = {
    tenantId: "tenant-a",
    action: "ASSOCIATE_OWNER" as const,
    targetUid: "uid-target-001",
    ownerUids: ["uid-owner-001", "uid-owner-002"],
    delegatedStoreUids: ["uid-manager-001"],
  };

  it("returns unmasked identifiers for global admins", () => {
    expect(buildRoleScopedOwnershipResponse(responseInput, true)).toEqual(responseInput);
  });

  it("masks identifiers for tenant owners/admins", () => {
    expect(buildRoleScopedOwnershipResponse(responseInput, false)).toEqual({
      tenantId: "tenant-a",
      action: "ASSOCIATE_OWNER",
      targetUid: "ui***01",
      ownerUids: ["ui***01", "ui***02"],
      delegatedStoreUids: ["ui***01"],
    });
  });
});
