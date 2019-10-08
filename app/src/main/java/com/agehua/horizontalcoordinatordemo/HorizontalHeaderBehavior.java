/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.agehua.horizontalcoordinatordemo;

import android.content.Context;
import android.support.v4.math.MathUtils;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

/**
 * See {@link HeaderScrollingViewBehavior}.
 * 水平方向支持触摸滑动
 * 该类主要用于View处理触摸事件以及触摸后的fling事件
 */
abstract class HorizontalHeaderBehavior<V extends View> extends ViewOffsetBehavior<V> {

    private static final int INVALID_POINTER = -1;

    private Runnable mFlingRunnable;
    OverScroller mScroller;

    private boolean mIsBeingDragged;
    private int mActivePointerId = INVALID_POINTER;
    private int mLastMotionX;
    private int mTouchSlop = -1;
    private VelocityTracker mVelocityTracker;

    public HorizontalHeaderBehavior() {}

    public HorizontalHeaderBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(HorizontalCoordinatorLayout parent, V child, MotionEvent ev) {
        if (mTouchSlop < 0) {
            mTouchSlop = ViewConfiguration.get(parent.getContext()).getScaledTouchSlop();
        }

        final int action = ev.getAction();

        // Shortcut since we're being dragged
        if (action == MotionEvent.ACTION_MOVE && mIsBeingDragged) {
            return true;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mIsBeingDragged = false;
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                if (canDragView(child) && parent.isPointInChildBounds(child, x, y)) {
                    mLastMotionX = x;
                    mActivePointerId = ev.getPointerId(0);
                    ensureVelocityTracker();
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    break;
                }

                final int x = (int) ev.getX(pointerIndex);
                final int xDiff = Math.abs(x - mLastMotionX);
                if (xDiff > mTouchSlop) {
                    mIsBeingDragged = true;
                    mLastMotionX = x;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            }
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(HorizontalCoordinatorLayout parent, V child, MotionEvent ev) {
        if (mTouchSlop < 0) {
            mTouchSlop = ViewConfiguration.get(parent.getContext()).getScaledTouchSlop();
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();

                if (parent.isPointInChildBounds(child, x, y) && canDragView(child)) {
                    mLastMotionX = x;
                    mActivePointerId = ev.getPointerId(0);
                    ensureVelocityTracker();
                } else {
                    return false;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    return false;
                }

                final int x = (int) ev.getX(activePointerIndex);
                int dx = mLastMotionX - x;

                if (!mIsBeingDragged && Math.abs(dx) > mTouchSlop) {
                    mIsBeingDragged = true;
                    if (dx > 0) {
                        dx -= mTouchSlop;
                    } else {
                        dx += mTouchSlop;
                    }
                }

                if (mIsBeingDragged) {
                    mLastMotionX = x;
                    // We're being dragged so scroll the ABL
                    scroll(parent, child, dx, getMaxDragOffset(child), 0);
                }
                break;
            }

            case MotionEvent.ACTION_UP:
                if (mVelocityTracker != null) {
                    mVelocityTracker.addMovement(ev);
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float xvel = mVelocityTracker.getXVelocity(mActivePointerId);
                    fling(parent, child, -getScrollRangeForDragFling(child), 0, xvel);
                }
                // $FALLTHROUGH
            case MotionEvent.ACTION_CANCEL: {
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
            }
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        return true;
    }

    int setHeaderLeftRightOffset(HorizontalCoordinatorLayout parent, V header, int newOffset) {
        return setHeaderLeftRightOffset(parent, header, newOffset,
                Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    int setHeaderLeftRightOffset(HorizontalCoordinatorLayout parent, V header, int newOffset,
                                 int minOffset, int maxOffset) {
        final int curOffset = getLeftAndRightOffset();
        int consumed = 0;

        if (minOffset != 0 && curOffset >= minOffset && curOffset <= maxOffset) {
            // If we have some scrolling range, and we're currently within the min and max
            // offsets, calculate a new offset
            newOffset = MathUtils.clamp(newOffset, minOffset, maxOffset);

            if (curOffset != newOffset) {
                setLeftAndRightOffset(newOffset);
                // Update how much dy we have consumed
                consumed = curOffset - newOffset;
            }
        }

        return consumed;
    }

    int getLeftRightOffsetForScrollingSibling() {
        return getLeftAndRightOffset();
    }

    final int scroll(HorizontalCoordinatorLayout horizontalCoordinatorLayout, V header,
                     int dx, int minOffset, int maxOffset) {
        return setHeaderLeftRightOffset(horizontalCoordinatorLayout, header,
                getLeftRightOffsetForScrollingSibling() - dx, minOffset, maxOffset);
    }

    final boolean fling(HorizontalCoordinatorLayout horizontalCoordinatorLayout, V layout, int minOffset,
                        int maxOffset, float velocityX) {
        if (mFlingRunnable != null) {
            layout.removeCallbacks(mFlingRunnable);
            mFlingRunnable = null;
        }

        if (mScroller == null) {
            mScroller = new OverScroller(layout.getContext());
        }

        mScroller.fling(
                getLeftAndRightOffset(), 0, // curr
                Math.round(velocityX),0, // velocity.
                minOffset, maxOffset,// x
                0, 0  // y
        );

        if (mScroller.computeScrollOffset()) {
            mFlingRunnable = new FlingRunnable(horizontalCoordinatorLayout, layout);
            ViewCompat.postOnAnimation(layout, mFlingRunnable);
            return true;
        } else {
            onFlingFinished(horizontalCoordinatorLayout, layout);
            return false;
        }
    }

    /**
     * Called when a fling has finished, or the fling was initiated but there wasn't enough
     * velocity to start it.
     */
    void onFlingFinished(HorizontalCoordinatorLayout parent, V layout) {
        // no-op
    }

    /**
     * Return true if the view can be dragged.
     */
    boolean canDragView(V view) {
        return false;
    }

    /**
     * Returns the maximum px offset when {@code view} is being dragged.
     */
    int getMaxDragOffset(V view) {
        return -view.getWidth();
    }

    int getScrollRangeForDragFling(V view) {
        return view.getWidth();
    }

    private void ensureVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private class FlingRunnable implements Runnable {
        private final HorizontalCoordinatorLayout mParent;
        private final V mLayout;

        FlingRunnable(HorizontalCoordinatorLayout parent, V layout) {
            mParent = parent;
            mLayout = layout;
        }

        @Override
        public void run() {
            if (mLayout != null && mScroller != null) {
                if (mScroller.computeScrollOffset()) {
                    setHeaderLeftRightOffset(mParent, mLayout, mScroller.getCurrX());
                    // Post ourselves so that we run on the next animation
                    ViewCompat.postOnAnimation(mLayout, this);
                } else {
                    onFlingFinished(mParent, mLayout);
                }
            }
        }
    }
}
