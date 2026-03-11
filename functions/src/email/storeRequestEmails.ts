/**
 * Email templates for store request approval/rejection notifications.
 */

export function buildApprovedEmailHtml(storeName: string): string {
  return `
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Tienda aprobada</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; }
    .header { background: #1a73e8; color: #ffffff; padding: 24px; text-align: center; }
    .header h1 { margin: 0; font-size: 24px; }
    .content { padding: 32px 24px; }
    .content h2 { color: #1a73e8; margin-top: 0; }
    .content p { color: #333333; line-height: 1.6; }
    .highlight { background: #e8f0fe; border-radius: 8px; padding: 16px; margin: 16px 0; }
    .highlight strong { color: #1a73e8; }
    .footer { padding: 16px 24px; text-align: center; color: #999999; font-size: 12px; border-top: 1px solid #eeeeee; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>Sellia</h1>
    </div>
    <div class="content">
      <h2>Tu tienda fue aprobada</h2>
      <div class="highlight">
        <strong>${escapeHtml(storeName)}</strong>
      </div>
      <p>Tu solicitud para crear la tienda <strong>${escapeHtml(storeName)}</strong> fue aprobada.</p>
      <p>Ya podés acceder a tu panel de dueño desde la aplicación para:</p>
      <ul>
        <li>Configurar tu Vidriera Pública</li>
        <li>Agregar productos</li>
        <li>Personalizar tu tienda para tus clientes</li>
      </ul>
      <p>Abrí la app Sellia y entrá a "Mi tienda" para comenzar.</p>
    </div>
    <div class="footer">
      <p>Este email fue enviado automáticamente por Sellia. No respondas a este mensaje.</p>
    </div>
  </div>
</body>
</html>`;
}

export function buildRejectedEmailHtml(storeName: string): string {
  return `
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Solicitud de tienda</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; }
    .header { background: #1a73e8; color: #ffffff; padding: 24px; text-align: center; }
    .header h1 { margin: 0; font-size: 24px; }
    .content { padding: 32px 24px; }
    .content h2 { color: #d93025; margin-top: 0; }
    .content p { color: #333333; line-height: 1.6; }
    .highlight { background: #fce8e6; border-radius: 8px; padding: 16px; margin: 16px 0; }
    .highlight strong { color: #d93025; }
    .footer { padding: 16px 24px; text-align: center; color: #999999; font-size: 12px; border-top: 1px solid #eeeeee; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>Sellia</h1>
    </div>
    <div class="content">
      <h2>Solicitud no aprobada</h2>
      <div class="highlight">
        <strong>${escapeHtml(storeName)}</strong>
      </div>
      <p>Tu solicitud para crear la tienda <strong>${escapeHtml(storeName)}</strong> no fue aprobada en esta oportunidad.</p>
      <p>Podés volver a intentarlo desde la app o contactar a soporte para más información.</p>
    </div>
    <div class="footer">
      <p>Este email fue enviado automáticamente por Sellia. No respondas a este mensaje.</p>
    </div>
  </div>
</body>
</html>`;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
