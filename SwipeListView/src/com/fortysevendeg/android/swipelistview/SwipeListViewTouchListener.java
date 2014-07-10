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

import android.graphics.Rect;
import android.os.Handler;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.nineoldandroids.view.ViewHelper.setAlpha;
import static com.nineoldandroids.view.ViewHelper.setTranslationX;
import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

/**
 * Touch listener impl for the SwipeListView
 */
public class SwipeListViewTouchListener implements View.OnTouchListener {

	protected int mSwipeMode = SwipeListView.SWIPE_MODE_BOTH;
	private boolean mSwipeOpenOnLongPress = true;
	protected boolean mSwipeClosesAllItemsWhenListMoves = true;

	protected int mSwipeFrontView = 0;
	protected int mSwipeBackView = 0;

	protected Rect mRect = new Rect();

	// Cached ViewConfiguration and system-wide constant values
    protected int mSlop;
	protected int mMinFlingVelocity;
	protected int mMaxFlingVelocity;
    private long mAnimationTime;

	protected float mLeftOffset = 0;
	protected float mRightOffset = 0;

	// Fixed properties
	protected SwipeListView mSwipeListView;
	protected int mViewWidth = 1; // 1 and not 0 to prevent dividing by zero

	private List<PendingDismissData> mPendingDismisses = new ArrayList<PendingDismissData>();
	private int mDismissAnimationRefCount = 0;

	protected float mDownX;
	protected boolean mSwiping;
	protected VelocityTracker mVelocityTracker;
	protected int mDownPosition;
	private View mParentView;
	protected View mFrontView;
	protected boolean mPaused;

	protected int mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;

	protected int mSwipeActionLeft = SwipeListView.SWIPE_ACTION_REVEAL;
	protected int mSwipeActionRight = SwipeListView.SWIPE_ACTION_REVEAL;

	protected List<Boolean> mOpened = new ArrayList<Boolean>();
	protected List<Boolean> mOpenedRight = new ArrayList<Boolean>();
	protected boolean mListViewMoving;

	/**
	 * Constructor
	 * @param swipeListView SwipeListView
	 * @param swipeFrontView front view Identifier
	 * @param swipeBackView back view Identifier
	 */
	public SwipeListViewTouchListener(SwipeListView swipeListView, int swipeFrontView, int swipeBackView) {
        mSwipeFrontView = swipeFrontView;
        mSwipeBackView = swipeBackView;
		ViewConfiguration vc = ViewConfiguration.get(swipeListView.getContext());
		mSlop = vc.getScaledTouchSlop();
		mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
		mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mAnimationTime = swipeListView.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
		this.mSwipeListView = swipeListView;
	}

	/**
	 * Sets current item's parent view
	 * @param parentView Parent view
	 */
	protected void setParentView(View parentView) {
		mParentView = parentView;
	}

	protected boolean allItemsClosed(){
        for (Boolean anOpened : mOpened) {
            if (anOpened) {
                return false;
            }
        }
		return true;
	}

	/**
	 * Sets current item's front view
	 * @param frontView Front view
	 */
    protected void setFrontView(View frontView) {
		mFrontView = frontView;
		frontView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mDownPosition == ListView.INVALID_POSITION) {
					return;
				}
				mSwipeListView.onClickFrontView(mDownPosition);
			}
		});
		if (mSwipeOpenOnLongPress) {
			frontView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					openAnimate(mDownPosition);
					return false;
				}
			});
		}
	}

    public void resetOpened() {
        mOpened = new ArrayList<Boolean>();
    }

	/**
	 * Set current item's back view
	 * @param backView
	 */
    protected void setBackView(View backView) {
		backView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mSwipeListView.onClickBackView(mDownPosition);
			}
		});
	}

	/**
	 * @return true if the list is in motion
	 */
	public boolean isListViewMoving() {
		return mListViewMoving;
	}

	/**
	 * Sets animation time when the user drops the cell
	 *
	 * @param animationTime milliseconds
	 */
	public void setAnimationTime(long animationTime) {
	    mAnimationTime = animationTime;
	}

	/**
	 * Sets the right offset
	 *
	 * @param rightOffset Offset
	 */
	public void setRightOffset(float rightOffset) {
		mRightOffset = rightOffset;
	}

	/**
	 * Set the left offset
	 *
	 * @param leftOffset Offset
	 */
	public void setLeftOffset(float leftOffset) {
		mLeftOffset = leftOffset;
	}

	/**
	 * Set if all item opened will be close when the user move ListView
	 *
	 * @param swipeClosesAllItemsWhenListMoves
	 */
	public void setSwipeClosesAllItemsWhenListMoves(boolean swipeClosesAllItemsWhenListMoves) {
	    mSwipeClosesAllItemsWhenListMoves = swipeClosesAllItemsWhenListMoves;
	}

	/**
	 * Set if the user can open an item with long press on cell
	 *
	 * @param swipeOpenOnLongPress
	 */
	public void setSwipeOpenOnLongPress(boolean swipeOpenOnLongPress) {
		mSwipeOpenOnLongPress = swipeOpenOnLongPress;
	}

	/**
	 * Sets the swipe mode
	 *
	 * @param swipeMode
	 */
	public void setSwipeMode(int swipeMode) {
        mSwipeMode = swipeMode;
	}

	/**
	 * Check is swiping is enabled
	 *
	 * @return
	 */
	protected boolean isSwipeEnabled() {
		return mSwipeMode != SwipeListView.SWIPE_MODE_NONE;
	}

	/**
	 * Return action on left
	 *
	 * @return Action
	 */
	public int getSwipeActionLeft() {
		return mSwipeActionLeft;
	}

	/**
	 * Set action on left
	 *
	 * @param swipeActionLeft Action
	 */
	public void setSwipeActionLeft(int swipeActionLeft) {
        mSwipeActionLeft = swipeActionLeft;
	}

	/**
	 * Return action on right
	 *
	 * @return Action
	 */
	public int getSwipeActionRight() {
		return mSwipeActionRight;
	}

	/**
	 * Set action on right
	 *
	 * @param swipeActionRight Action
	 */
	public void setSwipeActionRight(int swipeActionRight) {
        mSwipeActionRight = swipeActionRight;
	}

	/**
	 * Adds new items when adapter is modified
	 */
	public void resetItems() {
		if (mSwipeListView.getAdapter() != null) {
			int count = mSwipeListView.getAdapter().getCount();
			for (int i = mOpened.size(); i <= count; i++) {
                mOpened.add(false);
				mOpenedRight.add(false);
			}
		}
	}

	/**
	 * Open item
	 * @param position Position of list
	 */
	protected void openAnimate(int position) {
		openAnimate(mSwipeListView.getChildAt(position -
                mSwipeListView.getFirstVisiblePosition()).findViewById(mSwipeFrontView), position);
	}

	/**
	 * Close item
	 * @param position Position of list
	 */
	protected void closeAnimate(int position) {
		closeAnimate(mSwipeListView.getChildAt(position -
                mSwipeListView.getFirstVisiblePosition()).findViewById(mSwipeFrontView), position);
	}

	/**
	 * Open item
	 * @param view affected view
	 * @param position Position of list
	 */
	private void openAnimate(View view, int position) {
		if (!mOpened.get(position)) {
			generateRevealAnimate(view, true, false, position);
		}
	}

	/**
	 * Close item
	 * @param view affected view
	 * @param position Position of list
	 */
	private void closeAnimate(View view, int position) {
		if (mOpened.get(position)) {
            if(view != null){
                generateRevealAnimate(view, true, false, position);
            } else {
                if(mSwipeListView.getCloseItemsListener() != null){
                    mSwipeListView.getCloseItemsListener().onAllItemsClosed();
                }
            }
		}
	}

	/**
	 * Create animation
	 * @param view affected view
	 * @param swap If state should change. If "false" returns to the original position
	 * @param swapRight If swap is true, this parameter tells if move is to the right or left
	 * @param position Position of list
	 */
    protected void generateAnimate(final View view, final boolean swap,
                                   final boolean swapRight, final int position) {
		if (mSwipeCurrentAction == SwipeListView.SWIPE_ACTION_REVEAL) {
			generateRevealAnimate(view, swap, swapRight, position);
		}

		if (mSwipeCurrentAction == SwipeListView.SWIPE_ACTION_DISMISS) {
			generateDismissAnimate(mParentView, swap, swapRight, position);
		}
	}

	/**
	 * Create dismiss animation
	 * @param view affected view
	 * @param swap If will change state. If is "false" returns to the original position
	 * @param swapRight If swap is true, this parameter tells if move is to the right or left
	 * @param position Position of list
	 */
	private void generateDismissAnimate(final View view, final boolean swap,
                                        final boolean swapRight, final int position) {
		int moveTo = 0;
		if (mOpened.get(position)) {
			if (!swap) {
				moveTo = mOpenedRight.get(position) ?
                        (int) (mViewWidth - mRightOffset) : (int) (-mViewWidth + mLeftOffset);
			}
		} else {
			if (swap) {
				moveTo = swapRight ?
                        (int) (mViewWidth - mRightOffset) : (int) (-mViewWidth + mLeftOffset);
			}
		}

		int alpha = 1;
		if (swap) {
			++mDismissAnimationRefCount;
			alpha = 0;
		}

		animate(view)
		.translationX(moveTo)
		.alpha(alpha)
		.setDuration(mAnimationTime)
		.setListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				if (swap) {
					closeOpenedItems();
					performDismiss(view, position);
				}
			}
		});

	}

	/**
	 * Create reveal animation
	 * @param view affected view
	 * @param swap If will change state. If "false" returns to the original position
	 * @param swapRight If swap is true, this parameter tells if movement is toward right or left
	 * @param position list position
	 */
	private void generateRevealAnimate(final View view, final boolean swap, final boolean swapRight, final int position) {
		int moveTo = 0;
		mViewWidth = mSwipeListView.getWidth();
		if (mOpened.get(position)) {
			if (!swap) {
				moveTo = mOpenedRight.get(position) ? (int) (mViewWidth - mRightOffset) : (int) (-mViewWidth + mLeftOffset);
			}
		} else {
			if (swap) {
				moveTo = swapRight ? (int) (mViewWidth - mRightOffset) : (int) (-mViewWidth + mLeftOffset);
			}
		}

		animate(view)
		.translationX(moveTo)
		.setDuration(mAnimationTime)
		.setListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				mSwipeListView.resetScrolling();
				if (swap) {
					boolean aux = !mOpened.get(position);
                    mOpened.set(position, aux);
					if (aux) {
						mSwipeListView.onOpened(position, swapRight);
						mOpenedRight.set(position, swapRight);
					} else {
						mSwipeListView.onClosed(position, mOpenedRight.get(position));
						if(allItemsClosed() && 
								mSwipeListView.getCloseItemsListener() != null){
							mSwipeListView.getCloseItemsListener().onAllItemsClosed();
						}
					}
				}
			}
		});
	}

	/**
	 * Set enabled
	 * @param enabled
	 */
	public void setEnabled(boolean enabled) {
        mPaused = !enabled;
	}

	/**
	 * Return ScrollListener for ListView
	 * @return OnScrollListener
	 */
	public AbsListView.OnScrollListener makeScrollListener() {
		return new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                scrollChange(scrollState);
			}

			@Override
			public void onScroll(AbsListView absListView, int i, int i1, int i2) {
			}
		};
	}

    protected void scrollChange(int scrollState) {
        setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
        if (mSwipeClosesAllItemsWhenListMoves && scrollState ==
                AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            closeOpenedItems();
        }
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mListViewMoving = true;
            setEnabled(false);
        }
        if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_FLING &&
                scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mListViewMoving = false;
            mDownPosition = ListView.INVALID_POSITION;
            mSwipeListView.resetScrolling();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    setEnabled(true);
                }
            }, 500);
        }
    }

	/**
	 * Close all opened items
	 */
	public void closeOpenedItems() {
		if (mOpened != null) {
			int start = mSwipeListView.getFirstVisiblePosition();
			int end = mSwipeListView.getLastVisiblePosition();
            try {
                for (int i = start; i <= end; i++) {
                    if (mOpened.get(i)) {
                        closeAnimate(mSwipeListView.getChildAt(i - start).findViewById(mSwipeFrontView), i);
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                /*
                 * Header views and footer views if visible,
                 * will case an index out of bounds exception
                 */
            }

		}
	}

	/**
	 * @see View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
	 */
	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
		if(!isSwipeEnabled()) {
			return false;
		}

		mViewWidth = mSwipeListView.getWidth();

		switch (motionEvent.getAction()) {
		case MotionEvent.ACTION_DOWN: {
            return actionDown(view, motionEvent);
        }

		case MotionEvent.ACTION_UP: {
            actionUp(motionEvent);
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

	/**
	 * Moves the view
	 * @param deltaX delta
	 */
	public void move(float deltaX) {
		mSwipeListView.onMove(mDownPosition, deltaX);
		if (mSwipeCurrentAction == SwipeListView.SWIPE_ACTION_DISMISS) {
			setTranslationX(mParentView, deltaX);
			setAlpha(mParentView, Math.max(0f, Math.min(1f,
					1f - 2f * Math.abs(deltaX) / mViewWidth)));
		} else {
			setTranslationX(mFrontView, deltaX);
		}
	}

	/**
	 * Class that saves pending dismiss data
	 */
	class PendingDismissData implements Comparable<PendingDismissData> {
		public int mPosition;
		public View mView;

		public PendingDismissData(int position, View view) {
			mPosition = position;
			mView = view;
		}

		@Override
		public int compareTo(PendingDismissData other) {
			// Sort by descending position
			return other.mPosition - mPosition;
		}
	}

	/**
	 * Perform dismiss action
	 * @param dismissView View
	 * @param dismissPosition Position of list
	 */
	public void performDismiss(final View dismissView, final int dismissPosition) {
		final ViewGroup.LayoutParams lp = dismissView.getLayoutParams();
		final int originalHeight = dismissView.getMeasuredHeight();

		ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1).setDuration(mAnimationTime);

		animator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				--mDismissAnimationRefCount;
				if (mDismissAnimationRefCount == 0) {
					// No active animations, process all pending dismisses.
					// Sort by descending position
					Collections.sort(mPendingDismisses);

					int[] dismissPositions = new int[mPendingDismisses.size()];
					for (int i = mPendingDismisses.size() - 1; i >= 0; i--) {
						dismissPositions[i] = mPendingDismisses.get(i).mPosition;
					}
					mSwipeListView.onDismiss(dismissPositions);

					ViewGroup.LayoutParams lp;
					for (PendingDismissData pendingDismiss : mPendingDismisses) {
						// Reset view presentation
						setAlpha(pendingDismiss.mView, 1f);
						setTranslationX(pendingDismiss.mView, 0);
						lp = pendingDismiss.mView.getLayoutParams();
						lp.height = originalHeight;
						pendingDismiss.mView.setLayoutParams(lp);
					}

					mPendingDismisses.clear();
				}
			}
		});

		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator valueAnimator) {
				lp.height = (Integer) valueAnimator.getAnimatedValue();
				dismissView.setLayoutParams(lp);
			}
		});

		mPendingDismisses.add(new PendingDismissData(dismissPosition, dismissView));
		animator.start();
	}

    protected boolean actionDown(View view, MotionEvent motionEvent) {
        if (mPaused) {
            return false;
        }
        mSwipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;

        int childCount = mSwipeListView.getChildCount();
        int[] listViewCoords = new int[2];
        mSwipeListView.getLocationOnScreen(listViewCoords);
        int x = (int) motionEvent.getRawX() - listViewCoords[0];
        int y = (int) motionEvent.getRawY() - listViewCoords[1];
        View child;
        for (int i = 0; i < childCount; i++) {
            child = mSwipeListView.getChildAt(i);
            child.getHitRect(mRect);

            int childPosition = mSwipeListView.getPositionForView(child);

            // dont allow swiping if this is on the header or footer or IGNORE_ITEM_VIEW_TYPE or enabled is false on the adapter
            boolean allowSwipe = mSwipeListView.getAdapter().isEnabled(childPosition) && mSwipeListView.getAdapter().getItemViewType(childPosition) >= 0;

            if (allowSwipe && mRect.contains(x, y)) {
                setParentView(child);
                setFrontView(child.findViewById(mSwipeFrontView));

                mDownX = motionEvent.getRawX();
                mDownPosition = childPosition;

                mFrontView.setClickable(!mOpened.get(mDownPosition));
                mFrontView.setLongClickable(!mOpened.get(mDownPosition));

                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(motionEvent);
                if (mSwipeBackView > 0) {
                    setBackView(child.findViewById(mSwipeBackView));
                }
                break;
            }
        }
        view.onTouchEvent(motionEvent);
        return true;
    }

    protected void actionUp(MotionEvent motionEvent) {
        if (mVelocityTracker == null || !mSwiping) {
            return;
        }

        float deltaX = motionEvent.getRawX() - mDownX;
        mVelocityTracker.addMovement(motionEvent);
        mVelocityTracker.computeCurrentVelocity(1000);
        float velocityX = Math.abs(mVelocityTracker.getXVelocity());
        if (!mOpened.get(mDownPosition)) {
            if (mSwipeMode == SwipeListView.SWIPE_MODE_LEFT && mVelocityTracker.getXVelocity() > 0) {
                velocityX = 0;
            }
            if (mSwipeMode == SwipeListView.SWIPE_MODE_RIGHT && mVelocityTracker.getXVelocity() < 0) {
                velocityX = 0;
            }
        }
        float velocityY = Math.abs(mVelocityTracker.getYVelocity());
        boolean swap = false;
        boolean swapRight = false;
        if (mMinFlingVelocity <= velocityX && velocityX <= mMaxFlingVelocity && velocityY < velocityX) {
            swapRight = mVelocityTracker.getXVelocity() > 0;
            swap = !(mOpened.get(mDownPosition) && mOpenedRight.get(mDownPosition) && swapRight) &&
                    !(mOpened.get(mDownPosition) && !mOpenedRight.get(mDownPosition) && !swapRight);
        } else if (Math.abs(deltaX) > mViewWidth / 2) {
            swap = true;
            swapRight = deltaX > 0;
        }
        generateAnimate(mFrontView, swap, swapRight, mDownPosition);

        mVelocityTracker.recycle();
        mVelocityTracker = null;
        mDownX = 0;
        // change clickable front view
        if (swap) {
            mFrontView.setClickable(mOpened.get(mDownPosition));
            mFrontView.setLongClickable(mOpened.get(mDownPosition));
        }
        mFrontView = null;
        mDownPosition = ListView.INVALID_POSITION;
        mSwiping = false;
    }

}
