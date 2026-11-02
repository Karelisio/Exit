# Exit

Appli Android pour **espacer progressivement les cigarettes, puis arrêter**, sans culpabiliser.
100 % locale : pas de compte, pas de serveur, pas de publicité. Kotlin, Jetpack Compose,
Material 3 Expressive.

## Fonctionnalités

- **Configuration initiale** : consommation moyenne, prix du paquet, cigarettes par paquet, date
  cible d'arrêt ou « pas de date, on espace d'abord », paliers par jour ou par semaine.
- **« J'en fume une »** en un geste : sur l'accueil, sur le widget et dans la notification.
  Contexte optionnel (café, stress, après repas, voiture, alcool, ennui, perso). Historique
  horodaté modifiable : ajout d'un oubli, changement d'heure, suppression avec annulation.
- **Espacement progressif** : intervalle minimum entre deux cigarettes, qui augmente
  automatiquement selon le plan. Minuteur « prochaine autorisée » (anneau qui se remplit et
  morphe), notification à l'heure exacte. Fumer avant reste possible : c'est noté « en avance »,
  jamais bloqué.
- **Plan d'arrêt** : courbe de réduction jusqu'à la date cible. Si la consommation des 3 derniers
  jours dépasse nettement le plan, il se réajuste en douceur, avec un message bienveillant, et la
  date recule si la baisse devenait trop raide. Réglages manuels (±15 min, date, paliers,
  « recalculer depuis aujourd'hui ») et journal du plan.
- **Mode SOS envie**, accessible depuis tous les écrans, le widget (pendant l'arrêt), la
  notification et un raccourci de l'icône. Il propose un minuteur de 5 minutes, une respiration
  guidée par une forme M3 qui gonfle et morphe (rythme réglable, cohérence cardiaque 5·5 par
  défaut, vibrations douces) et une distraction (bouffées de fumée à éclater, idées). Chaque envie
  surmontée est comptée et célébrée par une volute qui se dissipe en particules.
- **Gains** : argent économisé, cigarettes évitées, temps de vie regagné (~20 min par cigarette),
  jours sans fumer. **Jalons santé** de 20 min à 15 ans, avec leur source (Tabac info service, OMS).
- **Stats** : consommation par jour (14 j, avec l'objectif du plan) et par semaine (8 sem.),
  intervalle moyen, plus long intervalle, meilleure série, heures fréquentes, contextes à risque
  avec un conseil.
- **Après l'arrêt** : compteur de jours sans fumer, badges. Un écart n'efface rien (historique,
  gains, badges) : on choisit de continuer l'arrêt ou de revenir au plan d'espacement.
- **Sauvegarde** : export / import JSON local, et sauvegarde automatique quotidienne dans un dossier
  au choix (Réglages → Sauvegarde locale → Sauvegarde automatique).
- **Mes raisons**, **intensité des envies**, **bonus tenu**, objectif **première du matin**,
  **objectif tirelire** et **bilan de la semaine** (dimanche 18 h).
- **Tuiles des réglages rapides** : « J'en fume une » et « SOS envie » (glisser deux fois vers le bas
  → crayon → ajouter les tuiles Exit).
- **Widget** Glance redimensionnable aux couleurs dynamiques : compte à rebours en direct,
  bouton +1, compteur du jour ou jours sans fumer.
- **Mises à jour intégrées** : vérification de la dernière release GitHub (au plus toutes les 6 h),
  changelog, téléchargement avec progression, installation via PackageInstaller.

## Étapes manuelles

### 1. Secrets GitHub (une seule fois)

Exit utilise **le même keystore qu'Écho, avec le même alias**. Les deux applis sont alors signées
avec le même certificat, ce qui sera nécessaire pour une future intégration entre elles (permission
de niveau `signature`). Il suffit de recopier les valeurs des secrets d'Écho.

Dans le dépôt : *Settings → Secrets and variables → Actions → New repository secret*.

| Secret | Valeur |
| --- | --- |
| `KEYSTORE_BASE64` | le keystore encodé en base64 (le même fichier que pour Écho) |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias de la clé (le même que pour Écho) |
| `KEY_PASSWORD` | mot de passe de la clé |

Pour réencoder le keystore si besoin :

```bash
base64 -w0 echo-release.jks > keystore.b64        # Linux
base64 -i echo-release.jks -o keystore.b64        # macOS
```

Ne commitez jamais le keystore : `*.jks`, `*.keystore` et `*.b64` sont ignorés par git.
**Gardez toujours ce même keystore** : Android refuse une mise à jour signée avec une autre clé.

Le dépôt est public : les releases (et donc les APK) y sont publiées directement, sans dépôt
séparé. Si le dépôt devenait privé, il faudrait publier les APK dans un dépôt public dédié, puis
changer `EXIT_UPDATE_REPO` dans le workflow (ou `-PexitUpdateRepo=owner/repo`).

### 2. Publier une version

Le fichier `VERSION` (à la racine, contenu `X.Y.Z`) est la **source unique de la version** :
`versionName` y est lu, et `versionCode` vaut le numéro de run GitHub Actions (toujours croissant).

1. Modifier `VERSION` (par exemple `1.1.0`).
2. Facultatif : ajouter une section `## 1.1.0` dans `CHANGELOG.md`. Sinon, le changelog reprend
   les messages de commit depuis la release précédente.
3. Pousser sur `main` (ou fusionner la pull request).

À chaque push sur `main`, GitHub Actions lit `VERSION` :

- si le tag `vX.Y.Z` n'existe pas encore, il construit l'APK signé, crée le tag et publie une
  release GitHub avec l'APK `exit-X.Y.Z.apk` et le changelog ;
- si le tag existe déjà, il construit seulement l'APK (vérification), téléchargeable dans les
  artefacts du run.

Les autres branches lancent les tests et un build de vérification. L'appli installée détecte la
nouvelle release au lancement suivant (au plus toutes les 6 h, ou tout de suite via Réglages →
Version).

### 3. Première installation

1. Ouvrir la dernière release du dépôt depuis le téléphone et télécharger `exit-X.Y.Z.apk`.
2. Autoriser le navigateur (ou le gestionnaire de fichiers) à installer des applis inconnues.
3. Ouvrir Exit : l'onboarding présente l'appli, demande la configuration, puis chaque autorisation
   (une page chacune), avec une coche animée dès qu'elle est accordée.

### 4. Téléphone Honor (MagicOS)

MagicOS ferme agressivement les applis en arrière-plan. En plus de l'exclusion de l'optimisation de
batterie demandée par l'onboarding :

- *Paramètres → Batterie → Lancement d'applis → Exit* : désactiver la gestion automatique et
  activer les trois options (lancement automatique, lancement secondaire, exécution en
  arrière-plan). Le bouton « Ouvrir « Lancement d'applis » » de l'onboarding et de l'écran
  Autorisations y mène directement quand c'est possible.
- Facultatif : verrouiller Exit dans les applis récentes (glisser la carte vers le bas).

En filet de sécurité, une tâche WorkManager replanifie les alarmes toutes les 6 h, ainsi qu'au
redémarrage, après une mise à jour et après un changement d'heure.

### 5. Widget

Appui long sur l'écran d'accueil → Widgets → Exit. Le widget principal est redimensionnable de 1×1 à 4×2, avec un anneau de progression autour du +1. Un second widget « SOS envie » (1×1) ouvre directement le mode SOS. Pendant
l'espacement, le bouton note une cigarette (+1). Pendant l'arrêt, il ouvre le mode SOS.

## Développement local

Prérequis : JDK 21 et le SDK Android (plateforme 37).

```bash
./gradlew :app:assembleDebug        # APK debug (paquet com.karelisio.exit.debug, installable à côté)
./gradlew :app:testDebugUnitTest    # tests : moteur du plan, gains, stats, rendu des écrans (Robolectric)
./gradlew :app:testDebugUnitTest --tests '*ScreensTest*' -PexitScreenshots=true
                                    # captures des écrans dans app/build/screenshots
```

Pour signer une release en local : définir `EXIT_KEYSTORE_FILE`, `EXIT_KEYSTORE_PASSWORD`,
`EXIT_KEY_ALIAS` et `EXIT_KEY_PASSWORD`, puis lancer `./gradlew :app:assembleRelease`.

## Architecture

```
app/src/main/java/com/karelisio/exit/
├── data/        Room (cigarettes, envies, journal du plan, configuration), dépôt, sauvegarde JSON
├── domain/      Moteur d'espacement et de plan, gains, jalons santé, badges, stats (fonctions pures, testées)
├── notify/      Notifications, alarmes exactes (AlarmManager), WorkManager, redémarrage
├── widget/      Widget Glance (Chronometer système pour le compte à rebours)
├── update/      Vérification des releases GitHub, téléchargement, PackageInstaller
├── system/      Autorisations (notifications, alarmes exactes, batterie, installation)
└── ui/          Compose : thème Material 3 Expressive, composants animés, écrans
```

- **Thème** : `MaterialExpressiveTheme`, couleurs dynamiques (repli vert menthe / bleu air frais),
  `MotionScheme.expressive()`, typographie expressive, mode sombre automatique.
- **Animations signature** : anneau minuteur morphé (`ui/components/TimerRing.kt`), volute de
  fumée en particules (`SmokeBurst.kt`), respiration SOS morphée (`ui/sos/SosScreen.kt`). Les
  animations décoratives en boucle s'arrêtent si le réglage système « Supprimer les animations »
  est actif.
- **Navigation** : Navigation Compose typée, transitions partagées (FAB SOS → écran SOS, carte du
  plan → écran du plan), retour prédictif.

## Textes et traduction

Tous les textes sont dans `app/src/main/res/values/` :

- `strings_ui.xml` : interface, notifications, widget, mises à jour (clés préfixées par écran) ;
- `strings_domain.xml` : déclencheurs, jalons santé, badges, autorisations ;
- `strings_core.xml` : messages du plan, navigation, `format_locale` (langue des dates et montants).

Pour ajouter une langue (par exemple l'anglais), copier ces trois fichiers dans `values-en/`,
traduire les textes et mettre `format_locale` à `en-GB`. Les messages du journal du plan déjà
enregistrés restent dans la langue où ils ont été écrits.

## Sources

- Tabac info service, « Les bénéfices de l'arrêt » : <https://www.tabac-info-service.fr>
- OMS, « Tobacco: health benefits of smoking cessation » : <https://www.who.int>
- Temps de vie : Jackson S. et al., *Addiction*, 2025 (University College London), environ
  20 minutes par cigarette.

Exit ne remplace pas un avis médical. Tabac info service : **39 89** (service gratuit).
