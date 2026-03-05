package com.floki.app.viewmodel

import com.floki.app.data.local.entity.ProductEntity
import com.floki.app.repository.CustomerRepository
import com.floki.app.repository.FakeCashRepository
import com.floki.app.repository.FakeInvoiceRepository
import com.floki.app.repository.FakeScanProductRepository
import com.floki.app.repository.SellDraftRepository
import com.floki.app.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SellViewModelScanTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var repo: FakeScanProductRepository
    private lateinit var vm: SellViewModel

    private val invoiceRepo = FakeInvoiceRepository()
    private val cashRepository = FakeCashRepository()
    private val sellDraftRepository: SellDraftRepository = mock()
    private val customerRepository: CustomerRepository = mock()

    @Before
    fun setup() {
        // sellDraftRepository.load() devuelve null → restoreDraft() no restaura nada
        whenever(sellDraftRepository.load()).thenReturn(null)

        repo = FakeScanProductRepository(
            initial = listOf(
                ProductEntity(
                    id = 1,
                    barcode = "123",
                    name = "Manzana",
                    price = 100.0,
                    listPrice = 110.0,
                    quantity = 5
                ),
                ProductEntity(
                    id = 2,
                    barcode = "999",
                    name = "Naranja",
                    price = 80.0,
                    listPrice = 88.0,
                    quantity = 0
                )
            )
        )

        vm = SellViewModel(
            repo = repo,
            invoiceRepo = invoiceRepo,
            cashRepository = cashRepository,
            sellDraftRepository = sellDraftRepository,
            customerRepository = customerRepository
        )
    }

    @Test
    fun `onScanBarcode devuelve found cuando existe`() = runTest {
        val res = vm.onScanBarcode("123")
        assertThat(res.foundId).isEqualTo(1)
        assertThat(res.prefillBarcode).isEqualTo("123")
    }

    @Test
    fun `onScanBarcode devuelve not found cuando no existe`() = runTest {
        val res = vm.onScanBarcode("000")
        assertThat(res.foundId).isNull()
        assertThat(res.prefillBarcode).isEqualTo("000")
    }

    @Test
    fun `addToCartByScan agrega item y acumula cantidad respetando stock`() = runTest {
        var ok = false
        vm.addToCartByScan(
            barcode = "123",
            qty = 2,
            onSuccess = { ok = true }
        )

        assertThat(ok).isTrue()

        val state1 = vm.state.value
        assertThat(state1.items).hasSize(1)
        val item = state1.items.first()
        assertThat(item.productId).isEqualTo(1)
        assertThat(item.qty).isEqualTo(2)
        assertThat(item.maxStock).isEqualTo(5)

        // Intentamos sumar 10 más (debería clamp al stock 5)
        vm.addToCartByScan("123", 10) {}
        val state2 = vm.state.value
        assertThat(state2.items.first().qty).isEqualTo(5)
    }

    @Test
    fun `addToCartByScan llama onNotFound si no existe`() = runTest {
        var notFound = false
        vm.addToCartByScan(
            barcode = "no-bar",
            qty = 1,
            onNotFound = { notFound = true }
        )
        assertThat(notFound).isTrue()
        assertThat(vm.state.value.items).isEmpty()
    }
}
