# FOV Mod Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent TobysCamera from disabling third-party FOV zoom mods while its own viewfinder zoom is inactive.

**Architecture:** The Minecraft 1.21.11 `GameRenderer#getFov` return injection will leave its callback untouched when TobysCamera's viewfinder multiplier is `1.0f`. When the viewfinder is actively zoomed, it will retain the existing FOV division. A focused JUnit test invokes the injection with the closed default viewfinder and asserts that it neither cancels nor replaces the FOV callback result.

**Tech Stack:** Java 21, Fabric Loom, SpongePowered Mixin, JUnit 5.

---

### Task 1: Reproduce the callback-cancellation regression

**Files:**
- Create: `fabric/src/test/java/dev/tobyscamera/fabric/mixin/CameraMixinTest.java`
- Test: `fabric/src/test/java/dev/tobyscamera/fabric/mixin/CameraMixinTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.tobyscamera.fabric.mixin;

import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CameraMixinTest {
    @Test
    void leavesOtherFovCallbacksUncancelledWhenViewfinderZoomIsInactive() throws Exception {
        CameraMixin mixin = new CameraMixin() { };
        CallbackInfoReturnable<Float> callback = new CallbackInfoReturnable<>("getFov", true, 70.0f);
        Method method = CameraMixin.class.getDeclaredMethod(
                "tobyscamera$applyViewfinderZoom", Camera.class, float.class, boolean.class, CallbackInfoReturnable.class);
        method.setAccessible(true);

        method.invoke(mixin, null, 0.0f, true, callback);

        assertFalse(callback.isCancelled());
        assertEquals(70.0f, callback.getReturnValueF());
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew.bat :fabric-1.21.11:test --tests dev.tobyscamera.fabric.mixin.CameraMixinTest`

Expected: FAIL because the current injection calls `setReturnValue(70.0f / 1.0f)`, which cancels the callback.

### Task 2: Preserve the FOV callback when no TobysCamera zoom is active

**Files:**
- Modify: `fabric/src/main/java/dev/tobyscamera/fabric/mixin/CameraMixin.java:15-18`
- Test: `fabric/src/test/java/dev/tobyscamera/fabric/mixin/CameraMixinTest.java`

- [ ] **Step 1: Implement the minimal conditional change**

```java
@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
private void tobyscamera$applyViewfinderZoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> callback) {
    float zoom = TobysCameraClient.viewfinderZoom();
    if (zoom == 1.0f) return;
    callback.setReturnValue(callback.getReturnValueF() / zoom);
}
```

- [ ] **Step 2: Run the focused regression test to verify it passes**

Run: `./gradlew.bat :fabric-1.21.11:test --tests dev.tobyscamera.fabric.mixin.CameraMixinTest`

Expected: PASS; the default viewfinder leaves the callback result at `70.0f` and does not cancel it.

- [ ] **Step 3: Run the full Fabric 1.21.11 test suite**

Run: `./gradlew.bat :fabric-1.21.11:test`

Expected: PASS with no test failures.

- [ ] **Step 4: Inspect the final diff and commit**

Run: `git diff --check && git diff -- fabric/src/main/java/dev/tobyscamera/fabric/mixin/CameraMixin.java fabric/src/test/java/dev/tobyscamera/fabric/mixin/CameraMixinTest.java`

Then run:

```powershell
git add -- fabric/src/main/java/dev/tobyscamera/fabric/mixin/CameraMixin.java fabric/src/test/java/dev/tobyscamera/fabric/mixin/CameraMixinTest.java docs/superpowers/plans/2026-07-18-fov-mod-compatibility.md
git commit -m "fix: preserve FOV mod compatibility"
```
