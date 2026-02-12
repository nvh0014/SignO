package com.android.signo.utils

import android.content.Context
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class SyncHelper(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()

    fun syncPendingCatastros() {
        // 1. Buscamos catastros que sean temporales (isTemp == true)
        db.collection("catastros")
            .whereEqualTo("isTemp", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) return@addOnSuccessListener

                // Procesamos uno por uno
                for (doc in documents) {
                    convertTempToReal(doc.id, doc.data)
                }
            }
    }

    private fun convertTempToReal(tempId: String, data: Map<String, Any>) {
        val docRefContador = db.collection("counters").document("catastro_counter")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRefContador)
            val currentCount = snapshot.getLong("count") ?: 3067
            val nextCount = currentCount + 1
            val nuevoId = "CAT_$nextCount"

            // 1. Actualizar contador
            transaction.update(docRefContador, "count", nextCount)

            // 2. Crear el nuevo documento con ID real
            val newData = data.toMutableMap()
            newData["isTemp"] = false // Ya no es temporal
            // Importante: Actualizar timestamp al momento de sincronización real o mantener el original

            transaction.set(db.collection("catastros").document(nuevoId), newData)

            // 3. (Opcional) Retornamos info para borrar el viejo después
            nuevoId
        }.addOnSuccessListener { nuevoId ->
            // 4. Borramos el documento temporal solo si la transacción fue exitosa
            db.collection("catastros").document(tempId).delete()
            Toast.makeText(context, "Sincronizado: $tempId -> $nuevoId", Toast.LENGTH_SHORT).show()
        }
    }
}