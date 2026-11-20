package com.fongmi.android.tv;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.utils.ResUtil;

public class Product {

    public static int getDeviceType() {
        return 0;
    }

    public static int getColumn() {
        int widthDp = ResUtil.getScreenWidth() / Math.max(1, ResUtil.dp2px(1));
        // Keep the existing TV/tablet column preference untouched.
        // On phones, use wider cards in portrait and a restrained grid in landscape.
        if (!ResUtil.isPad()) {
            if (widthDp < 480) return 2;
            if (widthDp < 600) return 3;
            return 4;
        }
        return Math.abs(Setting.getSize() - 7);
    }

    public static int getColumn(Style style) {
        int column = getColumn();
        return style.isLand() ? Math.max(2, column - 1) : column;
    }

    public static int[] getSpec(Style style) {
        int column = getColumn(style);
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (column - 1));
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(space, column, style);
    }

    public static int[] getSpec(int space, int column, Style style) {
        int base = ResUtil.getScreenWidth() - space;
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    public static int getEms() {
        return Math.min(ResUtil.getScreenWidth() / ResUtil.sp2px(24), 35);
    }
}
