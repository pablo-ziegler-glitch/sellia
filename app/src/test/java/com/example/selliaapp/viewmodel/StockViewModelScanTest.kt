package com.example.selliaapp.viewmodel


import com.example.selliaapp.data.local.entity.ProductEntity
import com.example.selliaapp.repository.FakeScanProductRepository
import com.example.selliaapp.repository.IProductRepository
import com.example.selliaapp.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockViewModelScanTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var repo: FakeScanProductRepository
    private lateinit var vm: StockViewModel

    @Before
    fun setup() {
        repo = FakeScanProductRepository(
            initial = listOf(
                ProductEntity(id = 10, barcode = "ABC", name = "Pera", price = 50.0, listPrice = 55.0, quantity = 1)
            )
        )
        vm = StockViewModel(repo as IProductRepository)
    }

    @Test
    fun `onScanBarcode encuentra existente con nombre`() = runTest {
        val res = vm.onScanBarcode("ABC")
        assertThat(res.foundId).isEqualTo(10)
        assertThat(res.prefillBarcode).isEqualTo("ABC")
        assertThat(res.name).isEqualTo("Pera")
    }

    @Test
    fun `onScanBarcode not found devuelve barcode para alta`() = runTest {
        val res = vm.onScanBarcode("XYZ")
        assertThat(res.foundId).isNull()
        assertThat(res.prefillBarcode).isEqualTo("XYZ")
        assertThat(res.name).isNull()
    }

    @Test
    fun `addStockByScan incrementa stock y retorna success`() = runTest {
        var ok = false
        vm.addStockByScan(
            barcode = "ABC",
            qty = 3,
            onSuccess = { ok = true }
        )
        assertThat(ok).isTrue()
        // Verificamos que el fake registró la llamada
        assertThat(repo.lastIncrease).isEqualTo("ABC" to 3)
        // El flujo de productos debería reflejar el nuevo stock (1 + 3 = 4)
        val newQty = repo.cachedOrEmpty().first { it.id == 10 }.quantity
        assertThat(newQty).isEqualTo(4)
    }

    @Test
    fun `addStockByScan retorna notFound si el barcode no existe`() = runTest {
        var notFound = false
        vm.addStockByScan(
            barcode = "NOPE",
            qty = 2,
            onNotFound = { notFound = true }
        )
        assertThat(notFound).isTrue()
    }
}
