package cn.bigfish.phone;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.ScrollView;

public final class ViewportScrollView extends ScrollView {
    private boolean updatingStatus;
    private boolean preserveNextLayout;

    public ViewportScrollView(Context context) { super(context); }

    public void updateStatus(Runnable update) {
        int previousX = getScrollX();
        int previousY = getScrollY();
        updatingStatus = true;
        try { update.run(); }
        finally {
            updatingStatus = false;
            preserveNextLayout = true;
            if (getScrollX() != previousX || getScrollY() != previousY) scrollTo(previousX, previousY);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int previousX = getScrollX();
        int previousY = getScrollY();
        boolean preserve = preserveNextLayout;
        preserveNextLayout = false;
        super.onLayout(changed, left, top, right, bottom);
        if (preserve && (getScrollX() != previousX || getScrollY() != previousY)) scrollTo(previousX, previousY);
    }

    @Override public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        return !updatingStatus && super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    @Override public void requestChildFocus(View child, View focused) {
        if (!updatingStatus) preserveNextLayout = false;
        super.requestChildFocus(child, focused);
    }
}
