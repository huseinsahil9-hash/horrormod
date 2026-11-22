package id.horrormod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class HorrorModClient implements ClientModInitializer {

    public static final EntityModelLayer STALKER_LAYER =
            new EntityModelLayer(new Identifier("horrormod", "stalker"), "main");

    private static int scareTicks = 0;
    private static boolean scareReady = false;
    private static final Identifier SCARE_TEX = new Identifier("horrormod", "stalker_scare");

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(STALKER_LAYER, StalkerModel::getTexturedModelData);
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

    private static void fill(NativeImage img, int x0, int y0, int x1, int y1, int color) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) img.setColor(x, y, color);
        }
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
        fill(img, 0, 0, 32, 32, abgr(0, 0, 0));
        ellipse(img, 15.5, 16, 13, 15.5, abgr(205, 205, 205));
        ellipse(img, 10.5, 12.5, 3.5, 4.5, abgr(0, 0, 0));
        ellipse(img, 21.5, 12.5, 3.5, 4.5, abgr(0, 0, 0));
        ellipse(img, 16, 23.5, 6, 5, abgr(0, 0, 0));
        ellipse(img, 16, 25, 3, 2, abgr(150, 0, 0));
        img.setColor(10, 13, abgr(255, 0, 0));
        img.setColor(11, 13, abgr(255, 0, 0));
        img.setColor(21, 13, abgr(255, 0, 0));
        img.setColor(22, 13, abgr(255, 0, 0));

        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(SCARE_TEX, new NativeImageBackedTexture(img));
    }

    // Model buatan sendiri: tinggi, kurus, tangan panjang, telapak besar bercakar
    public static class StalkerModel extends EntityModel<StalkerEntity> {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart body;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        public StalkerModel(ModelPart root) {
            this.root = root;
            this.head = root.getChild("head");
            this.body = root.getChild("body");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        public static TexturedModelData getTexturedModelData() {
            ModelData data = new ModelData();
            ModelPartData r = data.getRoot();

            r.addChild("head",
                    ModelPartBuilder.create().uv(0, 0).cuboid(-4f, -9f, -4f, 8f, 9f, 8f),
                    ModelTransform.pivot(0f, -5f, 0f));
            r.addChild("body",
                    ModelPartBuilder.create().uv(0, 20).cuboid(-3f, 0f, -1.5f, 6f, 12f, 3f),
                    ModelTransform.pivot(0f, -5f, 0f));

            r.addChild("right_arm",
                    ModelPartBuilder.create()
                            .uv(32, 0).cuboid(-1.5f, 0f, -1.5f, 3f, 17f, 3f)
                            .uv(44, 26).cuboid(-2f, 17f, -2f, 4f, 4f, 4f)
                            .uv(44, 36)
                            .cuboid(-1.5f, 21f, -0.4f, 0.8f, 6f, 0.8f)
                            .cuboid(-0.4f, 21f, -0.4f, 0.8f, 6f, 0.8f)
                            .cuboid(0.7f, 21f, -0.4f, 0.8f, 6f, 0.8f),
                    ModelTransform.pivot(-4.5f, -4f, 0f));
            r.addChild("left_arm",
                    ModelPartBuilder.create().mirrored(true)
                            .uv(32, 0).cuboid(-1.5f, 0f, -1.5f, 3f, 17f, 3f)
                            .uv(44, 26).cuboid(-2f, 17f, -2f, 4f, 4f, 4f)
                            .uv(44, 36)
                            .cuboid(-1.5f, 21f, -0.4f, 0.8f, 6f, 0.8f)
                            .cuboid(-0.4f, 21f, -0.4f, 0.8f, 6f, 0.8f)
                            .cuboid(0.7f, 21f, -0.4f, 0.8f, 6f, 0.8f),
                    ModelTransform.pivot(4.5f, -4f, 0f));

            r.addChild("right_leg",
                    ModelPartBuilder.create().uv(0, 36).cuboid(-1.5f, 0f, -1.5f, 3f, 17f, 3f),
                    ModelTransform.pivot(-1.7f, 7f, 0f));
            r.addChild("left_leg",
                    ModelPartBuilder.create().mirrored(true).uv(0, 36).cuboid(-1.5f, 0f, -1.5f, 3f, 17f, 3f),
                    ModelTransform.pivot(1.7f, 7f, 0f));

            return TexturedModelData.of(data, 64, 64);
        }

        private static void zero(ModelPart p) {
            p.pitch = 0f;
            p.yaw = 0f;
            p.roll = 0f;
        }

        @Override
        public void setAngles(StalkerEntity e, float limbAngle, float limbDistance,
                              float animationProgress, float headYaw, float headPitch) {
            int s = e.getState();
            boolean headless = e.isUpright();
            boolean revealed = s != StalkerEntity.STALK;

            // Reset pose dasar
            this.head.setPivot(0f, -5f, 0f);
            this.body.setPivot(0f, -5f, 0f);
            this.rightArm.setPivot(-4.5f, -4f, 0f);
            this.leftArm.setPivot(4.5f, -4f, 0f);
            this.rightLeg.setPivot(-1.7f, 7f, 0f);
            this.leftLeg.setPivot(1.7f, 7f, 0f);
            zero(this.head);
            zero(this.body);
            zero(this.rightArm);
            zero(this.leftArm);
            zero(this.rightLeg);
            zero(this.leftLeg);

            // Versi tanpa kepala
            this.head.visible = !headless;

            this.head.yaw = headYaw * 0.017453292f;
            this.head.pitch = headPitch * 0.017453292f;

            float armOut = 0.06f;
            float armSwing = 1.0f;
            if (revealed) {
                // Bentuk mengamuk: bahu lebar, tangan terbuka, kepala menunduk
                this.rightArm.setPivot(-5.5f, -4f, 0f);
                this.leftArm.setPivot(5.5f, -4f, 0f);
                this.head.pitch += 0.25f;
                armOut = 0.3f;
                armSwing = 1.4f;
            }

            this.rightArm.pitch = MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * limbDistance * armSwing;
            this.leftArm.pitch = MathHelper.cos(limbAngle * 0.6662f) * limbDistance * armSwing;
            this.rightArm.roll = armOut + MathHelper.sin(animationProgress * 0.067f) * 0.04f;
            this.leftArm.roll = -armOut - MathHelper.sin(animationProgress * 0.067f) * 0.04f;

            this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.6662f) * 1.4f * limbDistance;
            this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * 1.4f * limbDistance;

            if (s == StalkerEntity.TILT) {
                if (headless) {
                    this.body.roll = -0.25f;
                } else {
                    // Kepala miring (ganti tanda minus jadi plus kalau arahnya terbalik)
                    this.head.roll = -0.8f + MathHelper.sin(animationProgress * 1.5f) * 0.05f;
                }
            } else if (s == StalkerEntity.CHASE && !headless) {
                this.head.roll = MathHelper.sin(animationProgress * 0.9f) * 0.15f;
            }
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                           float red, float green, float blue, float alpha) {
            this.root.render(matrices, vertices, light, overlay, red, green, blue, alpha);
        }
    }

    public static class StalkerRenderer extends MobEntityRenderer<StalkerEntity, StalkerModel> {

        private static final Identifier TEX_CALM = new Identifier("horrormod", "stalker_calm");
        private static final Identifier TEX_ANGRY = new Identifier("horrormod", "stalker_angry");
        private static boolean ready = false;

        public StalkerRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, new StalkerModel(ctx.getPart(STALKER_LAYER)), 0.5f);
        }

        // Tekstur 64x64 digambar lewat kode. angry = wajah menganga bergigi.
        private static void makeTexture(Identifier id, boolean angry) {
            NativeImage img = new NativeImage(64, 64, true);
            fill(img, 0, 0, 64, 64, 0);

            int skin = angry ? abgr(190, 95, 45) : abgr(155, 100, 85);
            int skinDark = angry ? abgr(140, 60, 30) : abgr(120, 70, 60);
            int blood = abgr(120, 15, 15);
            int black = abgr(0, 0, 0);
            int white = abgr(235, 235, 235);

            // Kepala (0,0)-(32,17), wajah depan di x 8..15, y 8..16
            fill(img, 0, 0, 32, 17, skin);
            fill(img, 0, 0, 8, 8, skinDark);
            if (angry) {
                fill(img, 9, 9, 11, 13, black);    // mata kiri
                fill(img, 13, 9, 15, 13, black);   // mata kanan
                img.setColor(10, 10, white);
                img.setColor(10, 11, white);
                img.setColor(13, 10, white);
                img.setColor(13, 11, white);
                fill(img, 8, 12, 16, 17, black);   // mulut menganga lebar
                img.setColor(9, 12, white);
                img.setColor(11, 12, white);
                img.setColor(13, 12, white);
                img.setColor(10, 16, white);
                img.setColor(12, 16, white);
                img.setColor(14, 16, white);
            } else {
                fill(img, 9, 10, 11, 13, black);
                fill(img, 13, 10, 15, 13, black);
                fill(img, 9, 14, 15, 17, black);
                img.setColor(9, 14, white);
                img.setColor(11, 14, white);
                img.setColor(13, 14, white);
                img.setColor(10, 16, white);
                img.setColor(12, 16, white);
                img.setColor(14, 16, white);
                img.setColor(9, 13, blood);
                img.setColor(14, 13, blood);
            }

            // Badan (0,20)-(18,35): baju teal, celah hitam di pinggang
            fill(img, 0, 20, 18, 35, abgr(40, 165, 175));
            fill(img, 0, 33, 18, 35, abgr(15, 15, 15));

            // Tangan (32,0)-(44,20): lengan baju teal lalu kulit
            fill(img, 32, 0, 44, 9, abgr(40, 165, 175));
            fill(img, 32, 9, 44, 20, skin);
            fill(img, 32, 14, 44, 20, skinDark);
            img.setColor(34, 13, blood);
            img.setColor(37, 16, blood);
            img.setColor(40, 18, blood);

            // Telapak (44,26)-(60,34) dan cakar (44,36)-(52,46): hitam
            fill(img, 44, 26, 60, 34, black);
            fill(img, 44, 36, 52, 46, black);

            // Kaki (0,36)-(12,56): celana biru, sepatu abu-abu
            fill(img, 0, 36, 12, 56, abgr(45, 45, 190));
            fill(img, 0, 52, 12, 56, abgr(140, 140, 145));

            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(id, new NativeImageBackedTexture(img));
        }

        @Override
        public Identifier getTexture(StalkerEntity entity) {
            if (!ready) {
                makeTexture(TEX_CALM, false);
                makeTexture(TEX_ANGRY, true);
                ready = true;
            }
            return entity.getState() == StalkerEntity.STALK ? TEX_CALM : TEX_ANGRY;
        }

        @Override
        protected void scale(StalkerEntity entity, MatrixStack matrices, float amount) {
            float s = entity.getState() == StalkerEntity.STALK ? 1.0f : 1.2f;
            matrices.scale(s, s, s);
        }
    }
                 }
