package com.android.signo.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Report(
    @DocumentId var id_reporte: String = "",
    val nombreSenal: String = "",
    val leyenda: String = "",
    val callePrincipal: String = "",
    val interseccion: String = "",
    val numeracion: String = "",
    val cantidadPostes: String = "",
    val tipoPoste: String = "",
    val medida: String = "",
    val existencia: String = "",
    val estado: String = "",
    val mantencion: List<String> = emptyList(),
    val observacion: String = "",
    val groupId: String = "",
    val userUid: String = "",
    val userName: String = "",
    @ServerTimestamp val timestamp: Date? = null
)
