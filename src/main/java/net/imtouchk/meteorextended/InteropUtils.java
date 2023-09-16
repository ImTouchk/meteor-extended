package net.imtouchk.meteorextended;

import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class InteropUtils {
    static public Module getMeteorModule(String name) {
        return Modules.get().get(name);
    }

    static public <T extends Module> T getMeteorModule(Class<T> klass) {
        return Modules.get().get(klass);
    }
}
