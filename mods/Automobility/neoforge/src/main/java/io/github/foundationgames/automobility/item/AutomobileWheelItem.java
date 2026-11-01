package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileWheel;

public class AutomobileWheelItem extends AutomobileComponentItem.Dynamic<AutomobileWheel> {
    public AutomobileWheelItem(Properties settings) {
        super(settings, "wheel", AutomobileWheel.REGISTRY, AutomobilityItems.COMPONENT_WHEEL, AutomobileWheel.EMPTY);
    }
}
