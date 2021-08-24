/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.javafx.tk.quantum;

import java.nio.IntBuffer;
import com.sun.glass.ui.Pixels;
import com.sun.prism.Graphics;
import com.sun.prism.GraphicsPipeline;
import com.sun.prism.RTTexture;
import com.sun.prism.Texture.WrapMode;
import com.sun.prism.impl.Disposer;
import com.sun.prism.impl.QueuedPixelSource;

/**
 * UploadingPainter is used when we need to render into an offscreen buffer.
 * The PresentingPainter is used when we are rendering to the main screen.
 */
final class UploadingPainter extends ViewPainter implements Runnable {

    private RTTexture   rttexture;
    // resolveRTT is a temporary render target to "resolve" a msaa render buffer
    // into a normal color render target.
    private RTTexture   resolveRTT = null;

    private QueuedPixelSource pixelSource = new QueuedPixelSource(true);
    private float penScale;

    UploadingPainter(GlassScene view) {
        super(view);
    }

    void disposeRTTexture() {
        if (rttexture != null) {
            rttexture.dispose();
            rttexture = null;
        }
        if (resolveRTT != null) {
            resolveRTT.dispose();
            resolveRTT = null;
        }
    }

    @Override
    public float getPixelScaleFactor() {
        return sceneState.getRenderScale();
    }

    @Override public void run() {
        renderLock.lock();

        boolean errored = false;
        try {
            if (!validateStageGraphics()) {
                if (QuantumToolkit.verbose) {
                    System.err.println("UploadingPainter: validateStageGraphics failed");
                }
                paintImpl(null);
                return;
            }

            if (factory == null) {
                factory = GraphicsPipeline.getDefaultResourceFactory();
            }
            if (factory == null || !factory.isDeviceReady()) {
                return;
            }

            float scale = getPixelScaleFactor();
            int bufWidth = sceneState.getRenderWidth();
            int bufHeight = sceneState.getRenderHeight();

            // Repaint everything on pen scale or view size change because
            // texture contents are no longer correct.
            // Repaint everything on new texture dimensions because otherwise
            // our upload logic below may fail with index out of bounds.
            boolean needsReset = (penScale != scale ||
                                  penWidth != viewWidth ||
                                  penHeight != viewHeight ||
                                  rttexture == null ||
                                  rttexture.getContentWidth() != bufWidth ||
                                  rttexture.getContentHeight() != bufHeight);

            if (!needsReset) {
                rttexture.lock();
                if (rttexture.isSurfaceLost()) {
                    rttexture.unlock();
                    sceneState.getScene().entireSceneNeedsRepaint();
                    needsReset = true;
                }
            }

            if (needsReset) {
                disposeRTTexture();
                rttexture = factory.createRTTexture(bufWidth, bufHeight, WrapMode.CLAMP_NOT_NEEDED,
                        sceneState.isMSAA());
                if (rttexture == null) {
                    return;
                }
                penScale    = scale;
                penWidth    = viewWidth;
                penHeight   = viewHeight;
                freshBackBuffer = true;
            }
            Graphics g = rttexture.createGraphics();
            if (g == null) {
                disposeRTTexture();
                sceneState.getScene().entireSceneNeedsRepaint();
                return;
            }
            g.scale(scale, scale);
            paintImpl(g);
            freshBackBuffer = false;

            int outWidth = sceneState.getOutputWidth();
            int outHeight = sceneState.getOutputHeight();
            float outScale = sceneState.getOutputScale();
            RTTexture rtt;
            if (rttexture.isMSAA() || outWidth != bufWidth || outHeight != bufHeight) {
                rtt = resolveRenderTarget(g, outWidth, outHeight);
            } else {
                rtt = rttexture;
            }

            Pixels pix = pixelSource.getUnusedPixels(outWidth, outHeight, outScale);
            IntBuffer bits = (IntBuffer) pix.getPixels();

            int rawbits[] = rtt.getPixels();

            if (rawbits != null) {
                bits.put(rawbits, 0, outWidth * outHeight);
            } else {
                if (!rtt.readPixels(bits)) {
                    /* device lost */
                    sceneState.getScene().entireSceneNeedsRepaint();
                    disposeRTTexture();
                    pix = null;
                }
            }

            if (rttexture != null) {
                rttexture.unlock();
            }

            if (pix != null) {
                /* transparent pixels created and ready for upload */
                // Copy references, which are volatile, used by upload. Thus
                // ensure they still exist once event queue is consumed.
                pixelSource.enqueuePixels(pix);
                sceneState.uploadPixels(pixelSource);
            }

        } catch (Throwable th) {
            errored = true;
            th.printStackTrace(System.err);
        } finally {
            if (rttexture != null && rttexture.isLocked()) {
                rttexture.unlock();
            }
            if (resolveRTT != null && resolveRTT.isLocked()) {
                resolveRTT.unlock();
            }

            Disposer.cleanUp();

            sceneState.getScene().setPainting(false);

            if (factory != null) {
                factory.getTextureResourcePool().freeDisposalRequestedAndCheckResources(errored);
            }

            renderLock.unlock();
        }
    }

    private RTTexture resolveRenderTarget(Graphics g, int width, int height) {
        if (resolveRTT != null) {
            resolveRTT.lock();
            if (resolveRTT.isSurfaceLost() ||
                resolveRTT.getContentWidth() != width ||
                resolveRTT.getContentHeight() != height)
            {
                resolveRTT.unlock();
                resolveRTT.dispose();
                resolveRTT = null;
            }
        }
        if (resolveRTT == null) {
            resolveRTT = g.getResourceFactory().createRTTexture(
                    width, height,
                    WrapMode.CLAMP_NOT_NEEDED, false);
        }
        int srcw = rttexture.getContentWidth();
        int srch = rttexture.getContentHeight();
        g.blit(rttexture, resolveRTT, 0, 0, srcw, srch, 0, 0, width, height);
        return resolveRTT;
    }
}
