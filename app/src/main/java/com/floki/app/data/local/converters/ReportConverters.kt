package com.floki.app.data.local.converters

import androidx.room.TypeConverter
import com.floki.app.data.local.entity.ReportScope

class ReportConverters {
    @TypeConverter
    fun scopeToString(scope: ReportScope): String = scope.name

    @TypeConverter
    fun stringToScope(value: String): ReportScope = ReportScope.valueOf(value)
}
