package com.example.selliaapp.repository

import com.example.selliaapp.data.dao.ProviderInvoiceDao
import com.example.selliaapp.data.model.ProviderInvoice
import com.example.selliaapp.data.model.ProviderInvoiceStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ProviderInvoiceRepositoryTest {

    private val dao: ProviderInvoiceDao = mock()
    private val repository = ProviderInvoiceRepository(dao)

    @Test
    fun markPaid_rejectsNonPositiveAmount() = runTest {
        val invoice = testInvoice()

        val exception = runCatching {
            repository.markPaid(
                invoice = invoice,
                ref = "REF-123",
                amount = 0.0,
                paymentDateMillis = 1710000000000
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is InvalidProviderPaymentException)
        assertTrue(exception.message!!.contains("monto"))
        verify(dao, never()).updateInvoice(any())
    }

    @Test
    fun markPaid_rejectsEmptyReference() = runTest {
        val invoice = testInvoice()

        val exception = runCatching {
            repository.markPaid(
                invoice = invoice,
                ref = "   ",
                amount = 100.0,
                paymentDateMillis = 1710000000000
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is InvalidProviderPaymentException)
        assertTrue(exception.message!!.contains("referencia"))
        verify(dao, never()).updateInvoice(any())
    }

    @Test
    fun markPaid_updatesInvoiceWhenInputIsValid() = runTest {
        val invoice = testInvoice()

        repository.markPaid(
            invoice = invoice,
            ref = "  TRANSFER   9988  ",
            amount = 455.75,
            paymentDateMillis = 1710000000000
        )

        val captor = argumentCaptor<ProviderInvoice>()
        verify(dao).updateInvoice(captor.capture())
        val updated = captor.firstValue
        assertEquals(ProviderInvoiceStatus.PAGA, updated.status)
        assertEquals("TRANSFER 9988", updated.paymentRef)
        assertEquals(455.75, updated.paymentAmount!!, 0.0)
        assertEquals(1710000000000, updated.paymentDateMillis)
    }

    private fun testInvoice() = ProviderInvoice(
        id = 10,
        providerId = 7,
        number = "A-001",
        issueDateMillis = 1700000000000,
        total = 500.0
    )
}
