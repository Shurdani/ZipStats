package com.zipstats.app.repository

import com.zipstats.app.model.Avatar
import com.zipstats.app.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
                // Verificar primero si el usuario sigue autenticado
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    try {
                        close()
                    } catch (e: Exception) {
                        // Ignorar errores si el canal ya está cerrado
                    }
                    return@addSnapshotListener
                }
                
                if (error != null) {
                    // Manejar PERMISSION_DENIED silenciosamente (típico al cerrar sesión)
                    val isPermissionError = error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    
                    if (isPermissionError || auth.currentUser == null) {
                        android.util.Log.w("UserRepository", "Permiso denegado o usuario no autenticado (probablemente durante logout). Cerrando listener silenciosamente.")
                        try {
                            close()
                        } catch (e: Exception) {
                            // Ignorar errores si el canal ya está cerrado
                        }
                    } else {
                    close(error)
                    }
                    return@addSnapshotListener
                }

                val user = snapshot?.toObject(User::class.java)
                if (user != null) {
                    // Verificar nuevamente antes de enviar datos
                    if (auth.currentUser == null) {
                        try {
                            close()
                        } catch (e: Exception) {
                            // Ignorar errores si el canal está cerrado
                        }
                        return@addSnapshotListener
                    }
                    
                    try {
                    trySend(user)
                    } catch (e: Exception) {
                        // Ignorar errores si el canal está cerrado (puede ocurrir durante logout)
                        android.util.Log.w("UserRepository", "Error al enviar perfil de usuario (probablemente durante logout)", e)
                    }
                }
            }

        awaitClose { subscription.remove() }
    }
} 