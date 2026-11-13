package com.strikerr212.minepc.client;

import com.strikerr212.minepc.lan.RouterBlockEntity;
import com.strikerr212.minepc.network.RouterOpenPayload;
import com.strikerr212.minepc.network.RouterSavePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Окно роутера: имя сети, пароль, радиус и кто сейчас подключён (список обновляется сам раз в 2 секунды). */
public class RouterScreen extends Screen {

    private static final int W = 248, H = 214;
    private static final int BG = 0xF01D2026, PANEL = 0xFF272B33, LINE = 0xFF3A404B, FG = 0xFFE6E9EF, DIM = 0xFF8D95A3,
            ACCENT = 0xFF4EA1FF, GOOD = 0xFF5BD982, WARN = 0xFFF2B13C, BAD = 0xFFE5534B;

    private final BlockPos pos;
    private String ssid, password;
    private int radius;
    private List<String[]> clients = List.of();
    private EditBox ssidBox, passBox;
    private boolean showPassword;
    private Button showButton;
    private int left, top, age;

    public RouterScreen(RouterOpenPayload p) {
        super(Component.translatable("gui.minepc.router.title"));
        this.pos = p.pos();
        this.ssid = p.ssid();
        this.password = p.password();
        this.radius = p.radius();
        update(p);
    }

    public BlockPos pos() {
        return pos;
    }

    /** Пришёл свежий список подключённых (поля, которые игрок сейчас правит, не трогаем). */
    public void update(RouterOpenPayload p) {
        List<String[]> list = new ArrayList<>();
        for (String line : p.clients().split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            if (f.length >= 4) list.add(f);
        }
        clients = list;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (ssidBox != null) {
            ssid = ssidBox.getValue();
            password = passBox.getValue();
        }
        ssidBox = addRenderableWidget(new EditBox(font, left + 10, top + 36, W - 20, 16, Component.translatable("gui.minepc.router.ssid")));
        ssidBox.setMaxLength(RouterBlockEntity.MAX_SSID);
        ssidBox.setValue(ssid);
        passBox = addRenderableWidget(new EditBox(font, left + 10, top + 70, W - 44, 16, Component.translatable("gui.minepc.router.password")));
        passBox.setMaxLength(RouterBlockEntity.MAX_PASSWORD);
        passBox.setValue(password);
        passBox.setFormatter((text, start) -> FormattedCharSequence.forward(showPassword ? text : "•".repeat(text.length()),
                net.minecraft.network.chat.Style.EMPTY));
        showButton = addRenderableWidget(Button.builder(Component.literal(showPassword ? "abc" : "•••"), b -> {
            showPassword = !showPassword;
            b.setMessage(Component.literal(showPassword ? "abc" : "•••"));
        }).bounds(left + W - 32, top + 70, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> radius = Math.max(RouterBlockEntity.MIN_RADIUS, radius - 4))
                .bounds(left + W - 58, top + 96, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> radius = Math.min(maxRadius(), radius + 4))
                .bounds(left + W - 32, top + 96, 20, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minepc.router.save"), b -> save())
                .bounds(left + 10, top + H - 26, 110, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 120, top + H - 26, 110, 18).build());
        setInitialFocus(ssidBox);
    }

    /** У потолочной точки доступа радиус меньше, чем у роутера. */
    private int maxRadius() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && level.getBlockState(pos).getBlock() instanceof com.strikerr212.minepc.lan.AccessPointBlock
                ? com.strikerr212.minepc.lan.AccessPointBlock.MAX_RADIUS : RouterBlockEntity.MAX_RADIUS;
    }

    private void save() {
        PacketDistributor.sendToServer(new RouterSavePayload(pos, ssidBox.getValue(), passBox.getValue(), radius, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (++age % 40 == 0) PacketDistributor.sendToServer(new RouterSavePayload(pos, "", "", 0, true));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, LINE);
        g.fill(left, top, left + W, top + H, BG);
        g.fill(left, top, left + W, top + 20, PANEL);
        wifiIcon(g, left + 8, top + 5, ACCENT);
        g.drawString(font, title, left + 26, top + 6, FG, false);
        String count = String.valueOf(clients.size());
        g.drawString(font, count, left + W - 10 - font.width(count), top + 6, clients.isEmpty() ? DIM : GOOD, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Component.translatable("gui.minepc.router.ssid"), left + 10, top + 26, DIM, false);
        Component passLabel = Component.translatable(passBox.getValue().isEmpty() ? "gui.minepc.router.open" : "gui.minepc.router.password");
        g.drawString(font, passLabel, left + 10, top + 60, passBox.getValue().isEmpty() ? WARN : DIM, false);
        g.drawString(font, Component.translatable("gui.minepc.router.radius"), left + 10, top + 100, DIM, false);
        String r = Component.translatable("gui.minepc.router.blocks", radius).getString();
        g.drawString(font, r, left + W - 64 - font.width(r), top + 100, FG, false);

        int y = top + 120;
        g.fill(left + 10, y, left + W - 10, y + 1, LINE);
        g.drawString(font, Component.translatable("gui.minepc.router.clients"), left + 10, y + 5, DIM, false);
        y += 17;
        if (clients.isEmpty()) {
            g.drawString(font, Component.translatable("gui.minepc.router.none"), left + 10, y, DIM, false);
        }
        int shown = 0;
        for (String[] c : clients) {
            if (shown == 4) {
                g.drawString(font, Component.translatable("gui.minepc.router.more", clients.size() - 4), left + 10, y, DIM, false);
                break;
            }
            String name = font.plainSubstrByWidth(c[0].isEmpty() ? Component.translatable("gui.minepc.net.noname").getString() : c[0], 78);
            String addr = c[1].length() > 6 ? c[1].substring(0, 6) : c[1];
            int signal = parse(c[2]);
            g.drawString(font, name, left + 10, y, c[0].isEmpty() ? DIM : FG, false);
            g.drawString(font, addr, left + 92, y, 0xFF7FD48A, false);
            bars(g, left + 134, y + 7, signal);
            String speed = c[3] + " " + Component.translatable("gui.minepc.router.mbit").getString();
            g.drawString(font, speed, left + W - 10 - font.width(speed), y, DIM, false);
            y += 11;
            shown++;
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final String[] WIFI = {
            "..#######..",
            ".#.......#.",
            "#..#####..#",
            "..#.....#..",
            "....###....",
            "...........",
            ".....#.....",
    };

    /** Значок Wi-Fi: три дуги и точка. */
    private static void wifiIcon(GuiGraphics g, int x, int y, int color) {
        for (int r = 0; r < WIFI.length; r++) {
            for (int c = 0; c < WIFI[r].length(); c++) {
                if (WIFI[r].charAt(c) == '#') g.fill(x + c, y + r + 1, x + c + 1, y + r + 2, color);
            }
        }
    }

    /** Сила сигнала: четыре столбика. */
    private static void bars(GuiGraphics g, int x, int baseY, int signal) {
        int lit = signal >= 75 ? 4 : signal >= 50 ? 3 : signal >= 25 ? 2 : signal > 0 ? 1 : 0;
        int color = lit >= 3 ? GOOD : lit == 2 ? WARN : BAD;
        for (int i = 0; i < 4; i++) {
            int h = 2 + i * 2;
            g.fill(x + i * 3, baseY - h, x + i * 3 + 2, baseY, i < lit ? color : LINE);
        }
        String pct = signal + "%";
        g.drawString(Minecraft().font, pct, x + 14, baseY - 7, DIM, false);
    }

    private static net.minecraft.client.Minecraft Minecraft() {
        return net.minecraft.client.Minecraft.getInstance();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
