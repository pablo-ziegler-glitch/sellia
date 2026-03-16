import type { ReactNode } from "react";
import { CartProvider } from "./CartContext";
import CartFab from "./CartFab";

type Props = {
  children: ReactNode;
  params: Promise<{ tenantId: string }>;
};

export default async function TenantLayout({ children, params }: Props) {
  const { tenantId } = await params;

  return (
    <CartProvider tenantId={tenantId}>
      {children}
      <CartFab tenantId={tenantId} />
    </CartProvider>
  );
}
