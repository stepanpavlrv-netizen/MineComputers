package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/** Настройки роутера: имя сети (SSID), пароль, радиус. */
public class RouterBlockEntity extends BlockEntity implements CordList.Holder {

    public static final int MIN_RADIUS = 4, MAX_RADIUS = 48, MAX_SSID = 24, MIN_PASSWORD = 8, MAX_PASSWORD = 32;
    /** Скорость Wi-Fi со стороны роутера, Мбит/с (как у Wi-Fi 5): клиент получит не больше, чем умеет его адаптер. */
    public static final int RADIO_MBIT = 1200;

    private String ssid = "";
    private String password = "";
    private int radius = 32;
    private final CordList cords = new CordList();

    @Override
    public CordList cords() {
        return cords;
    }

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
    }

    public String ssid() {
        if (ssid.isEmpty()) ssid = String.format(Locale.ROOT, "MineNet-%04X", (worldPosition.hashCode() & 0xFFFF));
        return ssid;
    }

    public String password() {
        return password;
    }

    public int radius() {
        return radius;
    }

    public boolean secured() {
        return !password.isEmpty();
    }

    /** Проверка введённого игроком: null — всё хорошо, иначе ключ перевода с причиной. */
    public static String validate(String ssid, String password, int radius) {
        if (ssid.isBlank() || ssid.length() > MAX_SSID) return "gui.minepc.router.bad_ssid";
        if (!password.isEmpty() && (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD)) return "gui.minepc.router.bad_password";
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) return "gui.minepc.router.bad_radius";
        return null;
    }

    public void apply(String ssid, String password, int radius) {
        this.ssid = ssid.strip();
        this.password = password;
        this.radius = radius;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ssid", ssid());
        tag.putString("password", password);
        tag.putInt("radius", radius);
        cords.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ssid = tag.getString("ssid");
        password = tag.getString("password");
        radius = tag.contains("radius") ? Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, tag.getInt("radius"))) : 32;
        cords.load(tag);
    }

    /** Корды видны игрокам; пароль роутера игрокам не отправляем. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        cords.save(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        cords.load(tag);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        cords.load(pkt.getTag());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LanManager.updateDevice(level, worldPosition);
    }
}
