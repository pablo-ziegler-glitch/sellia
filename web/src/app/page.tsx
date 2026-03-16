import { listStores } from "@/lib/catalog";
import StoreDiscovery from "./StoreDiscovery";

export const revalidate = 300;

export default async function HomePage() {
  const stores = await listStores();

  return (
    <div>
      <h1 className="text-2xl font-bold mb-2">Tiendas</h1>
      <p className="text-gray-500 mb-6">
        Explorá los catálogos públicos de las tiendas en Sellia.
      </p>

      <StoreDiscovery stores={stores} />
    </div>
  );
}
