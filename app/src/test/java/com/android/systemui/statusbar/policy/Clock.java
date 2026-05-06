package com.android.systemui.statusbar.policy;

public class Clock {
    private CharSequence text;

    public Clock(CharSequence text) {
        this.text = text;
    }

    public CharSequence getText() {
        return text;
    }

    public void setText(CharSequence text) {
        this.text = text;
    }
}
