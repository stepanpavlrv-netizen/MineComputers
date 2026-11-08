package net.minecraft.network.chat;
public class MutableComponent implements Component {
  private String t; public MutableComponent(String t) { this.t = t; }
  public String text() { return t; }
  public MutableComponent withStyle(net.minecraft.ChatFormatting f) { return this; }
  public MutableComponent append(Component c) { t = t + c.text(); return this; }
  public String toString() { return t; }
}
