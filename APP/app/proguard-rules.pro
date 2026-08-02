# ============================================================================
# Gridea Android ProGuard / R8 混淆规则
# ============================================================================

# ----- 通用基础 -----
# 保留泛型签名（序列化、反射需要）
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
# 保留源文件名与行号，便于崩溃堆栈定位
-keepattributes SourceFile, LineNumberTable

# ============================================================================
# Hilt / Dagger 依赖注入
# ============================================================================
# 保留 Hilt 生成的组件与入口类
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelFactoriesEntryPoint { *; }
# 保留 @HiltViewModel 标注的 ViewModel 及其构造函数（Hilt 通过工厂实例化）
-keep,allowobfuscation,allowshrinking class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
# 保留 @Inject 注解的构造函数与字段（Dagger 依赖这些完成注入）
-keep,allowobfuscation,allowshrinking class * { @javax.inject.Inject <init>(...); }
-keep,allowobfuscation,allowshrinking class * { @javax.inject.Inject <fields>; }

# ============================================================================
# Room 数据库
# ============================================================================
# 保留 RoomDatabase 子类
-keep class * extends androidx.room.RoomDatabase { <init>(); }
# 保留实体类及其字段名（SQL 列名依赖字段名，重命名会导致查询失败）
-keep @androidx.room.Entity class * { *; }
# 保留 DAO 接口方法签名（生成的 _Impl 类引用这些方法）
-keep @androidx.room.Dao interface * { *; }
# 保留 TypeConverter 方法
-keep class * { @androidx.room.TypeConverter <methods>; }

# ============================================================================
# kotlinx.serialization
# ============================================================================
# 保留 @Serializable 类的伴生对象 serializer() 方法
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留生成的 $$serializer 类（编译器生成，反射访问）
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class **$$serializer { *; }
# 保留 @Serializable 标注的数据模型类（JSON 字段名依赖属性名）
-keep @kotlinx.serialization.Serializable class * { *; }

# ============================================================================
# Jetpack Compose
# ============================================================================
# Compose 库自带 consumer ProGuard 规则，此处仅做补充保护
-dontwarn androidx.compose.**
# 保留 @Stable / @Immutable 标注的类（Compose 编译器稳定性推断需要）
-keep,allowshrinking,allowobfuscation @androidx.compose.runtime.Stable class *
-keep,allowshrinking,allowobfuscation @androidx.compose.runtime.Immutable class *

# ============================================================================
# Pebble 模板引擎
# ============================================================================
# Pebble 通过反射/Bean Introspection 访问模板变量的属性名，
# R8 混淆会重命名字段导致属性访问失败，必须保留渲染数据模型类的字段名。
# 保留 renderer 包下所有 RenderData/Pagination/PostStats 数据类的字段
-keep class com.gridea.android.renderer.**RenderData { *; }
-keep class com.gridea.android.renderer.Pagination { *; }
-keep class com.gridea.android.renderer.PostStats { *; }
# 主题资源声明（模板遍历 asset.src / asset.defer_ 等属性）
-keep class com.gridea.android.data.model.ThemeAsset { *; }
# 友链模型（friends.peb 通过反射访问 name/url/description/avatar 字段）
-keep class com.gridea.android.data.model.FriendLink { *; }
# 归档页按年分组数据（archives.peb 遍历 year/posts 字段）
-keep class com.gridea.android.renderer.ArchiveYearGroup { *; }
# Pebble 核心类与扩展点（Filter/Extension/Node 等通过反射加载）
-keep class io.pebbletemplates.pebble.** { *; }
-keep class com.mitchellbosecke.pebble.** { *; }
# 抑制 Pebble 对可选依赖（servlet-api/caffeine）的反射探测警告
-dontwarn io.pebbletemplates.pebble.cache.**
-dontwarn javax.servlet.**

# ============================================================================
# 其他第三方库（以下库自带 consumer rules，仅抑制无害警告）
# ============================================================================
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.**
-dontwarn org.conscrypt.**

# ============================================================================
# JSch（SFTP 部署）
# ============================================================================
# JSch 引用了多个平台可选依赖（Windows Pageant/JNA、Kerberos/JGSS、
# Unix Domain Socket、SLF4J/Log4j 日志），这些在 Android 上均不可用
# 也不会被调用，R8 混淆时声明 dontwarn 即可
-dontwarn com.jcraft.jsch.**
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**

# ============================================================================
# Markwon / commonmark
# ============================================================================
# Markwon 的 StrikeHandler 引用了 commonmark-ext-gfm-strikethrough 的类，
# 该扩展已从依赖中移除（改用正则后处理），运行时不会触发
-dontwarn org.commonmark.ext.gfm.strikethrough.**
-dontwarn io.noties.markwon.html.tag.StrikeHandler
