/*
 * Copyright (C) 2013 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
 *
 * Copyright 2012 Roman Nurik
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

import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

/**
 * Touch listener impl for the SwipeListView
 */
public class SwipeRefreshListViewTouchListener extends SwipeListViewTouchListener {


    private RefreshSwipeListener mRefreshSwipeListener;

    /**
     * Constructor
     *
     * @param swipeListView  SwipeListView
     * @param swipeFrontView front view Identifier
     * @param swipeBackView  back view Identifier
     */
    public SwipeRefreshListViewTouchListener(RefreshSwipeListView swipeListView,
                                             int swipeFrontView, int swipeBackView) {
        super(swipeListView, swipeFrontView, swipeBackView);
    }

    /**
     * Return ScrollListener for ListView
     * @return OnScrollListener
     */
    @Override
    public AbsListView.OnScrollListener makeScrollListener() {
        return new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                if (mSwipeListView.isListAtTop() && !mSwiping) {
                    if (mRefreshSwipeListener != null) {
                        mRefreshSwipeListener.setScroll(false);
                    }
                } else {
                    if (mRefreshSwipeListener != null) {
                        mRefreshSwipeListener.setScroll(true);
                    }
                }

                scrollChange(scrollState);
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {}
        };
    }

    /**
     * @see View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
     */
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (!isSwipeEnabled()) {
            return false;
        }

        mViewWidth = mSwipeListView.getWidth();

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                return actionDown(view, motionEvent);
            }

            case MotionEvent.ACTION_UP: {
                actionUp(motionEvent);
                if (mRefreshSwipeListener != null && mSwipeListView.isListAtTop()) {
                    mRefreshSwipeListener.setScroll(false);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker == null || mPaused || mDownPosition == ListView.INVALID_POSITION) {
                    break;
                }

                mVelocityTracker.addMovement(motionEvent);
                mVelocityTracker.computeCurrentVelocity(1000);
                float velocityX = Math.abs(mVelocityTracker.getXVelocity());
                float velocityY = Math.abs(mVelocityTracker.getYVelocity());

                float deltaX = motionEvent.getRawX() - mDownX;
                float deltaMode = Math.abs(deltaX);

                int swipeMode = mSwipeMode;
                int changeSwipeMode = mSwipeListView.changeSwipeMode(mDownPosition);
                if (changeSwipeMode >= 0) {
                    swipeMode = changeSwipeMode;
                }

                if (swipeMode == SwipeListView.SWIPE_MODE_NONE) {
                    deltaMode = 0;
                } else if (swipeMode != SwipeListView.SWIPE_MODE_BOTH) {
                    if (mOpened.get(mDownPosition)) {
                        if (swipeMode == SwipeListView.SWIPE_MODE_LEFT && deltaX < 0) {
                            deltaMode = 0;
                        } else if (swipeMode == SwipeListView.SWIPE_MODE_RIGHT && deltaX > 0) {
                            deltaMode = 0;
                        }
                    } else {
                        if (swipeMode == SwipeListView.SWIPE_MODE_LEFT && deltaX > 0) {
                            deltaMode = 0;
                        } else if (swipeMode == SwipeListView.SWIPE_MODE_RIGHT && deltaX < 0) {
                            deltaMode = 0;
                        }
                    }
                }
                if (deltaMode > mSlop && mSwipeCurrentAction == SwipeListView.SWIPE_ACTION_NONE && velocityY < velocityX) {
                    mSwiping = true;
                    if (mRefreshSwipeListener != null) {
                        mRefreshSwipeListener.setScroll(true);
                    }
                    boolean swipingRight = (deltaX > 0);
                    if (mOpened.get(mDownPosition)) {
                        mSwipeListView.onStartClose(mDownPosition, swipingRight);
                        mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                    } else {
                        if (swipingRight && mSwipeActionRight == SwipeListView.SWIPE_ACTION_DISMISS) {
                            mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_DISMISS;
                        } else if (!swipingRight && mSwipeActionLeft == SwipeListView.SWIPE_ACTION_DISMISS) {
                            mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_DISMISS;
                        } else if (swipingRight && mSwipeActionRight == SwipeListView.SWIPE_ACTION_CHECK) {
                            mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_CHECK;
                        } else if (!swipingRight && mSwipeActionLeft == SwipeListView.SWIPE_ACTION_CHECK) {
                            mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_CHECK;
                        } else {
                            mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                        }
                        mSwipeListView.onStartOpen(mDownPosition, mSwipeCurrentAction, swipingRight);
                    }
                    mSwipeListView.requestDisallowInterceptTouchEvent(true);
                    MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL |
                            (MotionEventCompat.getActionIndex(motionEvent) << MotionEventCompat.ACTION_POINTER_INDEX_SHIFT));
                    mSwipeListView.onTouchEvent(cancelEvent);
                }

                if (mSwiping) {
                    if (mOpened.get(mDownPosition)) {
                        deltaX += mOpenedRight.get(mDownPosition) ? mViewWidth - mRightOffset : -mViewWidth + mLeftOffset;
                    }
                    move(deltaX);
                    return true;
                }
                break;
            }
        }
        return false;
    }

    public void setRefreshSwipeListener(RefreshSwipeListener refreshSwipeListener) {
        mRefreshSwipeListener = refreshSwipeListener;
    }
}
