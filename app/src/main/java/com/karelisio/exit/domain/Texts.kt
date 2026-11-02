package com.karelisio.exit.domain

import com.karelisio.exit.system.ExitPermission
import com.karelisio.exit.str

// Textes localisés des modèles du domaine (le domaine ne stocke que des identifiants de ressources).
val Trigger.label: String get() = str(labelRes)
val HealthSource.label: String get() = str(labelRes)
val HealthMilestone.label: String get() = str(labelRes)
val HealthMilestone.title: String get() = str(titleRes)
val HealthMilestone.description: String get() = str(descriptionRes)
val Badge.title: String get() = str(titleRes)
val Badge.description: String get() = str(descriptionRes)
val ExitPermission.title: String get() = str(titleRes)
val ExitPermission.rationale: String get() = str(rationaleRes)
val ExitPermission.detail: String get() = str(detailRes)
