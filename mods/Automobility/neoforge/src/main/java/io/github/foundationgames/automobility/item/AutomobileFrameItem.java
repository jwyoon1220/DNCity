package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileFrame;

public class AutomobileFrameItem extends AutomobileComponentItem.Dynamic<AutomobileFrame> {
    public AutomobileFrameItem(Properties settings) {
        super(settings, "frame", AutomobileFrame.REGISTRY, AutomobilityItems.COMPONENT_FRAME, AutomobileFrame.EMPTY);
    }
}
