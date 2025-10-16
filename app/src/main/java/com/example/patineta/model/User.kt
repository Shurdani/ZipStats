package com.example.patineta.model

data class User(
    val email: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val avatar: String? = null,
    val avatarId: Int? = null,
    val totalDistance: Double = 0.0,
    val totalRecords: Int = 0,
    val co2Saved: Double = 0.0
) 