package com.example.selliaapp.data.local.converters

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    @Test
    fun stringList_roundTrip() {
        val input = listOf("content://ticket/1", "file:///tmp/receipt.jpg")
        val json = Converters.stringListToJson(input)
        val output = Converters.jsonToStringList(json)

        assertEquals(input, output)
    }

    @Test
    fun stringList_emptyHandlesNull() {
        val output = Converters.jsonToStringList(null)

        assertEquals(emptyList<String>(), output)
    }
}
