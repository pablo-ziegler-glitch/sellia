package com.example.selliaapp.data.csv

object ProductImportTemplate {
    fun templateCsv(): String {
        return buildString {
            appendLine("code,barcode,name,quantity,purchase_price,price,list_price,cash_price,transfer_price,transfer_net_price,ml_price,ml_3c_price,ml_6c_price,category,min_stock,description,image_url")
            appendLine("SKU-001,7791234567890,Shampoo 500ml,24,1800.00,2500.00,2600.00,2550.00,2580.00,2450.00,2800.00,2900.00,3000.00,Higiene,5,Shampoo para uso diario,https://example.com/shampoo.jpg")
            appendLine("SKU-002,,Jabón Neutro x3,50,1200.00,1800.00,1900.00,1850.00,1880.00,1780.00,2100.00,2200.00,2300.00,Higiene,10,Jabón neutro suave,")
        }
    }

    fun templateFileName(): String = "plantilla_productos.csv"

    fun templateMimeType(): String = "application/vnd.ms-excel"
}
