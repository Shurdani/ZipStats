# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========================================
# REGLAS ESPECÍFICAS PARA FIREBASE Y DESERIALIZACIÓN
# ========================================

# Mantener todas las clases de datos del modelo para Firebase
-keep class com.zipstats.app.model.** { *; }

# Mantener constructores sin argumentos para deserialización
-keepclassmembers class com.zipstats.app.model.** {
    <init>();
}

# Reglas específicas para Firebase Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Mantener clases de datos que se usan con Firebase
-keep class com.zipstats.app.model.Record { *; }
-keep class com.zipstats.app.model.User { *; }
-keep class com.zipstats.app.model.Vehicle { *; }
-keep class com.zipstats.app.model.Scooter { *; }
-keep class com.zipstats.app.model.Route { *; }
-keep class com.zipstats.app.model.RoutePoint { *; }
-keep class com.zipstats.app.model.Repair { *; }
-keep class com.zipstats.app.model.Achievement { *; }
-keep class com.zipstats.app.model.Avatar { *; }
-keep class com.zipstats.app.model.MonthlyStatistics { *; }

# Mantener clases de ViewModel y UI State
-keep class com.zipstats.app.ui.**.ViewModel { *; }
-keep class com.zipstats.app.ui.**.*UiState { *; }
-keep class com.zipstats.app.ui.statistics.** { *; }
-keep class com.zipstats.app.ui.achievements.** { *; }
-keep class com.zipstats.app.ui.profile.** { *; }

# Mantener clases de repositorio
-keep class com.zipstats.app.repository.** { *; }

# Mantener clases de servicio
-keep class com.zipstats.app.service.** { *; }

# Reglas para Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Reglas para Retrofit y Gson
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.squareup.retrofit2.** { *; }

# Reglas para Cloudinary
-keep class com.cloudinary.** { *; }

# Reglas para Apache POI
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.w3.x2000.x09.xmldsig.** { *; }
-keep class org.etsi.uri.x01903.v13.** { *; }

# Reglas específicas para esquemas de Office
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.** { *; }
-keep class org.openxmlformats.schemas.officeDocument.x2006.** { *; }
-keep class org.openxmlformats.schemas.spreadsheetml.x2006.main.** { *; }
-keep class org.openxmlformats.schemas.presentationml.x2006.main.** { *; }
-keep class org.openxmlformats.schemas.drawingml.x2006.** { *; }

# Mantener clases de esquemas XML que POI necesita
-keep class org.apache.xmlbeans.** { *; }
-keep class org.apache.xmlbeans.impl.** { *; }
-keep class org.apache.xmlbeans.impl.values.** { *; }
-keep class org.apache.xmlbeans.impl.schema.** { *; }

# Reglas para evitar ofuscación de clases de POI
-keepnames class org.apache.poi.** { *; }
-keepnames class org.openxmlformats.schemas.** { *; }
-keepnames class org.w3.x2000.x09.xmldsig.** { *; }

# Reglas específicas para clases que están causando problemas
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocVars { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTEastAsianLayout { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTEdnDocProps { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTEdnPos { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFFDDList { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFFHelpText { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFFStatusText { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFFTextType { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnDocProps { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHeaders { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTKinsoku { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLevelSuffix { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLineNumber { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLongHexNumber { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvlLegacy { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMacroName { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMailMerge { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMathCtrlDel { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMathCtrlIns { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMultiLevelType { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPicBullet { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTObjectEmbed { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTObjectLink { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrChange { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTProof { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTReadingModeInkLockDown { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSaveThroughXslt { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPrChange { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShapeDefaults { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSmartTagType { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStylePaneFilter { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyleSort { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridChange { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPrChange { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPrExChange { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblStylePr { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPrChange { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTextEffect { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTextboxTightWrap { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPrChange { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrackChangeNumbering { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrackChangesView { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTwipsMeasure { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTUnsignedDecimalNumber { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTView { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTWriteProtection { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.CTWritingStyle { *; }

# Mantener enums y tipos de esquemas
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.STChapterSep { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.STDropCap { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.STPTabAlignment { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.STPTabLeader { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.STPTabRelativeTo { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageBorderZOrder { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.STZoom { *; }

# Reglas para XMLDSIG
-keep class org.w3.x2000.x09.xmldsig.KeyInfoType { *; }
-keep class org.w3.x2000.x09.xmldsig.SignatureMethodType { *; }
-keep class org.w3.x2000.x09.xmldsig.TransformsType { *; }

# Regla general para evitar problemas con POI - mantener todas las clases de esquemas
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.x2000.** { *; }

# Reglas adicionales para POI
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.w3.x2000.**
-dontwarn org.etsi.uri.**

# Reglas para Microsoft Office schemas
-keep class com.microsoft.schemas.** { *; }
-keep class com.microsoft.schemas.office.** { *; }
-keep class com.microsoft.schemas.office.office.** { *; }
-keep class com.microsoft.schemas.office.powerpoint.** { *; }
-keep class com.microsoft.schemas.office.visio.** { *; }
-keep class com.microsoft.schemas.office.word.** { *; }
-keep class com.microsoft.schemas.office.x2006.** { *; }
-keep class com.microsoft.schemas.vml.** { *; }

# Reglas para Glide (Cloudinary)
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# Reglas para Picasso (Cloudinary)
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**

# Reglas para AWT (usado por POI)
-keep class java.awt.** { *; }
-dontwarn java.awt.**

# Reglas para GraphBuilder (usado por POI)
-keep class com.graphbuilder.** { *; }
-dontwarn com.graphbuilder.**

# Mantener atributos necesarios para serialización
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Reglas específicas para Kotlin y R8
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# Reglas para evitar problemas de Kotlin metadata con R8
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Reglas para mantener clases de Kotlin que R8 puede tener problemas
-keep class kotlin.Metadata { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.reflect.** { *; }

# Reglas para evitar warnings de Kotlin metadata
-dontwarn kotlin.jvm.internal.**
-dontwarn kotlin.reflect.**

# Reglas específicas para manejar problemas de Kotlin metadata con R8
-ignorewarnings
-keep class * extends java.lang.Exception
-keep class * extends java.lang.Throwable

# Reglas para mantener clases de Kotlin que pueden causar problemas con R8
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }

# Reglas para evitar problemas con Kotlin metadata parsing
-dontwarn kotlin.coroutines.**
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**

# Evitar ofuscación de nombres de campos para Firebase
-keepnames class com.zipstats.app.model.** { *; }

# Mantener clases que implementan Parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Mantener clases que implementan Serializable
-keep class * implements java.io.Serializable { *; }

# Reglas específicas para evitar errores de deserialización
-keepclassmembers class com.zipstats.app.model.** {
    public <init>();
    public <init>(...);
}

# Mantener métodos getter y setter para Firebase
-keepclassmembers class com.zipstats.app.model.** {
    public void set*(***);
    public *** get*();
    public boolean is*();
}

# Reglas para mantener clases anónimas y clases internas
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Mantener clases que usan @DocumentId
-keepclassmembers class com.zipstats.app.model.** {
    @com.google.firebase.firestore.DocumentId <fields>;
}

# ========================================
# REGLAS ESPECÍFICAS PARA APACHE POI
# ========================================

# Mantener todas las clases de Apache POI
-keep class org.apache.poi.** { *; }

# Ignorar warnings de clases faltantes de Microsoft Office (no necesarias para funcionalidad básica)
-dontwarn com.microsoft.schemas.**
-dontwarn org.apache.poi.schemas.**

# Mantener clases específicas de POI que se usan
-keep class org.apache.poi.ss.usermodel.** { *; }
-keep class org.apache.poi.xssf.usermodel.** { *; }
-keep class org.apache.poi.hssf.usermodel.** { *; }

# Mantener métodos públicos de POI
-keepclassmembers class org.apache.poi.** {
    public *;
}

# Reglas para evitar problemas de reflexión en POI
-keepclassmembers class org.apache.poi.** {
    <init>();
    <init>(...);
}