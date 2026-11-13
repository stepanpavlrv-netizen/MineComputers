package com.strikerr212.minepc.lan;

/**
 * Конец сети с адресом: компьютер (системный блок, сервер в стойке) или устройство без компьютера внутри —
 * контроллер ввода-вывода. Ему доставляются сообщения, он может их отправлять.
 */
public interface NetEndpoint {

    /** Постоянный адрес в сети. */
    String address();

    /** Имя (hostname) или "". */
    String label();

    /** Принимает сообщения сейчас (компьютер включён, контроллер — всегда). */
    boolean netOnline();

    /** Пришло сообщение по сети (вызывает LanManager в тике сервера). */
    void netReceive(String from, int port, int hops, Object[] payload, int bytes);
}
