package com.example.selliaapp.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste localmente la preventa activa para que no se pierda al salir de la pantalla.
 */
@Singleton
class SellDraftRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _hasActiveDraft = MutableStateFlow(prefs.getString(KEY_DRAFT, null) != null)
    val hasActiveDraft: StateFlow<Boolean> = _hasActiveDraft

    fun save(draft: SellDraft) {
        prefs.edit().putString(KEY_DRAFT, encode(draft)).apply()
        _hasActiveDraft.value = true
    }

    fun load(): SellDraft? {
        val raw = prefs.getString(KEY_DRAFT, null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_DRAFT).apply()
        _hasActiveDraft.value = false
    }

    private fun encode(draft: SellDraft): String {
        val items = JSONArray().apply {
            draft.items.forEach { item ->
                put(
                    JSONObject()
                        .put("productId", item.productId)
                        .put("name", item.name)
                        .put("barcode", item.barcode)
                        .put("unitPrice", item.unitPrice)
                        .put("listPrice", item.listPrice)
                        .put("cashPrice", item.cashPrice)
                        .put("transferPrice", item.transferPrice)
                        .put("qty", item.qty)
                        .put("maxStock", item.maxStock)
                )
            }
        }

        return JSONObject()
            .put("items", items)
            .put("discountPercent", draft.discountPercent)
            .put("customerDiscountPercent", draft.customerDiscountPercent)
            .put("surchargePercent", draft.surchargePercent)
            .put("paymentMethod", draft.paymentMethod)
            .put("paymentNotes", draft.paymentNotes)
            .put("orderType", draft.orderType)
            .put("selectedCustomerId", draft.selectedCustomerId)
            .put("selectedCustomerName", draft.selectedCustomerName)
            .toString()
    }

    private fun decode(raw: String): SellDraft {
        val json = JSONObject(raw)
        val itemsArray = json.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            repeat(itemsArray.length()) { index ->
                val item = itemsArray.optJSONObject(index) ?: return@repeat
                add(
                    SellDraftItem(
                        productId = item.optInt("productId", 0),
                        name = item.optString("name", ""),
                        barcode = item.optString("barcode").takeIf { it.isNotBlank() },
                        unitPrice = item.optDouble("unitPrice", 0.0),
                        listPrice = item.optDouble("listPrice", 0.0),
                        cashPrice = item.optDouble("cashPrice", 0.0),
                        transferPrice = item.optDouble("transferPrice", 0.0),
                        qty = item.optInt("qty", 1),
                        maxStock = item.optInt("maxStock", 0)
                    )
                )
            }
        }

        return SellDraft(
            items = items,
            discountPercent = json.optInt("discountPercent", 0),
            customerDiscountPercent = json.optInt("customerDiscountPercent", 0),
            surchargePercent = json.optInt("surchargePercent", 0),
            paymentMethod = json.optString("paymentMethod", "LISTA"),
            paymentNotes = json.optString("paymentNotes", ""),
            orderType = json.optString("orderType", "INMEDIATA"),
            selectedCustomerId = if (json.has("selectedCustomerId")) json.optInt("selectedCustomerId") else null,
            selectedCustomerName = json.optString("selectedCustomerName").takeIf { it.isNotBlank() }
        )
    }

    companion object {
        private const val PREFS_NAME = "sell_draft"
        private const val KEY_DRAFT = "active_draft"
    }
}

data class SellDraft(
    val items: List<SellDraftItem>,
    val discountPercent: Int,
    val customerDiscountPercent: Int,
    val surchargePercent: Int,
    val paymentMethod: String,
    val paymentNotes: String,
    val orderType: String,
    val selectedCustomerId: Int?,
    val selectedCustomerName: String?
)

data class SellDraftItem(
    val productId: Int,
    val name: String,
    val barcode: String?,
    val unitPrice: Double,
    val listPrice: Double,
    val cashPrice: Double,
    val transferPrice: Double,
    val qty: Int,
    val maxStock: Int
)
