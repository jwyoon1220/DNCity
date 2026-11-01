package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;

public class AutomobileEngineItem extends AutomobileComponentItem.Dynamic<AutomobileEngine> {
    public AutomobileEngineItem(Properties settings) {
        super(settings, "engine", AutomobileEngine.REGISTRY, AutomobilityItems.COMPONENT_ENGINE, AutomobileEngine.EMPTY);
    }
}
