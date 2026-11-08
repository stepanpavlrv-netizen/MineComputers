package net.minecraft.network.chat;
public interface Component {
  String text();
  static MutableComponent translatable(String key, Object... args) {
    StringBuilder b = new StringBuilder(key);
    if (args.length > 0) { b.append("("); for (Object a : args) b.append(a instanceof Component c ? c.text() : String.valueOf(a)).append(","); b.append(")"); }
    return new MutableComponent(b.toString()); }
  static MutableComponent literal(String s) { return new MutableComponent(s); }
  default MutableComponent copy() { return new MutableComponent(text()); }
}
