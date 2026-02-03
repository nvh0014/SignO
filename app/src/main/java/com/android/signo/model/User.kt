package com.android.signo.model

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val rol: String = "",
    val id_grupo: String = ""
)
