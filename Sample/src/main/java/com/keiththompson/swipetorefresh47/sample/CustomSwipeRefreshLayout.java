package com.keiththompson.swipetorefresh47.sample;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;

import com.fortysevendeg.android.swipelistview.RefreshSwipeListener;

/**
 * Created by Keith Thompson on 30/04/2014.
 *
 * Extended SwipeRefreshLayout to work with 47Deg's SwipeListView
 *
 * We use the RefreshSwipeListener in
 * the touch listener to set whether the refresh view should scroll up or not
 *
 */
public class CustomSwipeRefreshLayout extends SwipeRefreshLayout implements RefreshSwipeListener {

    /**
     * CanScroll variable holds whether view can scroll up or not
     */
    private boolean mCanScroll = false;

    public CustomSwipeRefreshLayout(Context context) {
        super(context);
    }

    public CustomSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     *
     * @return Whether it is possible for
     * the child view of this layout to scroll up.
     */
    @Override
    public boolean canChildScrollUp () {
        return mCanScroll;
    }

    /**
     * Callback from RefreshSwipeListener
     * @param canScroll the value to set mCanScroll to
     */
    @Override
    public void setScroll(boolean canScroll) {
        mCanScroll = canScroll;
    }
}
