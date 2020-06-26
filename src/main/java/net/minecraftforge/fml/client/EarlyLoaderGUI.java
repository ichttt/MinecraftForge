/*
 * Minecraft Forge
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryUtil;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.ByteBuffer;
import java.util.List;

public class EarlyLoaderGUI {
    private final MainWindow window;
    private boolean handledElsewhere;

    public EarlyLoaderGUI(final MainWindow window) {
        this.window = window;
    }

    private void setupMatrix() {
        RenderSystem.clear(256, Minecraft.IS_RUNNING_ON_MAC);
        RenderSystem.matrixMode(5889);
        RenderSystem.loadIdentity();
        RenderSystem.ortho(0.0D, window.getFramebufferWidth() / window.getGuiScaleFactor(), window.getFramebufferHeight() / window.getGuiScaleFactor(), 0.0D, 1000.0D, 3000.0D);
        RenderSystem.matrixMode(5888);
        RenderSystem.loadIdentity();
        RenderSystem.translatef(0.0F, 0.0F, -2000.0F);
    }

    public void handleElsewhere() {
        this.handledElsewhere = true;
    }

    void renderFromGUI() {
        renderMessages();
    }

    void renderTick() {
        if (handledElsewhere) return;
        int guiScale = window.calcGuiScale(0, false);
        window.setGuiScale(guiScale);

        RenderSystem.clearColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);
        RenderSystem.pushMatrix();
        setupMatrix();
        renderBackground();
        renderMessages();
        window.flipFrame();
        RenderSystem.popMatrix();
    }

    private void renderBackground() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(239F / 255F, 50F / 255F, 61F / 255F, 255F / 255F); //Color from ResourceLoadProgressGui
        GL11.glVertex3f(0, 0, -10);
        GL11.glVertex3f(0, window.getScaledHeight(), -10);
        GL11.glVertex3f(window.getScaledWidth(), window.getScaledHeight(), -10);
        GL11.glVertex3f(window.getScaledWidth(), 0, -10);
        GL11.glEnd();
    }

    private void renderMessages() {
        List<Pair<Integer, StartupMessageManager.Message>> messages = StartupMessageManager.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            boolean nofade = i == 0;
            final Pair<Integer, StartupMessageManager.Message> pair = messages.get(i);
            final float fade = MathHelper.clamp((4000.0f - (float) pair.getLeft() - ( i - 4 ) * 1000.0f) / 5000.0f, 0.0f, 1.0f);
            if (fade <0.01f && !nofade) continue;
            StartupMessageManager.Message msg = pair.getRight();
            renderMessage(msg.getText(), msg.getTypeColour(), ((window.getScaledHeight() - 15) / 10) - i + 1, nofade ? 1.0f : fade);
        }
        renderMemoryInfo();
    }

    private static final float[] memorycolour = new float[] { 0.0f, 0.0f, 0.0f};

    private void renderMemoryInfo() {
        final MemoryUsage heapusage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        final MemoryUsage offheapusage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        final float pctmemory = (float) heapusage.getUsed() / heapusage.getMax();
        String memory = String.format("Memory Heap: %d / %d MB (%.1f%%)  OffHeap: %d MB", heapusage.getUsed() >> 20, heapusage.getMax() >> 20, pctmemory * 100.0, offheapusage.getUsed() >> 20);

        final int i = MathHelper.hsvToRGB((1.0f - (float)Math.pow(pctmemory, 1.5f)) / 3f, 1.0f, 0.5f);
        memorycolour[2] = ((i) & 0xFF) / 255.0f;
        memorycolour[1] = ((i >> 8 ) & 0xFF) / 255.0f;
        memorycolour[0] = ((i >> 16 ) & 0xFF) / 255.0f;
        renderMessage(memory, memorycolour, 1, 1.0f);
    }

    void renderMessage(final String message, final float[] colour, int line, float alpha) {
        ByteBuffer vertexBuf = MemoryUtil.memAlloc(message.length() * 270);
        int quads = STBEasyFont.stb_easy_font_print(0, 0, message, null, vertexBuf);

        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        GL14.glBlendColor(0,0,0, alpha);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.CONSTANT_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
        RenderSystem.pushMatrix();
        RenderSystem.scalef(1, 1, 0);

        //The quads need to be flipped in order for some reason
        //So quad 0 -> quad 3, quad 1 -> quad 2, quad 2 -> quad 1 and quad 3 -> quad 0
        BufferBuilder bufferbuilder = Tessellator.getInstance().getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float[] quadPosX = new float[4];
        float[] quadPosY = new float[4];
        float[] quadPosZ = new float[4];
        int lineOffset = line * 10;
        for (int quad = 0; quad < quads; quad++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                quadPosX[vertex] = vertexBuf.getFloat();
                quadPosY[vertex] = vertexBuf.getFloat();
                quadPosZ[vertex] = vertexBuf.getFloat();
                vertexBuf.getInt(); //color, but it is ignored, as we use our own color
            }
            for (int i = 3; i >= 0; i--)
                bufferbuilder.pos(quadPosX[i] + 10, quadPosY[i] + lineOffset, quadPosZ[i]).color(colour[0], colour[1], colour[2], 1.0F).endVertex();
        }
        bufferbuilder.finishDrawing();
        WorldVertexBufferUploader.draw(bufferbuilder);


        RenderSystem.popMatrix();

        MemoryUtil.memFree(vertexBuf);
    }
}
