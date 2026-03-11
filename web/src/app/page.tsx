import Link from "next/link";
import { listStores } from "@/lib/catalog";

export const revalidate = 300; // ISR: revalidate every 5 minutes

export default async function HomePage() {
  const stores = await listStores();

  return (
    <div>
      <h1 className="text-2xl font-bold mb-2">Tiendas</h1>
      <p className="text-gray-500 mb-6">
        Explorá los catálogos públicos de las tiendas en Sellia.
      </p>

      {stores.length === 0 ? (
        <p className="text-gray-400">No hay tiendas publicadas todavía.</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {stores.map((store) => (
            <Link
              key={store.id}
              href={`/tienda/${store.id}`}
              className="block bg-white rounded-lg border border-gray-200 p-6 hover:shadow-md transition-shadow"
            >
              <h2 className="text-lg font-semibold">{store.name}</h2>
              <p className="text-sm text-blue-600 mt-1">Ver catálogo →</p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
