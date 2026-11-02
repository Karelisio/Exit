# Room, WorkManager, Glance, kotlinx.serialization et OkHttp embarquent leurs règles R8.

# Glance instancie les ActionCallback par réflexion (getDeclaredConstructor().newInstance()) :
# en mode R8 complet, le constructeur sans argument serait supprimé.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
