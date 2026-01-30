# Plan Mercado Pago: Checkout Pro (base) y Bricks (mejora opcional)

## Checkout Pro (base)

La opción **base** será **Checkout Pro (redirect)**. Esta elección prioriza menor fricción de compliance y acelera el time‑to‑market, porque Mercado Pago concentra validaciones y requisitos dentro de su propio flujo de checkout hospedado.

**Motivo (menor fricción de compliance)**
- Menos exigencias de PCI/compliance para el equipo, ya que los datos sensibles se gestionan en la página de Mercado Pago.
- Reducción de riesgos operativos y legales al delegar la captura de pago a la plataforma.

**Impacto en UX**
- El usuario es redirigido a un flujo externo (Checkout Pro) para completar el pago.
- Esto agrega un paso visible de redirección, pero suele ser un patrón conocido y confiable para el usuario final.

**Costos**
- La **API de Mercado Pago no tiene costo** por uso.
- **Existe un fee por transacción** aplicado por Mercado Pago sobre cada pago procesado.

## Bricks (mejora opcional)

**Bricks** queda como **mejora opcional** para una segunda etapa. Permite embebido directo en el checkout propio, optimizando la continuidad visual del flujo.

**Motivo (mejorar UX sin bloquear el MVP)**
- Se habilita una experiencia más integrada (sin redirección), pero requiere más validaciones y controles de compliance.
- Conviene abordarlo cuando el flujo base ya esté estable y el equipo pueda dedicar tiempo a requisitos adicionales.

**Impacto en UX**
- Checkout más fluido y consistente con la UI propia.
- Menor fricción percibida al evitar redirección externa.

**Costos**
- La **API de Mercado Pago no tiene costo** por uso.
- **Existe un fee por transacción** aplicado por Mercado Pago sobre cada pago procesado.
