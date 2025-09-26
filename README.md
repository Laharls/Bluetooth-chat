# Bluetooth Chat — offline P2P (Android)

**Bluetooth Chat** est une app Android de messagerie **hors-ligne** entre deux téléphones à proximité.  
Un téléphone démarre en **Serveur**, l’autre en **Client** (scan → sélectionner l’appareil → connexion).  
Une fois connectés, les messages s’échangent en direct, **sans Internet**.

> UI en **Jetpack Compose**. Code clé dans `MainActivity.kt` + `bluetooth/BluetoothConnectionManager`.

---

## ✨ Fonctionnalités

- 🔎 **Scan Bluetooth** côté Client (liste des appareils proches).
- 🏁 **Serveur Bluetooth** (attend une connexion entrante).
- 🔗 Sélection d’un appareil détecté → **connexion** (client ↔ serveur).
- 💬 **Chat temps réel** (écran dédié) une fois connecté.
- 🛡️ Gestion des **permissions** au runtime (Bluetooth / localisation).
- 🧹 Nettoyage des receivers en `onStop()` (`nettoyerReceiver()`).

---

## 🖼️ Flow utilisateur (2 boutons)

Écran **Connexion Bluetooth** (Compose) :
- **Démarrer Scan** → lance la découverte (client).  
  Une **liste d’appareils** (nom + adresse) s’affiche ; **taper** sur un item pour se connecter.
- **Démarrer Serveur** → met le tel en **écoute** ; affiche “en attente de connexion…”.

À la connexion :
- Passage automatique à **ChatScreen** :  
  - zone des **messages** (historique mémoire vive),
  - **champ texte** + bouton **Envoyer** (appelle `chatSession?.sendMessage(...)`).


---

## 🛠️ Prérequis

- **Android Studio** récent (Giraffe/Flamingo+)
- **Android SDK 33+** recommandé
- 2 téléphones Android avec **Bluetooth 4.0 LE** ou supérieur et Android version 12

### Dépendances (exemple)
Vérifiez dans `app/build.gradle.kts` que vous avez (à adapter à votre projet) :
android { buildFeatures { viewBinding = true } } // si vous l'utilisez
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Compose (exemples, selon votre BOM)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
} 
