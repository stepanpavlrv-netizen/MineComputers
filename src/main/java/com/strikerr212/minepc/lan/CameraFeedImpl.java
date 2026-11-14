package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.computer.CameraFeed;

import java.util.Map;

/** Камера глазами компьютера: кадры по снимку мира, частота — по пропускной способности линии до неё. */
public final class CameraFeedImpl implements CameraFeed {

    private final CameraBlockEntity cam;
    private final long linkBytes;

    public CameraFeedImpl(CameraBlockEntity cam, long linkBytes) {
        this.cam = cam;
        this.linkBytes = linkBytes;
    }

    @Override
    public String address() {
        return cam.address();
    }

    @Override
    public String name() {
        return cam.label();
    }

    @Override
    public int width() {
        return cam.width();
    }

    @Override
    public int height() {
        return cam.height();
    }

    @Override
    public int fps() {
        return cam.frameRate();
    }

    @Override
    public int effectiveFps() {
        long frameBytes = Math.max(1, (long) cam.width() * cam.height() / 2);
        long byLink = linkBytes / frameBytes;
        return (int) Math.max(1, Math.min(cam.frameRate(), byLink));
    }

    @Override
    public boolean render(int[] px, int w, int h) {
        if (cam.isRemoved()) return false;
        cam.watched();
        int[] f = cam.render(w, h);
        if (f == null) return false;
        System.arraycopy(f, 0, px, 0, Math.min(f.length, px.length));
        return true;
    }

    @Override
    public Map<Object, Object> info() {
        Map<Object, Object> m = cam.info();
        m.put("link", linkBytes);
        m.put("stream", cam.bytesPerSecond());
        m.put("realFps", effectiveFps());
        return m;
    }
}
