pluginManagement {
    repositories {
        // 阿里云 Gradle 插件镜像（优先）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 阿里云 Google 仓库镜像（AGP 等官方插件）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 阿里云公共仓库镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 阿里云 jcenter 镜像（部分旧依赖）
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云 Google 仓库镜像（androidx 等）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 阿里云公共仓库镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 阿里云 jcenter 镜像（部分旧依赖）
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
    }
}

rootProject.name = "GrideaAndroid"
include(":app")
