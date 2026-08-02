package com.gridea.android.deploy

import com.gridea.android.data.model.Setting
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Vector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SFTP 部署器
 *
 * 对应旧版 Gridea 0.9.3 的 src/server/plugins/deploys/sftp.ts
 * 使用 JSch（mwiede fork）实现 SFTP 文件上传
 *
 * 部署策略：全量替换（与旧版一致）
 * 1. 连接 SFTP 服务器
 * 2. 删除远程目录下所有内容
 * 3. 递归上传本地构建目录所有文件
 */
@Singleton
class SftpDeployer @Inject constructor() : Deployer {

    override suspend fun detect(setting: Setting): DetectResult = withContext(Dispatchers.IO) {
        try {
            connect(setting).use { channel ->
                channel.ls("/")
            }
            DetectResult(success = true, message = "SFTP 连接成功：${setting.server}:${setting.port}")
        } catch (e: Exception) {
            DetectResult(success = false, message = "SFTP 连接失败：${e.message ?: "未知错误"}")
        }
    }

    override suspend fun publish(
        setting: Setting,
        buildDir: File,
        onProgress: (DeployProgress) -> Unit
    ): DeployResult = withContext(Dispatchers.IO) {
        try {
            val allFiles = collectFiles(buildDir)
            val remotePath = setting.remotePath.ifEmpty { "/" }

            connect(setting).use { channel ->
                // 1. 清空远程目录（全量替换）
                cleanRemoteDir(channel, remotePath)

                // 2. 确保远程目录存在
                ensureRemoteDir(channel, remotePath)

                // 3. 递归上传所有文件
                allFiles.forEachIndexed { index, file ->
                    val relativePath = file.relativeTo(buildDir).path.replace("\\", "/")
                    val remoteFilePath = "$remotePath/$relativePath"
                    val remoteDir = remoteFilePath.substringBeforeLast("/")

                    ensureRemoteDir(channel, remoteDir)

                    FileInputStream(file).use { fis ->
                        channel.put(fis, remoteFilePath, ChannelSftp.OVERWRITE)
                    }

                    onProgress(DeployProgress(
                        current = index + 1,
                        total = allFiles.size,
                        fileName = relativePath
                    ))
                }
            }

            DeployResult(
                success = true,
                message = "SFTP 部署成功",
                fileCount = allFiles.size
            )
        } catch (e: Exception) {
            DeployResult(
                success = false,
                message = "SFTP 部署失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 连接 SFTP 服务器并返回 ChannelSftp
     */
    private fun connect(setting: Setting): ChannelSftp {
        val jsch = JSch()

        if (setting.privateKey.isNotEmpty()) {
            if (setting.password.isNotEmpty()) {
                jsch.addIdentity(setting.privateKey, setting.password)
            } else {
                jsch.addIdentity(setting.privateKey)
            }
        }

        val session = jsch.getSession(
            setting.sftpUsername,
            setting.server,
            setting.port.toIntOrNull() ?: 22
        )

        if (setting.privateKey.isEmpty() && setting.password.isNotEmpty()) {
            session.setPassword(setting.password)
        }

        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "publickey,password")
        session.connect(30000)

        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(30000)

        return channel
    }

    private fun collectFiles(dir: File): List<File> {
        val files = mutableListOf<File>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                files.add(file)
            }
        }
        return files
    }

    private fun cleanRemoteDir(channel: ChannelSftp, remotePath: String) {
        try {
            val items = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
            for (item in items) {
                val name = item.filename
                if (name == "." || name == "..") continue

                val itemPath = "$remotePath/$name"
                try {
                    if (item.attrs.isDir) {
                        cleanRemoteDir(channel, itemPath)
                        channel.rmdir(itemPath)
                    } else {
                        channel.rm(itemPath)
                    }
                } catch (e: SftpException) {
                    // 忽略单个文件删除失败
                }
            }
        } catch (e: SftpException) {
            // 目录不存在，无需清理
        }
    }

    private fun ensureRemoteDir(channel: ChannelSftp, path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current = "$current/$part"
            try {
                channel.stat(current)
            } catch (e: SftpException) {
                try {
                    channel.mkdir(current)
                } catch (e2: SftpException) {
                    // 可能已被创建，忽略
                }
            }
        }
    }
}

/**
 * ChannelSftp 的 use 扩展（自动断开连接和 session）
 */
private inline fun <T> ChannelSftp.use(block: (ChannelSftp) -> T): T {
    try {
        return block(this)
    } finally {
        disconnect()
        session?.disconnect()
    }
}
