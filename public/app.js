import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.4/firebase-app.js";
import {
  getFirestore,
  collection,
  getDocs,
  query,
  where,
  limit
} from "https://www.gstatic.com/firebasejs/10.12.4/firebase-firestore.js";

const statusEl = document.getElementById("status");
const productCard = document.getElementById("productCard");
const contactCard = document.getElementById("contactCard");

const firebaseConfig = window.firebaseConfig;
if (!firebaseConfig || firebaseConfig.apiKey === "REEMPLAZAR_API_KEY") {
  statusEl.innerHTML =
    "<p>Configurá <strong>public/config.js</strong> con tus credenciales de Firebase.</p>";
} else {
  const app = initializeApp(firebaseConfig);
  const db = getFirestore(app);
  const params = new URLSearchParams(window.location.search);
  const queryValue = params.get("q");
  loadProduct(db, queryValue);
}

function formatCurrency(value) {
  if (value === null || value === undefined) return "-";
  try {
    return new Intl.NumberFormat("es-AR", {
      style: "currency",
      currency: "ARS"
    }).format(value);
  } catch (error) {
    return `$${value}`;
  }
}

async function loadProduct(db, queryValue) {
  if (!queryValue) {
    statusEl.innerHTML = "<p>Ingresá un código para buscar el producto.</p>";
    return;
  }

  statusEl.innerHTML = "<p>Buscando producto...</p>";

  const products = collection(db, "products");
  const candidates = [
    query(products, where("code", "==", queryValue), limit(1)),
    query(products, where("barcode", "==", queryValue), limit(1))
  ];

  let product = null;
  for (const q of candidates) {
    const snapshot = await getDocs(q);
    if (!snapshot.empty) {
      product = snapshot.docs[0].data();
      break;
    }
  }

  if (!product && queryValue.startsWith("PRODUCT-")) {
    const id = Number(queryValue.replace("PRODUCT-", ""));
    if (!Number.isNaN(id)) {
      const byId = query(products, where("id", "==", id), limit(1));
      const snapshot = await getDocs(byId);
      if (!snapshot.empty) {
        product = snapshot.docs[0].data();
      }
    }
  }

  if (!product) {
    statusEl.innerHTML = "<p>No encontramos el producto asociado a este QR.</p>";
    return;
  }

  renderProduct(product);
  renderContact();
}

function renderProduct(product) {
  document.getElementById("productName").textContent = product.name || "Producto";
  document.getElementById("productDescription").textContent = product.description || "";
  document.getElementById("productListPrice").textContent = formatCurrency(product.listPrice);
  document.getElementById("productCashPrice").textContent = formatCurrency(product.cashPrice);

  const image = document.getElementById("productImage");
  if (product.imageUrl) {
    image.src = product.imageUrl;
    image.alt = `Imagen de ${product.name}`;
  } else {
    image.src = "https://placehold.co/600x400?text=Producto";
  }

  statusEl.classList.add("hidden");
  productCard.classList.remove("hidden");
  contactCard.classList.remove("hidden");
}

function renderContact() {
  const contact = window.storeContact || {};
  const list = document.getElementById("contactList");
  list.innerHTML = "";

  const entries = [
    { label: "Tienda", value: contact.name },
    { label: "Teléfono", value: contact.phone },
    { label: "WhatsApp", value: contact.whatsapp },
    { label: "Email", value: contact.email }
  ].filter((entry) => entry.value);

  if (!entries.length) {
    list.innerHTML = "<li>Sin información de contacto cargada.</li>";
    return;
  }

  entries.forEach((entry) => {
    const li = document.createElement("li");
    li.textContent = `${entry.label}: ${entry.value}`;
    list.appendChild(li);
  });
}
