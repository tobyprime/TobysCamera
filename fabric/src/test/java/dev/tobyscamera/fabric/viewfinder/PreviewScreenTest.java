package dev.tobyscamera.fabric.viewfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

final class PreviewScreenTest {
    @Test
    void previewBlitUsesTheFullSourceTextureAtTheRequestedSize() {
        PreviewScreen.TextureBlit blit = PreviewScreen.textureBlit(320, 180, 512, 512, 400, 400);

        assertEquals(320, blit.left());
        assertEquals(180, blit.top());
        assertEquals(400, blit.width());
        assertEquals(400, blit.height());
        assertEquals(512, blit.sourceWidth());
        assertEquals(512, blit.sourceHeight());
        assertEquals(512, blit.textureWidth());
        assertEquals(512, blit.textureHeight());
    }

    @Test
    void previewBlitFitsLandscapeSourceWithoutCropping() {
        PreviewScreen.TextureBlit blit = PreviewScreen.textureBlit(20, 30, 512, 256, 400, 400);

        assertEquals(20, blit.left());
        assertEquals(130, blit.top());
        assertEquals(400, blit.width());
        assertEquals(200, blit.height());
        assertEquals(512, blit.sourceWidth());
        assertEquals(256, blit.sourceHeight());
    }

    @Test
    void previewBlitFitsPortraitSourceWithoutCropping() {
        PreviewScreen.TextureBlit blit = PreviewScreen.textureBlit(20, 30, 256, 512, 400, 300);

        assertEquals(145, blit.left());
        assertEquals(30, blit.top());
        assertEquals(150, blit.width());
        assertEquals(300, blit.height());
        assertEquals(256, blit.sourceWidth());
        assertEquals(512, blit.sourceHeight());
    }

    @Test
    void printSizeControlShowsResolutionInsteadOfPrint() {
        Component label = PreviewScreen.resolutionValueLabel(3);
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, label.getContents());

        assertEquals("tobyscamera.preview.resolution", contents.getKey());
        assertEquals(3, contents.getArgs()[0]);
    }
}
