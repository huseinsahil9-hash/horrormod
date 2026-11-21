package id.horrormod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class HorrorModClient implements ClientModInitializer {

    private static int scareTicks = 0;
    private static boolean scareReady = false;
    private static final Identifier SCARE_TEX = new Identifier("horrormod", "stalker_scare");

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(HorrorMod.STALKER, StalkerRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(HorrorMod.JUMPSCARE_PACKET,
                (client, handler, buf, sender) -> client.execute(() -> scareTicks = 45));

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (scareTicks > 0) scareTicks--;
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (scareTicks <= 0) return;
            if (!scareReady) {
                makeScareTexture();
                scareReady = true;
            }
            int w = ctx.getScaledWindowWidth();
            int h = ctx.getScaledWindowHeight();
            float alpha = Math.min(1f, scareTicks / 15f);
            int sx = (int) (Math.sin(scareTicks * 5.0) * 14);
            int sy = (int) (Math.cos(scareTicks * 7.0) * 14);

            RenderSystem.enableBlend();
            ctx.setShaderColor(1f, 1f, 1f, alpha);
            ctx.drawTexture(SCARE_TEX, -20 + sx, -20 + sy, w + 40, h + 40, 0, 0, 32, 32, 32, 32);
            ctx.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        });
    }

    private static int abgr(int r, int g, int b) {
        return (255 << 24) | (b << 16) | (g << 8) | r;
    }

    private static void ellipse(NativeImage img, double cx, double cy, double rx, double ry, int color) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                double ex = (x - cx) / rx;
                double ey = (y - cy) / ry;
                if (ex * ex + ey * ey <= 1.0) img.setColor(x, y, color);
            }
        }
    }

    // Wajah jumpscare 32x32, digambar lewat kode
    private static void makeScareTexture() {
        NativeImage img = new NativeImage(32, 32, true);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) img.setColor(x, y, abgr(0, 0, 0));
        }
        ellipse(img, 15.5, 16, 13, 15.5, abgr(205, 205, 205));       // wajah pucat
        ellipse(img, 10.5, 12.5, 3.5, 4.5, abgr(0, 0, 0));           // mata kiri
        ellipse(img, 21.5, 12.5, 3.5, 4.5, abgr(0, 0, 0));           // mata kanan
        ellipse(img, 16, 23.5, 6, 5, abgr(0, 0, 0));                 // mulut menganga
        ellipse(img, 16, 25, 3, 2, abgr(150, 0, 0));                 // bagian dalam mulut
        img.setColor(10, 13, abgr(255, 0, 0));
        img.setColor(11, 13, abgr(255, 0, 0));
        img.setColor(21, 13, abgr(255, 0, 0));
        img.setColor(22, 13, abgr(255, 0, 0));

        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(SCARE_TEX, new NativeImageBackedTexture(img));
    }

    // Model dengan pose merangkak dan kepala miring
    public static class StalkerModel extends BipedEntityModel<StalkerEntity> {
        public StalkerModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setAngles(StalkerEntity e, float limbAngle, float limbDistance,
                              float animationProgress, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            int s = e.getState();

            if (s == StalkerEntity.STALK) {
                this.body.pivotZ = 0f;
                this.head.pivotZ = 0f;
                this.rightArm.pivotZ = 0f;
                this.leftArm.pivotZ = 0f;
            } else {
                float swing = MathHelper.sin(limbAngle * 0.6662f) * 0.5f * Math.min(1f, limbDistance * 2f);

                this.body.pivotY = 18f;
                this.body.pivotZ = 0f;
                this.body.pitch = 1.45f;

                this.head.pivotY = 17f;
                this.head.pivotZ = -2f;
                this.head.pitch = -0.2f;
                this.head.roll = 0f;
                if (s == StalkerEntity.TILT) {
                    // Kepala miring (ganti tanda minus jadi plus kalau arahnya terbalik)
                    this.head.roll = -0.8f + MathHelper.sin(animationProgress * 1.5f) * 0.05f;
                }

                this.rightArm.pivotY = 18f;
                this.leftArm.pivotY = 18f;
                this.rightArm.pivotZ = 0f;
                this.leftArm.pivotZ = 0f;
                this.rightArm.pitch = -1.5f + swing;
                this.leftArm.pitch = -1.5f - swing;

                this.rightLeg.pivotY = 18f;
                this.leftLeg.pivotY = 18f;
                this.rightLeg.pivotZ = 12f;
                this.leftLeg.pivotZ = 12f;
                this.rightLeg.pitch = 1.45f - swing * 0.5f;
                this.leftLeg.pitch = 1.45f + swing * 0.5f;
            }
            this.hat.copyTransform(this.head);
        }
    }

    public static class StalkerRenderer
            extends BipedEntityRenderer<StalkerEntity, StalkerModel> {

        private static final Identifier TEXTURE = new Identifier("horrormod", "stalker_dynamic");
        private static boolean ready = false;

        public StalkerRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, new StalkerModel(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.5f);
        }

        private static void makeTexture() {
            NativeImage img = new NativeImage(64, 64, true);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int c;
                    if (y < 16 && x >= 32) c = 0;
                    else if (y < 16) c = abgr(200, 200, 200);
                    else c = abgr(12, 12, 12);
                    img.setColor(x, y, c);
                }
            }
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
