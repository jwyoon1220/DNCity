package io.github.foundationgames.automobility.sound;

import java.util.function.IntConsumer;

public interface AdvancedSoundInstance {
    default IntConsumer setupALState() {
        return null;
    }

    default IntConsumer updateALState() {
        return null;
    }
}
