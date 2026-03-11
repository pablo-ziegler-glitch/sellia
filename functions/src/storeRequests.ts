import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { buildApprovedEmailHtml, buildRejectedEmailHtml } from "./email/storeRequestEmails";

const db = admin.firestore();

interface StoreRequestData {
  userId: string;
  email: string;
  storeName: string;
  storeDescription?: string;
  storePhone?: string;
  status: string;
}

/**
 * Firestore trigger: fires when a store_request document is updated.
 * When status changes to "approved" or "rejected":
 * 1. Creates an in-app notification for the user
 * 2. Sends an email notification
 */
export const onStoreRequestStatusChange = functions.firestore
  .document("store_requests/{requestId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data() as StoreRequestData | undefined;
    const after = change.after.data() as StoreRequestData | undefined;

    if (!before || !after) {
      return null;
    }

    // Only proceed if status actually changed
    if (before.status === after.status) {
      return null;
    }

    const requestId = context.params.requestId;
    const { userId, email, storeName, status } = after;

    if (status !== "approved" && status !== "rejected") {
      return null;
    }

    const isApproved = status === "approved";

    // 1. Create in-app notification
    const notificationData: Record<string, unknown> = {
      userId,
      type: isApproved ? "store_request_approved" : "store_request_rejected",
      title: isApproved
        ? "Tu tienda fue aprobada"
        : "Solicitud de tienda rechazada",
      body: isApproved
        ? `Tu tienda "${storeName}" ya está activa. Podés acceder a tu panel de dueño.`
        : `Tu solicitud para la tienda "${storeName}" fue rechazada. Podés intentar nuevamente o contactar soporte.`,
      read: false,
      actionRoute: isApproved ? "storefront" : null,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await db.collection("notifications").add(notificationData);

    // 2. Send email notification
    if (email) {
      try {
        const htmlContent = isApproved
          ? buildApprovedEmailHtml(storeName)
          : buildRejectedEmailHtml(storeName);

        // Use Firestore "mail" collection (Firebase Extension: Trigger Email)
        // If you don't have the extension, replace with your email service (SendGrid, etc.)
        await db.collection("mail").add({
          to: email,
          message: {
            subject: isApproved
              ? `Sellia: Tu tienda "${storeName}" fue aprobada`
              : `Sellia: Actualización sobre tu solicitud de tienda`,
            html: htmlContent,
          },
        });

        functions.logger.info(
          `Email queued for ${email} (request ${requestId}, status: ${status})`
        );
      } catch (err) {
        functions.logger.error(
          `Failed to queue email for ${email} (request ${requestId})`,
          err
        );
      }
    }

    functions.logger.info(
      `Store request ${requestId} processed: status=${status}, userId=${userId}`
    );

    return null;
  });
