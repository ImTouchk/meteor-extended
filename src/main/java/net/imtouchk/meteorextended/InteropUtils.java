package net.imtouchk.meteorextended;

import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class InteropUtils {
    static public Module getMeteorModule(String name) {
        var modules = Systems.get(Modules.class);
        return modules.get(name);
    }

    static public <T extends Module> T getMeteorModule(Class<T> klass) {
        var modules = Systems.get(Modules.class);
        return modules.get(klass);
    }
}
