package dev.tobyscamera.folia.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

@SuppressWarnings("unchecked")
class CameraFilmServiceTest {
    @Test
    void loadingFilmWritesRemainingAndConfiguredMaximumComponents() {
        CameraFilmService films = new CameraFilmService("tobyscamera:camera", "tobyscamera:film", 4);
        ItemStack camera = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        Map<NamespacedKey, Integer> values = new HashMap<>();
        when(camera.getPersistentDataContainer()).thenReturn(data);
        when(camera.getItemMeta()).thenReturn(meta);
        when(data.getOrDefault(any(), eq(PersistentDataType.INTEGER), anyInt())).thenAnswer(call ->
                values.getOrDefault(call.getArgument(0), call.getArgument(2)));
        when(data.has(any())).thenAnswer(call -> values.containsKey(call.getArgument(0)));
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(2)); return null; })
                .when(data).set(any(), eq(PersistentDataType.INTEGER), anyInt());
        doAnswer(call -> { ((Consumer<PersistentDataContainer>) call.getArgument(0)).accept(data); return null; })
                .when(camera).editPersistentDataContainer(any());
        NamespacedKey maximumKey = new NamespacedKey("tobyscamera", "max_grid_size");
        values.put(maximumKey, 8);

        films.load(camera, 9);

        assertEquals(9, films.remaining(camera));
        assertEquals(4, films.maximum(camera, 4));
        assertEquals(4, values.get(maximumKey));
        assertEquals(3, films.maximumForFilm(camera, 4));
        assertTrue(films.consume(camera, 9));
        assertEquals(0, films.remaining(camera));
    }

    @Test
    void createsFilmLoreWithRemainingFilmAndMaximumGridSize() {
        var lore = CameraFilmService.lore(9, 4).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).toList();

        assertEquals(java.util.List.of("剩余胶卷: 9", "最大尺寸: 4x"), lore);
    }
}
