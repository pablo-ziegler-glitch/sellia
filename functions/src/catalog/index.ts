import * as legacy from "../legacyHandlers";
import { RuntimeFlagKeys } from "../config/runtime_flags";

export { parsePublicCatalogQuery, type PublicCatalogQueryDto } from "./contracts";

export const catalogRuntimeFlag = RuntimeFlagKeys.CATALOG_ROUTES;
export const syncPublicProductOnWrite = legacy.syncPublicProductOnWrite;
export const refreshPublicProducts = legacy.refreshPublicProducts;
export const publicCatalog = legacy.publicCatalog;
export const triggerStoreProductsSync = legacy.triggerStoreProductsSync;
