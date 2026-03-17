import { describe, expect, it } from "vitest";
import { manageTenantOwnership, parseManageTenantOwnershipInput } from "../src/orders";

describe("orders domain integration", () => {
  it("expone ownership handler", () => {
    expect(manageTenantOwnership).toBeTruthy();
    expect(parseManageTenantOwnershipInput({ tenantId: "tenant-a", ownerUid: "uid-1" })).toEqual({
      tenantId: "tenant-a",
      ownerUid: "uid-1",
    });
  });
});
