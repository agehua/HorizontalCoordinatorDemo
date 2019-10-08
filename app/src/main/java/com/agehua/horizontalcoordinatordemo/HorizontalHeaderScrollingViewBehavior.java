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
import android.graphics.Rect;
import android.support.v4.math.MathUtils;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 */
abstract class HorizontalHeaderScrollingViewBehavior extends ViewOffsetBehavior<View> {

    final Rect mTempRect1 = new Rect();
    final Rect mTempRect2 = new Rect();

    private int mHorizontalLayoutGap = 0;
    private int mOverlayLeft;

    public HorizontalHeaderScrollingViewBehavior() {}

    public HorizontalHeaderScrollingViewBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onMeasureChild(HorizontalCoordinatorLayout parent, View child,
                                  int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec,
                                  int heightUsed) {
        final int childLpWidth = child.getLayoutParams().width;
        if (childLpWidth == ViewGroup.LayoutParams.MATCH_PARENT
                || childLpWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
            // If the menu's height is set to match_parent/wrap_content then measure it
            // with the maximum visible height

            final List<View> dependencies = parent.getDependencies(child);
            final View header = findFirstDependency(dependencies);
            if (header != null) {
                if (ViewCompat.getFitsSystemWindows(header)
                        && !ViewCompat.getFitsSystemWindows(child)) {
                    // If the header is fitting system windows then we need to also,
                    // otherwise we'll get CoL's compatible measuring
                    ViewCompat.setFitsSystemWindows(child, true);

                    if (ViewCompat.getFitsSystemWindows(child)) {
                        // If the set succeeded, trigger a new layout and return true
                        child.requestLayout();
                        return true;
                    }
                }

                int availableWidth = View.MeasureSpec.getSize(parentWidthMeasureSpec);
                if (availableWidth == 0) {
                    // If the measure spec doesn't specify a size, use the current height
                    availableWidth = parent.getWidth();
                }

                final int width = availableWidth - header.getMeasuredWidth()
                        + getScrollRange(header);
                final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width,
                        childLpWidth == ViewGroup.LayoutParams.MATCH_PARENT
                                ? View.MeasureSpec.EXACTLY
                                : View.MeasureSpec.AT_MOST);

                // Now measure the scrolling view with the correct height
                parent.onMeasureChild(child, widthMeasureSpec,
                        widthUsed, parentHeightMeasureSpec, heightUsed);

                return true;
            }
        }
        return false;
    }

    @Override
    protected void layoutChild(final HorizontalCoordinatorLayout parent, final View child,
                               final int layoutDirection) {
        final List<View> dependencies = parent.getDependencies(child);
        final View header = findFirstDependency(dependencies);

        if (header != null) {
            final HorizontalCoordinatorLayout.LayoutParams lp =
                    (HorizontalCoordinatorLayout.LayoutParams) child.getLayoutParams();
            final Rect available = mTempRect1;
            if (layoutDirection == 0) {// 水平
                available.set(header.getRight() + lp.leftMargin,
                        parent.getTop() + lp.topMargin,
                        parent.getWidth() - parent.getPaddingRight()- lp.rightMargin,
                        parent.getHeight() + header.getBottom() - parent.getPaddingBottom() - lp.bottomMargin);
            } else {
                available.set(parent.getPaddingLeft() + lp.leftMargin,
                        header.getBottom() + lp.topMargin,
                        parent.getWidth() - parent.getPaddingRight() - lp.rightMargin,
                        parent.getHeight() + header.getBottom()
                                - parent.getPaddingBottom() - lp.bottomMargin);
            }

            final WindowInsetsCompat parentInsets = parent.getLastWindowInsets();
            if (parentInsets != null && ViewCompat.getFitsSystemWindows(parent)
                    && !ViewCompat.getFitsSystemWindows(child)) {
                // If we're set to handle insets but this child isn't, then it has been measured as
                // if there are no insets. We need to lay it out to match horizontally.
                // Top and bottom and already handled in the logic above
                available.left += parentInsets.getSystemWindowInsetLeft();
                available.right -= parentInsets.getSystemWindowInsetRight();
            }

            final Rect out = mTempRect2;
            GravityCompat.apply(resolveGravity(lp.gravity), child.getMeasuredWidth(),
                    child.getMeasuredHeight(), available, out, layoutDirection);

            final int overlap = getOverlapPixelsForOffset(header);

            child.layout(out.left- overlap, out.top , out.right - overlap, out.bottom );
            mHorizontalLayoutGap = out.left - header.getRight();
        } else {
            // If we don't have a dependency, let super handle it
            super.layoutChild(parent, child, layoutDirection);
            mHorizontalLayoutGap = 0;
        }
    }

    float getOverlapRatioForOffset(final View header) {
        return 1f;
    }

    final int getOverlapPixelsForOffset(final View header) {
        return mOverlayLeft == 0 ? 0 : MathUtils.clamp(
                (int) (getOverlapRatioForOffset(header) * mOverlayLeft), 0, mOverlayLeft);
    }

    private static int resolveGravity(int gravity) {
        return gravity == Gravity.NO_GRAVITY ? GravityCompat.START | Gravity.TOP : gravity;
    }

    abstract View findFirstDependency(List<View> views);

    int getScrollRange(View v) {
        return v.getMeasuredHeight();
    }

    /**
     * The gap between the top of the scrolling view and the bottom of the header layout in pixels.
     */
    final int getHorizontalLayoutGap() {
        return mHorizontalLayoutGap;
    }

    /**
     *
     * @param overlayTop the distance in px
     */
    public final void setOverlayLeft(int overlayTop) {
        mOverlayLeft = overlayTop;
    }

    /**
     */
    public final int getOverlayLeft() {
        return mOverlayLeft;
    }
}