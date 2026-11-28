package cc.openfic.one;

/** OpenFic 主题配色，取值对齐 Radix Themes 的 gray / indigo 调色板。 */
final class Palette {
    static final Palette LIGHT = new Palette(false);
    static final Palette DARK = new Palette(true);

    final boolean dark;
    final int bg;
    final int panel;
    final int text;
    final int muted;
    final int faint;
    final int border;
    final int inputBg;
    final int accent;
    final int accentContrast;
    final int accentSoft;
    final int accentSoftText;

    private Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF111111;
            panel = 0xFF191919;
            text = 0xFFEEEEEE;
            muted = 0xFFB4B4B4;
            faint = 0xFF8B8B8B;
            border = 0xFF3A3A3A;
            inputBg = 0xFF222222;
            accent = 0xFF3E63DD;
            accentContrast = 0xFFFFFFFF;
            accentSoft = 0xFF182449;
            accentSoftText = 0xFF9EB1FF;
        } else {
            bg = 0xFFFCFCFC;
            panel = 0xFFFFFFFF;
            text = 0xFF202020;
            muted = 0xFF646464;
            faint = 0xFF8D8D8D;
            border = 0xFFD9D9D9;
            inputBg = 0xFFF0F0F0;
            accent = 0xFF3E63DD;
            accentContrast = 0xFFFFFFFF;
            accentSoft = 0xFFEDF2FE;
            accentSoftText = 0xFF3A5BC7;
        }
    }

    static Palette of(MainActivity act) {
        return Theme.isDark(act) ? DARK : LIGHT;
    }

    /** 遮罩层底色。 */
    int mask() {
        return dark ? 0x99000000 : 0x66000000;
    }
}
