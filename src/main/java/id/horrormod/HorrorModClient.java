package id.horrormod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.ZombieEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class HorrorModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(HorrorMod.STALKER, StalkerRenderer::new);
    }

    public static class StalkerRenderer
            extends BipedEntityRenderer<StalkerEntity, ZombieEntityModel<StalkerEntity>> {

        private static final Identifier TEXTURE = new Identifier("horrormod", "stalker_dynamic");
        private static boolean ready = false;

        public StalkerRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, new ZombieEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.5f);
        }

        private static int abgr(int r, int g, int b) {
            return (255 << 24) | (b << 16) | (g << 8) | r;
        }

        private static void makeTexture() {
            NativeImage img = new NativeImage(64, 64, true);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int c;
                    if (y < 16 && x >= 32) c = 0;                 // lapisan topi: transparan
                    else if (y < 16) c = abgr(200, 200, 200);     // kepala pucat
                    else c = abgr(12, 12, 12);                    // badan hitam
                    img.setColor(x, y, c);
                }
            }
            // Mata hitam kosong dengan titik merah, dan mulut
            int[] eyeX = {9, 10, 13, 14};
            for (int ex : eyeX) {
                for (int ey = 10; ey <= 12; ey++) img.setColor(ex, ey, abgr(0, 0, 0));
            }
            img.setColor(10, 11, abgr(255, 0, 0));
            img.setColor(13, 11, abgr(255, 0, 0));
            for (int mx = 10; mx <= 13; mx++) img.setColor(mx, 14, abgr(0, 0, 0));
            img.setColor(11, 15, abgr(0, 0, 0));
            img.setColor(12, 15, abgr(0, 0, 0));

            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(TEXTURE, new NativeImageBackedTexture(img));
        }

        @Override
        public Identifier getTexture(StalkerEntity entity) {
            if (!ready) {
                makeTexture();
                ready = true;
            }
            return TEXTURE;
        }

        @Override
        protected void scale(StalkerEntity entity, MatrixStack matrices, float amount) {
            matrices.scale(1.1f, 1.25f, 1.1f);
        }
    }
              }
