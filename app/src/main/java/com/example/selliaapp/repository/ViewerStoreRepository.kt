package com.example.selliaapp.repository

data class ViewerStoreSelection(
    val followedStores: List<TenantSummary> = emptyList(),
    val selectedStoreId: String? = null
)

data class PublicCatalogProduct(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val listPrice: Double?,
    val cashPrice: Double?,
    val transferPrice: Double?,
    val category: String?,
    val subcategory: String?
)

interface ViewerStoreRepository {
    suspend fun fetchViewerStoreSelection(): Result<ViewerStoreSelection>
    suspend fun followStore(store: TenantSummary): Result<Unit>
    suspend fun selectStore(storeId: String): Result<Unit>
    suspend fun fetchPublicCatalog(storeId: String): Result<List<PublicCatalogProduct>>
}
