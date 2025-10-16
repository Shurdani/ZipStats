package com.example.patineta.repository

import com.example.patineta.model.Avatar
import com.example.patineta.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val usersCollection = db.collection("users")

    suspend fun createUser(email: String, name: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        val user = User(
            email = email,
            name = name,
            photoUrl = null
        )
        usersCollection.document(userId).set(user).await()
    }

    suspend fun getUser(userId: String): User? {
        return try {
            val userDoc = usersCollection.document(userId).get().await()
            userDoc.toObject(User::class.java)
        } catch (e: Exception) {
            throw Exception("Error al obtener el usuario: ${e.message}")
        }
    }

    suspend fun updateProfile(user: User) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            try {
                usersCollection.document(currentUser.uid).set(user).await()
            } catch (e: Exception) {
                throw Exception("Error al actualizar el perfil: ${e.message}")
            }
        }
    }

    suspend fun updateAvatar(userId: String, avatar: Avatar) {
        try {
            val updates = hashMapOf<String, Any?>(
                "avatar" to avatar.emoji,
                "avatarId" to avatar.id,
                "photoUrl" to null
            )
            
            usersCollection.document(userId)
                .update(updates)
                .await()
        } catch (e: Exception) {
            throw Exception("Error al actualizar el avatar: ${e.message}")
        }
    }

    suspend fun deleteUser(userId: String) {
        try {
            usersCollection.document(userId).delete().await()
        } catch (e: Exception) {
            throw Exception("Error al eliminar el usuario: ${e.message}")
        }
    }

    suspend fun updateUserPhoto(photoUrl: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        val userRef = usersCollection.document(userId)
        
        userRef.update(
            mapOf(
                "photoUrl" to photoUrl,
                "avatar" to null,
                "avatarId" to null
            )
        ).await()
    }

    suspend fun updateUserAvatar(avatarId: Int, emoji: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        val userRef = usersCollection.document(userId)
        
        userRef.update(
            mapOf(
                "avatarId" to avatarId,
                "avatar" to emoji,
                "photoUrl" to null
            )
        ).await()
    }

    fun getUserProfile(): Flow<User> = callbackFlow {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        
        val subscription = usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val user = snapshot?.toObject(User::class.java)
                if (user != null) {
                    trySend(user)
                }
            }

        awaitClose { subscription.remove() }
    }
} 