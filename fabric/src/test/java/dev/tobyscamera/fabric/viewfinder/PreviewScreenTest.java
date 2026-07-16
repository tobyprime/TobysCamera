package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class PreviewScreenTest {
    @Test
    void previewBlitUsesTheFullSourceTextureAtTheRequestedSize() {
        PreviewScreen.TextureBlit blit = PreviewScreen.textureBlit(320, 180, 512, 512, 400);

        assertEquals(320, blit.left());
        assertEquals(180, blit.top());
        assertEquals(400, blit.width());
        assertEquals(400, blit.height());
        assertEquals(512, blit.textureWidth());
        assertEquals(512, blit.textureHeight());
    }
}
