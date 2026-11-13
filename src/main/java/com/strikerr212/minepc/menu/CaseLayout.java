package com.strikerr212.minepc.menu;

import com.strikerr212.minepc.block.PcCaseBlockEntity;

/**
 * Раскладка окна системного блока 2.0 (276×236): корпус без боковой крышки слева, экран состояния справа,
 * инвентарь и передняя панель снизу. Детали стоят там, где стоят в настоящем компьютере, и ловят мышь своей формой
 * ({@link #hit}), а не квадратом 16×16.
 *
 * Обычный корпус — вид сбоку: плата на задней стенке, сокет и память сверху, видеокарта, M.2, внизу кожух с блоком
 * питания, корзина дисков спереди, крепления SSD на кожухе. Серверное шасси — вид сверху: спереди отсеки дисков,
 * стена вентиляторов, плата на два сокета с памятью в три ряда, сзади два блока питания.
 */
public final class CaseLayout {

    private CaseLayout() {
    }

    public static final int W = 276, H = 236;
    /** Корпус изнутри: [CX0, CX1) × [CY0, CY1). */
    public static final int CX0 = 7, CY0 = 16, CX1 = 177, CY1 = 144;
    /** Плата обычного корпуса. */
    public static final int BX0 = 17, BY0 = 22, BX1 = 129, BY1 = 112;
    /** Плата серверного шасси. */
    public static final int SBX0 = 63, SBY0 = 19, SBX1 = 157, SBY1 = 141;
    /** Кожух блока питания (обычный корпус). */
    public static final int SHROUD_Y = 114;
    public static final int INV_X = 8, INV_Y = 160;
    public static final int USB_X = 187, USB_Y = 163;

    /** Стопка памяти сервера: ряды A (у первого сокета), C (у второго), B — между ними (верх и низ). */
    public static final int SRV_RAM_X0 = 67, SRV_RAM_X1 = 107, SRV_STICK = 4;

    /** Сколько отсеков у шасси с bays отсеками: обычный корпус — 2 HDD + 2 SSD. */
    public static int[] hit(boolean server, int bays, int slot) {
        return server ? serverHit(bays, slot) : desktopHit(slot);
    }

    private static int[] r(int x0, int y0, int x1, int y1) {
        return new int[]{x0, y0, x1, y1};
    }

    private static int[] desktopHit(int slot) {
        if (PcCaseBlockEntity.isUsb(slot)) return usb(slot);
        int ram = PcCaseBlockEntity.ramIndex(slot);
        if (ram >= 0) return ram < 4 ? r(76 + 7 * ram, 26, 83 + 7 * ram, 72) : null;
        if (slot == PcCaseBlockEntity.SLOT_MOTHERBOARD) return r(BX0, BY0, BX1, BY1);
        if (slot == PcCaseBlockEntity.SLOT_CPU) return r(41, 36, 63, 58);
        if (slot == PcCaseBlockEntity.SLOT_COOLER) return r(34, 29, 70, 65);
        if (slot == PcCaseBlockEntity.SLOT_GPU) return r(22, 78, 130, 93);
        if (slot == PcCaseBlockEntity.SLOT_PSU) return r(13, 117, 63, 141);
        if (slot == PcCaseBlockEntity.SLOT_HDD || slot == PcCaseBlockEntity.SLOT_HDD + 1) {
            int i = slot - PcCaseBlockEntity.SLOT_HDD;
            return r(131, 116 + 13 * i, 171, 128 + 13 * i);
        }
        if (slot == PcCaseBlockEntity.SLOT_SATA_SSD || slot == PcCaseBlockEntity.SLOT_SATA_SSD + 1) {
            int i = slot - PcCaseBlockEntity.SLOT_SATA_SSD;
            return r(70 + 28 * i, 117, 94 + 28 * i, 126);
        }
        if (slot >= PcCaseBlockEntity.SLOT_M2 && slot < PcCaseBlockEntity.SLOT_M2 + PcCaseBlockEntity.M2_SLOTS) {
            int i = slot - PcCaseBlockEntity.SLOT_M2;
            return r(38, 69 + 36 * i, 71, 76 + 36 * i);
        }
        return null;   // второй процессор, второй БП, память 5–16, отсеки 5–8 — только у сервера
    }

    private static int[] usb(int slot) {
        int i = slot - PcCaseBlockEntity.SLOT_USB;
        return r(USB_X + 20 * i, USB_Y, USB_X + 20 * i + 18, USB_Y + 18);
    }

    /** Место планки памяти сервера k (0..15) по вертикали: ряд A, ряд C, B-верх, B-низ. */
    public static int serverRamY(int k) {
        int bank = k / 4, i = k % 4;
        int y0 = switch (bank) {
            case 0 -> 20;          // A — над первым сокетом
            case 1 -> 124;         // C — под вторым
            case 2 -> 64;          // B — верхняя половина (первый сокет)
            default -> 80;         // B — нижняя половина (второй сокет)
        };
        return y0 + i * SRV_STICK;
    }

    private static int[] serverHit(int bays, int slot) {
        if (PcCaseBlockEntity.isUsb(slot)) return usb(slot);
        int ram = PcCaseBlockEntity.ramIndex(slot);
        if (ram >= 0) {
            int y = serverRamY(ram);
            return r(SRV_RAM_X0, y, SRV_RAM_X1, y + SRV_STICK);
        }
        int bay = PcCaseBlockEntity.bayIndex(slot);
        if (bay >= 0) {
            if (bay >= bays) return null;
            return bays > 4 ? r(10, 20 + 15 * bay, 40, 34 + 15 * bay) : r(10, 20 + 30 * bay, 40, 48 + 30 * bay);
        }
        if (slot == PcCaseBlockEntity.SLOT_MOTHERBOARD) return r(SBX0, SBY0, SBX1, SBY1);
        if (slot == PcCaseBlockEntity.SLOT_CPU) return r(71, 37, 101, 61);
        if (slot == PcCaseBlockEntity.SLOT_CPU2) return r(71, 97, 101, 121);
        if (slot == PcCaseBlockEntity.SLOT_COOLER) return r(67, 33, 105, 65);
        if (slot == PcCaseBlockEntity.SLOT_GPU) return r(111, 70, 151, 88);
        if (slot == PcCaseBlockEntity.SLOT_PSU) return r(159, 20, 175, 62);
        if (slot == PcCaseBlockEntity.SLOT_PSU2) return r(159, 64, 175, 106);
        if (slot >= PcCaseBlockEntity.SLOT_M2 && slot < PcCaseBlockEntity.SLOT_M2 + PcCaseBlockEntity.M2_SLOTS) {
            int i = slot - PcCaseBlockEntity.SLOT_M2;
            return r(111, 23 + 9 * i, 143, 29 + 9 * i);
        }
        return null;
    }

    /**
     * Где игра рисует значок предмета слота. Детали корпуса рисует само окно, поэтому значки слотов корпуса
     * уносятся за край экрана — у каждого своё место, по нему окно узнаёт слот (USB — как обычно, на передней панели).
     */
    public static int iconX(int slot) {
        if (PcCaseBlockEntity.isUsb(slot)) return usb(slot)[0] + 1;
        return -3000 - slot * 20;
    }

    public static int iconY(int slot) {
        if (PcCaseBlockEntity.isUsb(slot)) return usb(slot)[1] + 1;
        return -3000;
    }

    public static boolean inside(int[] r, int x, int y) {
        return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }
}
