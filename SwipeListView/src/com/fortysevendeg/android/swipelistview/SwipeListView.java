/*
 * Copyright (C) 2013 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fortysevendeg.android.swipelistview;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * ListView subclass that provides the swipe functionality
 */
public class SwipeListView extends ListView {

    /**
     * Used when user want change swipe list mode on some rows
     */
    public final static int SWIPE_MODE_DEFAULT = -1;

    /**
     * Disables all swipes
     */
    public final static int SWIPE_MODE_NONE = 0;

    /**
     * Enables both left and right swipe
     */
    public final static int SWIPE_MODE_BOTH = 1;

    /**
     * Enables right swipe
     */
    public final static int SWIPE_MODE_RIGHT = 2;

    /**
     * Enables left swipe
     */
    public final static int SWIPE_MODE_LEFT = 3;

    /**
     * Binds the swipe gesture to reveal a view behind the row (Drawer style)
     */
    public final static int SWIPE_ACTION_REVEAL = 0;

    /**
     * Dismisses the cell when swiped over
     */
    public final static int SWIPE_ACTION_DISMISS = 1;

    /**
     * Marks the cell as checked when swiped and release
     */
    public final static int SWIPE_ACTION_CHECK = 2;

    /**
     * No action when swiped
     */
    public final static int SWIPE_ACTION_NONE = 3;

    /**
     * Default ids for front view
     */
    public final static String SWIPE_DEFAULT_FRONT_VIEW = "swipelist_frontview";

    /**
     * Default id for back view
     */
    public final static String SWIPE_DEFAULT_BACK_VIEW = "swipelist_backview";

    /**
     * Indicates no movement
     */
    private final static int TOUCH_STATE_REST = 0;

    /**
     * State scrolling x position
     */
    private final static int TOUCH_STATE_SCROLLING_X = 1;

    /**
     * State scrolling y position
     */
    private final static int TOUCH_STATE_SCROLLING_Y = 2;

    private int mTouchState = TOUCH_STATE_REST;

    private float mLastMotionX;
    private float mLastMotionY;
    protected int mTouchSlop;

    int mSwipeFrontView = 0;
    int mSwipeBackView = 0;

    /**
     * Internal listener for common swipe events
     */
    private SwipeListViewListener mSwipeListViewListener;

    private CloseItemsListener mCloseItemsListener;

    /**
     * Internal touch listener
     */
    protected SwipeListViewTouchListener mTouchListener;


	/**
     * If you create a View programmatically you need send back and front identifier
     * @param context Context
     * @param swipeBackView Back Identifier
     * @param swipeFrontView Front Identifier
     */
    public SwipeListView(Context context, int swipeBackView, int swipeFrontView) {
        super(context);
        mSwipeFrontView = swipeFrontView;
        mSwipeBackView = swipeBackView;
        init(null);
    }

    /**
     * @see android.widget.ListView#ListView(android.content.Context, android.util.AttributeSet)
     */
    public SwipeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    /**
     * @see android.widget.ListView#ListView(android.content.Context, android.util.AttributeSet, int)
     */
    public SwipeListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    /**
     * Init ListView
     *
     * @param attrs AttributeSet
     */
    protected void init(AttributeSet attrs) {

        int swipeMode = SWIPE_MODE_BOTH;
        boolean swipeOpenOnLongPress = true;
        boolean swipeCloseAllItemsWhenMoveList = true;
        long swipeAnimationTime = 0;
        float swipeOffsetLeft = 0;
        float swipeOffsetRight = 0;

        int swipeActionLeft = SWIPE_ACTION_REVEAL;
        int swipeActionRight = SWIPE_ACTION_REVEAL;

        if (attrs != null) {
            TypedArray styled = getContext().obtainStyledAttributes(attrs, R.styleable.SwipeListView);
            swipeMode = styled.getInt(R.styleable.SwipeListView_swipeMode, SWIPE_MODE_BOTH);
            swipeActionLeft = styled.getInt(R.styleable.SwipeListView_swipeActionLeft, SWIPE_ACTION_REVEAL);
            swipeActionRight = styled.getInt(R.styleable.SwipeListView_swipeActionRight, SWIPE_ACTION_REVEAL);
            swipeOffsetLeft = styled.getDimension(R.styleable.SwipeListView_swipeOffsetLeft, 0);
            swipeOffsetRight = styled.getDimension(R.styleable.SwipeListView_swipeOffsetRight, 0);
            swipeOpenOnLongPress = styled.getBoolean(R.styleable.SwipeListView_swipeOpenOnLongPress, true);
            swipeAnimationTime = styled.getInteger(R.styleable.SwipeListView_swipeAnimationTime, 0);
            swipeCloseAllItemsWhenMoveList = styled.getBoolean(R.styleable.SwipeListView_swipeCloseAllItemsWhenMoveList, true);
            mSwipeFrontView = styled.getResourceId(R.styleable.SwipeListView_swipeFrontView, 0);
            mSwipeBackView = styled.getResourceId(R.styleable.SwipeListView_swipeBackView, 0);
        }

        if (mSwipeFrontView == 0 || mSwipeBackView == 0) {
            mSwipeFrontView = getContext().getResources().getIdentifier(SWIPE_DEFAULT_FRONT_VIEW, "id", getContext().getPackageName());
            mSwipeBackView =  getContext().getResources().getIdentifier(SWIPE_DEFAULT_BACK_VIEW, "id", getContext().getPackageName());

            if (mSwipeFrontView == 0 || mSwipeBackView == 0) {
                throw new RuntimeException(String.format("You forgot the attributes swipeFrontView or swipeBackView. You can add this attributes or use '%s' and '%s' identifiers", SWIPE_DEFAULT_FRONT_VIEW, SWIPE_DEFAULT_BACK_VIEW));
            }
        }

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
        mTouchListener = new SwipeListViewTouchListener(this, mSwipeFrontView, mSwipeBackView);
        if (swipeAnimationTime > 0) {
            mTouchListener.setAnimationTime(swipeAnimationTime);
        }
        mTouchListener.setRightOffset(swipeOffsetRight);
        mTouchListener.setLeftOffset(swipeOffsetLeft);
        mTouchListener.setSwipeActionLeft(swipeActionLeft);
        mTouchListener.setSwipeActionRight(swipeActionRight);
        mTouchListener.setSwipeMode(swipeMode);
        mTouchListener.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
        mTouchListener.setSwipeOpenOnLongPress(swipeOpenOnLongPress);
        setOnTouchListener(mTouchListener);
        setOnScrollListener(mTouchListener.makeScrollListener());
    }

    /**
     * @see android.widget.ListView#setAdapter(android.widget.ListAdapter)
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        mTouchListener.resetItems();
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                onListChanged();
                mTouchListener.resetItems();
            }
        });
    }

    /**
     * Open ListView's item
     *
     * @param position Position that you want open
     */
    public void openAnimate(int position, boolean toRight) {
        mTouchListener.openAnimate(position);
        mSwipeListViewListener.onOpened(position, toRight);
    }

    /**
     * Close ListView's item
     *
     * @param position Position that you want open
     */
    public void closeAnimate(int position) {
        mTouchListener.closeAnimate(position);
    }

    /**
     * Notifies onDismiss
     *
     * @param reverseSortedPositions All dismissed positions
     */
    protected void onDismiss(int[] reverseSortedPositions) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onDismiss(reverseSortedPositions);
        }
    }

    /**
     * Start open item
     * @param position list item
     * @param action current action
     * @param right to right
     */
    protected void onStartOpen(int position, int action, boolean right) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onStartOpen(position, action, right);
        }
    }

    /**
     * Start close item
     * @param position list item
     * @param right
     */
    protected void onStartClose(int position, boolean right) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onStartClose(position, right);
        }
    }

    /**
     * Notifies onClickFrontView
     *
     * @param position item clicked
     */
    protected void onClickFrontView(int position) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onClickFrontView(position);
        }
    }

    /**
     * Notifies onClickBackView
     *
     * @param position back item clicked
     */
    protected void onClickBackView(int position) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onClickBackView(position);
        }
    }

    /**
     * Notifies onOpened
     *
     * @param position Item opened
     * @param toRight  If should be opened toward the right
     */
    protected void onOpened(int position, boolean toRight) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onOpened(position, toRight);
        }
    }

    /**
     * Notifies onClosed
     *
     * @param position  Item closed
     * @param fromRight If open from right
     */
    protected void onClosed(int position, boolean fromRight) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onClosed(position, fromRight);
        }
    }

    /**
     * Notifies onListChanged
     */
    protected void onListChanged() {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onListChanged();
        }
    }

    /**
     * Notifies onMove
     *
     * @param position Item moving
     * @param x        Current position
     */
    protected void onMove(int position, float x) {
        if (mSwipeListViewListener != null) {
            mSwipeListViewListener.onMove(position, x);
        }
    }

    protected int changeSwipeMode(int position) {
        if (mSwipeListViewListener != null) {
            return mSwipeListViewListener.onChangeSwipeMode(position);
        }
        return SWIPE_MODE_DEFAULT;
    }

    /**
     * Sets the Listener
     *
     * @param swipeListViewListener Listener
     */
    public void setSwipeListViewListener(SwipeListViewListener swipeListViewListener) {
        this.mSwipeListViewListener = swipeListViewListener;
    }

    /**
     * Resets scrolling
     */
    public void resetScrolling() {
        mTouchState = TOUCH_STATE_REST;
    }

    /**
     * Set offset on right
     *
     * @param offsetRight Offset
     */
    public void setOffsetRight(float offsetRight) {
        mTouchListener.setRightOffset(offsetRight);
    }

    /**
     * Set offset on left
     *
     * @param offsetLeft Offset
     */
    public void setOffsetLeft(float offsetLeft) {  
        mTouchListener.setLeftOffset(offsetLeft);
    }

    /**
     * Set if all items opened will be closed when the user moves the ListView
     *
     * @param swipeCloseAllItemsWhenMoveList
     */
    public void setSwipeCloseAllItemsWhenMoveList(boolean swipeCloseAllItemsWhenMoveList) {
        mTouchListener.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
    }

    /**
     * Sets if the user can open an item with long pressing on cell
     *
     * @param swipeOpenOnLongPress
     */
    public void setSwipeOpenOnLongPress(boolean swipeOpenOnLongPress) {
        mTouchListener.setSwipeOpenOnLongPress(swipeOpenOnLongPress);
    }

    /**
     * Set swipe mode
     *
     * @param swipeMode
     */
    public void setSwipeMode(int swipeMode) {
		mTouchListener.setSwipeMode(swipeMode);
    }

    /**
     * Return action on left
     *
     * @return Action
     */
    public int getSwipeActionLeft() {
        return mTouchListener.getSwipeActionLeft();
    }

    /**
     * Set action on left
     *
     * @param swipeActionLeft Action
     */
    public void setSwipeActionLeft(int swipeActionLeft) {
        mTouchListener.setSwipeActionLeft(swipeActionLeft);
    }

    /**
     * Return action on right
     *
     * @return Action
     */
    public int getSwipeActionRight() {
        return mTouchListener.getSwipeActionRight();
    }

    /**
     * Set action on right
     *
     * @param swipeActionRight Action
     */
    public void setSwipeActionRight(int swipeActionRight) {
        mTouchListener.setSwipeActionRight(swipeActionRight);
    }

    /**
     * Sets animation time when user drops cell
     *
     * @param animationTime milliseconds
     */
    public void setAnimationTime(long animationTime) {
        mTouchListener.setAnimationTime(animationTime);
    }

    /**
     * @see android.widget.ListView#onInterceptTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        final float x = ev.getX();
        final float y = ev.getY();

		if(isEnabled() && mTouchListener.isSwipeEnabled()) {

			if (mTouchState == TOUCH_STATE_SCROLLING_X) {
				return mTouchListener.onTouch(this, ev);
			}

			switch (action) {
				case MotionEvent.ACTION_MOVE:
					checkInMoving(x, y);
					return mTouchState == TOUCH_STATE_SCROLLING_Y;
				case MotionEvent.ACTION_DOWN:
					mTouchListener.onTouch(this, ev);
					mTouchState = TOUCH_STATE_REST;
					mLastMotionX = x;
					mLastMotionY = y;
					return false;
				case MotionEvent.ACTION_CANCEL:
					mTouchState = TOUCH_STATE_REST;
					break;
				case MotionEvent.ACTION_UP:
					mTouchListener.onTouch(this, ev);
					return mTouchState == TOUCH_STATE_SCROLLING_Y;
				default:
					break;
			}
		}

        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Check if the user is moving the cell
     *
     * @param x Position X
     * @param y Position Y
     */
    private void checkInMoving(float x, float y) {
        final int xDiff = (int) Math.abs(x - mLastMotionX);
        final int yDiff = (int) Math.abs(y - mLastMotionY);

        final int touchSlop = this.mTouchSlop;
        boolean xMoved = xDiff > touchSlop;
        boolean yMoved = yDiff > touchSlop;

        if (xMoved) {
            mTouchState = TOUCH_STATE_SCROLLING_X;
            mLastMotionX = x;
            mLastMotionY = y;
        }

        if (yMoved) {
            mTouchState = TOUCH_STATE_SCROLLING_Y;
            mLastMotionX = x;
            mLastMotionY = y;
        }
    }
    
    /**
     * Close all opened items
     */
    public void closeOpenedItems() {
        mTouchListener.closeOpenedItems();
    }
    
    public SwipeListViewTouchListener getOnScrollListener(){
    	return mTouchListener;
    }

	public CloseItemsListener getCloseItemsListener() {
		return mCloseItemsListener;
	}

	public void setCloseItemsListener(CloseItemsListener closeItemsListener) {
		mCloseItemsListener = closeItemsListener;
	}

    public void resetOpened() {
        if (mTouchListener != null) {
           mTouchListener.resetOpened();
        }
    }

    public boolean isListAtTop() {
        return getChildCount() == 0 || getChildAt(0).getTop() == 0;
    }
}