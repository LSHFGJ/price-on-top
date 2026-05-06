package com.android.systemui.statusbar.views;

public class MiuiClock {
    private CharSequence text;

    public MiuiClock(CharSequence text) {
        this.text = text;
    }

    public CharSequence getText() {
        return text;
    }

    public void setText(CharSequence text) {
        this.text = text;
    }
}
