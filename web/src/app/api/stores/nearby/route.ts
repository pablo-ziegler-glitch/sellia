import { NextRequest, NextResponse } from "next/server";
import { getStoresByLocation } from "@/lib/catalog";

function parseNumber(value: string | null): number | null {
  if (value === null) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const lat = parseNumber(searchParams.get("lat"));
  const lng = parseNumber(searchParams.get("lng"));
  const radius = parseNumber(searchParams.get("radius")) ?? 5;

  if (
    lat === null ||
    lng === null ||
    lat < -90 ||
    lat > 90 ||
    lng < -180 ||
    lng > 180
  ) {
    return NextResponse.json(
      { error: "Invalid lat/lng" },
      { status: 400, headers: { "Cache-Control": "no-store" } }
    );
  }

  const stores = await getStoresByLocation(lat, lng, radius);
  return NextResponse.json(
    { stores },
    { headers: { "Cache-Control": "no-store" } }
  );
}
