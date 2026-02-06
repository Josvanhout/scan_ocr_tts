

# Projet TTS PDF Reader

Ce projet Android aide à lire des fichiers PDF en utilisant la synthèse vocale (TTS). Il effectue une recherche de texte dans chaque page du PDF et permet à l'utilisateur de configurer des réglages pour faciliter la recherche du texte dans les pages du document.

**Il s'agit d'un projet personnel que je partage ici. Je compte l'améliorer et l'optimiser progressivement.**

## Fonctionnalités

* Lecture de fichiers PDF en mode TTS (Text-to-Speech).
* Recherche du texte dans chaque page du PDF, page par page.
* Paramètres configurables pour ajuster la recherche du texte dans les pages.
* Interface simple et conviviale pour une utilisation facile.

[Manuel d'utilisation](./Doc/Manuel_d_utilisation.pdf)

## Prérequis

* Android 13 (API niveau 33) ou version supérieure.
* Accès à un fichier PDF à lire.
* Une connexion internet (pour télécharger des dépendances si nécessaire).

## Installation

1. Clonez ce projet sur votre machine :

   ```bash
   git clone https://github.com/ton-utilisateur/ton-projet.git
   ```

2. Ouvrez le projet dans Android Studio.

3. Compilez et exécutez l'application sur un appareil ou un émulateur.

## Configuration du projet

Le projet utilise les versions suivantes de SDK et de bibliothèques :

* **Compile SDK** : 36 (Android 14)
* **Min SDK** : 21 (Android 5.0 Lollipop)
* **Application ID** : `com.example.scan_ocr_tts`
* **Bibliothèques** :

  * Kotlin
  * Jetpack Compose

## Utilisation

1. Ouvrez un fichier PDF à lire.
2. Configurez les réglages pour ajuster la recherche de texte.
3. Lancez la lecture et laissez l'application lire le texte page par page.


## License

Ce projet est sous la licence MIT - voir le fichier [LICENSE](LICENSE) pour plus de détails.


