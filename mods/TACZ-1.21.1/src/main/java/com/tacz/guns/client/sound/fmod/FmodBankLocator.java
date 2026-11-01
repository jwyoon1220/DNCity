package com.tacz.guns.client.sound.fmod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * Locates FMOD bank files bundled as resources of another mod (DNCity itself, which ships
 * Master.bank/weapons.bank under src/main/resources/fmod/) and copies them out to a real
 * filesystem path, since {@code FMODSystem.loadBankFile} calls into the native FMOD API,
 * which needs a real OS file -- not a (possibly inside-a-jar) NIO path, and not something
 * resolvable purely through classpath resource lookups across mod/module boundaries.
 */
final class FmodBankLocator {
    private FmodBankLocator() {
    }

    static Path extractBank(String modId, String resourcePath, Path tempDir) throws IOException {
        Optional<? extends ModContainer> container = ModList.get().getModContainerById(modId);
        if (container.isEmpty()) {
            throw new IOException("Mod not loaded, can't locate FMOD bank: " + modId);
        }
        IModInfo modInfo = container.get().getModInfo();
        IModFile file = modInfo.getOwningFile().getFile();
        if (file == null) {
            throw new IOException("Mod has no owning file, can't locate FMOD bank: " + modId);
        }

        Path source = file.findResource(resourcePath);
        if (!Files.exists(source)) {
            throw new IOException("FMOD bank resource not found: " + resourcePath + " in mod " + modId);
        }

        Path dest = tempDir.resolve(new File(resourcePath).getName());
        try (InputStream in = Files.newInputStream(source);
                OutputStream out = new FileOutputStream(dest.toFile())) {
            IOUtils.copy(in, out);
        }
        return dest;
    }
}
