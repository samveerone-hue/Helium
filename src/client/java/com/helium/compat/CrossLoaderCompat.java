package com.helium.compat;

import com.helium.HeliumClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class CrossLoaderCompat {
    
    private static final List<Runnable> tickhandlers = new ArrayList<>();
    private static final List<KeyMapping> pendingkeybindings = new ArrayList<>();
    private static boolean fabrickeybindingavailable = false;
    private static boolean fabrictickavailable = false;
    private static boolean keybindingsregistered = false;
    
    static {
        try {
            Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
            fabrickeybindingavailable = true;
        } catch (ClassNotFoundException e) {
            fabrickeybindingavailable = false;
        }
        
        try {
            Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
            fabrictickavailable = true;
        } catch (ClassNotFoundException e) {
            fabrictickavailable = false;
        }
    }
    
    public static KeyMapping registerkeybinding(KeyMapping keybinding) {
        if (fabrickeybindingavailable) {
            return registerwithfabricapi(keybinding);
        }
        pendingkeybindings.add(keybinding);
        return keybinding;
    }
    
    private static KeyMapping registerwithfabricapi(KeyMapping keybinding) {
        try {
            Class<?> helper = Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
            Method register = helper.getMethod("registerKeyBinding", KeyMapping.class);
            return (KeyMapping) register.invoke(null, keybinding);
        } catch (Exception e) {
            HeliumClient.LOGGER.warn("fabric api keybinding failed, deferring: {}", e.getMessage());
            pendingkeybindings.add(keybinding);
            return keybinding;
        }
    }
    
    public static void registerpendingkeybindings() {
        if (keybindingsregistered || pendingkeybindings.isEmpty()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        
        try {
            com.helium.mixin.compat.GameOptionsAccessor accessor = 
                (com.helium.mixin.compat.GameOptionsAccessor) (Object) client.options;
            KeyMapping[] current = accessor.helium$getallkeys();
            
            for (KeyMapping keybinding : pendingkeybindings) {
                current = ArrayUtils.add(current, keybinding);
            }
            
            accessor.helium$setallkeys(current);
            keybindingsregistered = true;
            HeliumClient.LOGGER.info("registered {} keybindings via vanilla fallback", pendingkeybindings.size());
            pendingkeybindings.clear();
        } catch (Exception e) {
            HeliumClient.LOGGER.warn("deferred keybinding registration failed: {}", e.getMessage());
        }
    }
    
    public static void registertickevent(Runnable handler) {
        if (fabrictickavailable) {
            registertickwithfabricapi(handler);
        } else {
            tickhandlers.add(handler);
        }
    }
    
    private static void registertickwithfabricapi(Runnable handler) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
            Object endtick = events.getField("END_CLIENT_TICK").get(null);
            
            Class<?> listenerclass = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
            
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerclass.getClassLoader(),
                new Class<?>[] { listenerclass },
                (p, method, args) -> {
                    if ("onEndTick".equals(method.getName())) {
                        handler.run();
                    }
                    return null;
                }
            );
            
            Method register = null;
            for (Method m : endtick.getClass().getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                    register = m;
                    break;
                }
            }
            
            if (register == null) {
                throw new NoSuchMethodException("register method not found on " + endtick.getClass().getName());
            }
            
            register.setAccessible(true);
            register.invoke(endtick, proxy);
        } catch (Exception e) {
            HeliumClient.LOGGER.warn("fabric api tick event failed, using mixin fallback: {}", e.getMessage());
            fabrictickavailable = false;
            tickhandlers.add(handler);
        }
    }
    
    public static void tick() {
        registerpendingkeybindings();
        
        for (Runnable handler : tickhandlers) {
            try {
                handler.run();
            } catch (Exception e) {
                HeliumClient.LOGGER.debug("tick handler error: {}", e.getMessage());
            }
        }
    }
    
    public static boolean isfabrickeybindingavailable() {
        return fabrickeybindingavailable;
    }
    
    public static boolean isfabrictickavailable() {
        return fabrictickavailable;
    }
}
