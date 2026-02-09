package com.example.selliaapp.sync

/* [ANTERIOR]
... si ya tenías un repositorio de sync, dejalo y usalo ...
*/

/* [NUEVO] Interfaz mínima para compilar. Adaptá las funciones a tu caso real. */
interface SyncRepository {
    suspend fun pushPending()
    suspend fun pullRemote()
    suspend fun runSync(includeBackup: Boolean)
}
