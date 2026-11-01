package com.atsuishio.superbwarfare.client.sound.fmod

import net.neoforged.fml.ModList
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * DNCity(루트 프로젝트)가 번들한 FMOD 뱅크 리소스를 실제 파일시스템 경로로 추출한다.
 * `FMODSystem.loadBankFile`은 네이티브 FMOD API를 직접 호출하므로 jar 안의 NIO 경로가 아니라
 * 진짜 OS 파일이 필요하고, SBW와 DNCity는 서로 다른 모드 jar이므로 일반적인 클래스패스 리소스
 * 조회로는 건널 수 없다 — TACZ의 `FmodBankLocator`와 완전히 동일한 패턴(package-private이라
 * 직접 재사용은 불가능해 그대로 재구현).
 */
internal object SbwFmodBankLocator {

    @Throws(IOException::class)
    fun extractBank(modId: String, resourcePath: String, tempDir: Path): Path {
        val container = ModList.get().getModContainerById(modId)
            .orElseThrow { IOException("Mod not loaded, can't locate FMOD bank: $modId") }

        val file = container.modInfo.owningFile?.file
            ?: throw IOException("Mod has no owning file, can't locate FMOD bank: $modId")

        val source = file.findResource(resourcePath)
        if (!Files.exists(source)) {
            throw IOException("FMOD bank resource not found: $resourcePath in mod $modId")
        }

        val dest = tempDir.resolve(Path.of(resourcePath).fileName)
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
        return dest
    }
}
