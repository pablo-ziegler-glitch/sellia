package com.example.selliaapp.data.csv

object ProductImportTemplate {
    fun templateCsv(): String {
        return buildString {
            appendLine(
                "name,purchase_price,quantity,parent_category,category,description,imageUrl,image_urls,code,barcode,provider,brand,color,sizes,provider_sku," +
                    "min_stock,list_price,cash_price,transfer_price,transfer_net_price,ml_price,ml_3c_price,ml_6c_price,updated_at"
            )
            appendLine(
                "Shampoo 500ml,1800.00,24,Perfumería,Higiene,Shampoo para uso diario,https://example.com/shampoo.jpg," +
                    "https://example.com/shampoo-2.jpg,SKU-001,7791234567890,Proveedor Demo,Marca Demo,,,PROV-SKU-001,5,,,,,,,,2024-01-15"
            )
            appendLine(
                "Jabón Neutro x3,1200.00,50,Perfumería,Higiene,Jabón neutro suave,,," +
                    "SKU-002,,Proveedor Demo,Marca Demo,,,PROV-SKU-002,10,,,,,,,,2024-01-15"
            )
            appendLine(
                "Campera Kids,9000.00,8,Indumentaria,Abrigos,Campera térmica infantil,https://example.com/campera.jpg,," +
                    "SKU-003,7790000000003,Proveedor Demo,Marca Kids,Azul,6|8|10,PROV-SKU-003,2,11999,9999,10999,,,,,2024-01-15"
            )
        }
    }

    fun templateFileName(): String = "plantilla_productos.csv"

    fun templateMimeType(): String = "text/csv"
}
