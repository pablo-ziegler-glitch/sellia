import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center py-24">
      <h1 className="text-4xl font-bold text-gray-300 mb-4">404</h1>
      <p className="text-gray-500 mb-6">
        No encontramos lo que estás buscando.
      </p>
      <Link
        href="/"
        className="text-blue-600 hover:underline font-medium"
      >
        Volver al inicio
      </Link>
    </div>
  );
}
