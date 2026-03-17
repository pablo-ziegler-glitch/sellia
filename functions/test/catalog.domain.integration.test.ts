import { describe, expect, it } from "vitest";
import { catalogRuntimeFlag, parsePublicCatalogQuery, publicCatalog } from "../src/catalog";

describe("catalog domain integration", () => {
  it("expone handlers existentes y contratos", () => {
    expect(publicCatalog).toBeTruthy();
    expect(catalogRuntimeFlag).toBe("catalog.routes.enabled");
    expect(parsePublicCatalogQuery({ tenantId: "store-a", sort: "name_asc" })).toEqual({
      tenantId: "store-a",
      sort: "name_asc",
    });
  });
});
