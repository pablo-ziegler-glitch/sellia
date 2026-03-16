"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import type { TenantSummary } from "@/lib/catalog";

type NearbyStore = TenantSummary & { distanceKm?: number };
type FilterKey = "all" | "physical" | "online" | "delivery";

function StoreBadges({
  storeType,
  hasDelivery,
}: {
  storeType?: "physical" | "online" | "both";
  hasDelivery?: boolean;
}) {
  return (
    <>
      {(storeType === "physical" || storeType === "both") && (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-emerald-50 text-emerald-800 border border-emerald-300 dark:bg-emerald-950 dark:text-emerald-200 dark:border-emerald-700">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" /> Física
        </span>
      )}
      {(storeType === "online" || storeType === "both") && (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-800 border border-blue-300 dark:bg-blue-950 dark:text-blue-200 dark:border-blue-700">
          <span className="h-1.5 w-1.5 rounded-full bg-blue-500" /> Online
        </span>
      )}
      {hasDelivery === true && (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-800 border border-amber-300 dark:bg-amber-950 dark:text-amber-200 dark:border-amber-700">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-500" /> Delivery
        </span>
      )}
    </>
  );
}

export default function StoreDiscovery({ stores }: { stores: TenantSummary[] }) {
  const [geoStores, setGeoStores] = useState<NearbyStore[] | null>(null);
  const [filter, setFilter] = useState<FilterKey>("all");
  const [loadingGeo, setLoadingGeo] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);

  const sourceStores = geoStores ?? stores;

  const filteredStores = useMemo(() => {
    return sourceStores.filter((store) => {
      if (filter === "physical") return store.storeType === "physical" || store.storeType === "both";
      if (filter === "online") return store.storeType === "online" || store.storeType === "both";
      if (filter === "delivery") return store.hasDelivery === true;
      return true;
    });
  }, [sourceStores, filter]);

  const requestGeolocation = () => {
    setLoadingGeo(true);
    setGeoError(null);

    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const response = await fetch(
            `/api/stores/nearby?lat=${coords.latitude}&lng=${coords.longitude}&radius=5`,
            { cache: "no-store" }
          );
          if (!response.ok) throw new Error("No se pudo buscar tiendas cercanas.");
          const data = (await response.json()) as { stores: NearbyStore[] };
          setGeoStores(data.stores);
        } catch {
          setGeoError("No se pudo obtener tiendas cercanas en este momento.");
        } finally {
          setLoadingGeo(false);
        }
      },
      () => {
        setGeoError("Activá la ubicación en tu navegador para ver tiendas cercanas.");
        setLoadingGeo(false);
      }
    );
  };

  const buttonClass = (active: boolean) =>
    `px-3 py-1 rounded-full text-sm border ${
      active ? "bg-blue-600 text-white border-blue-600" : "bg-white border-gray-300 text-gray-600"
    }`;

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <button className={buttonClass(filter === "all")} onClick={() => setFilter("all")}>Todas</button>
        <button className={buttonClass(filter === "physical")} onClick={() => setFilter("physical")}>Física</button>
        <button className={buttonClass(filter === "online")} onClick={() => setFilter("online")}>Online</button>
        <button className={buttonClass(filter === "delivery")} onClick={() => setFilter("delivery")}>Con delivery</button>
      </div>

      <div className="mb-5 flex flex-wrap items-center gap-2">
        {geoStores ? (
          <button className={buttonClass(false)} onClick={() => setGeoStores(null)}>
            Ver todas las tiendas
          </button>
        ) : (
          <button className={buttonClass(false)} disabled={loadingGeo} onClick={requestGeolocation}>
            {loadingGeo ? "Buscando..." : "📍 Ver tiendas cerca mío"}
          </button>
        )}
      </div>

      {geoError && <p className="text-sm text-amber-700 mb-4">{geoError}</p>}

      {geoStores && geoStores.length === 0 && (
        <div className="mb-6 text-sm text-gray-500">
          <p>No encontramos tiendas en 5 km.</p>
          <button className="text-blue-600 mt-2" onClick={() => setGeoStores(null)}>
            Ver todas
          </button>
        </div>
      )}

      {filteredStores.length === 0 ? (
        <p className="text-gray-400">No hay tiendas para ese filtro.</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredStores.map((store) => (
            <Link href={`/tienda/${store.id}`} key={store.id}>
              <div className="block bg-white rounded-lg border border-gray-200 p-6 hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="text-lg font-semibold">{store.name}</h2>
                  {typeof store.distanceKm === "number" && (
                    <span className="text-xs text-gray-500 whitespace-nowrap bg-gray-100 px-2 py-0.5 rounded-full">
                      {store.distanceKm} km
                    </span>
                  )}
                </div>

                {(store.city || store.address) && (
                  <p className="text-sm text-gray-500 mt-1">
                    {store.address || [store.city, store.country].filter(Boolean).join(", ")}
                  </p>
                )}

                <div className="flex flex-wrap gap-1.5 mt-3">
                  <StoreBadges storeType={store.storeType} hasDelivery={store.hasDelivery} />
                </div>

                <p className="text-sm text-blue-600 mt-3">Ver catálogo →</p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
