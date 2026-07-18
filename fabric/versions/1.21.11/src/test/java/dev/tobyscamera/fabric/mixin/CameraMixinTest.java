package dev.tobyscamera.fabric.mixin;

import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CameraMixinTest {
    private static Object originalMinecraftInstance;

    @BeforeAll
    static void initializeMinecraftSingleton() throws ReflectiveOperationException {
        var instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        originalMinecraftInstance = instance.get(null);
        instance.set(null, unsafe().allocateInstance(Minecraft.class));
    }

    @AfterAll
    static void restoreMinecraftSingleton() throws ReflectiveOperationException {
        var instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, originalMinecraftInstance);
    }

    @Test
    void preservesOriginalFovWhenViewfinderIsInactive() throws ReflectiveOperationException {
        CallbackInfoReturnable<Float> callback = new CallbackInfoReturnable<>("getFov", true, 70.0f);
        Method method = CameraMixin.class.getDeclaredMethod("tobyscamera$applyViewfinderZoom",
                Camera.class, float.class, boolean.class, CallbackInfoReturnable.class);
        method.setAccessible(true);

        method.invoke(new CameraMixin() { }, null, 0.0f, false, callback);

        assertFalse(callback.isCancelled());
        assertEquals(70.0f, callback.getReturnValueF());
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
