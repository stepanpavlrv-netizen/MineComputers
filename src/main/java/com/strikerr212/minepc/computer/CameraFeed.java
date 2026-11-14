package com.strikerr212.minepc.computer;

import java.util.Map;

/** Камера в сети, найденная компьютером: из неё можно брать кадры (в потоке компьютера). */
public interface CameraFeed {

    String address();

    String name();

    /** Разрешение и частота, заданные на камере. */
    int width();

    int height();

    int fps();

    /** Сколько кадров в секунду реально пролезает по сети (медленная линия режет частоту). */
    int effectiveFps();

    /** Нарисовать кадр камеры (w×h — её разрешение). false — снимок мира ещё собирается. */
    boolean render(int[] px, int w, int h);

    /** Всё о камере (для программ). */
    Map<Object, Object> info();
}
