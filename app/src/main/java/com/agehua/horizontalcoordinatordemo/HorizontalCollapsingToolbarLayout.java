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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import android.support.v4.math.MathUtils;
import android.support.v4.util.ObjectsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

public class HorizontalCollapsingToolbarLayout extends FrameLayout {

    private static final int DEFAULT_SCRIM_ANIMATION_DURATION = 600;

    private boolean mRefreshToolbar = true;
    private int mToolbarId;
    private Toolbar mToolbar;
    private View mToolbarDirectChild;
    private View mDummyView;

    private boolean mCollapsingTitleEnabled;
    private boolean mDrawCollapsingTitle;

    Drawable mStatusBarScrim;
    private int mScrimAlpha;
    private boolean mScrimsAreShown;
    private ValueAnimator mScrimAnimator;
    private long mScrimAnimationDuration;
    private int mScrimVisibleHeightTrigger = -1;

    private HorizontalAppBarLayout.OnOffsetChangedListener mOnOffsetChangedListener;

    int mCurrentOffset;

    WindowInsetsCompat mLastInsets;

    public HorizontalCollapsingToolbarLayout(Context context) {
        this(context, null);
    }

    public HorizontalCollapsingToolbarLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalCollapsingToolbarLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CollapsingToolbarLayout, defStyleAttr,
                R.style.Widget_Design_CollapsingToolbar);

        mCollapsingTitleEnabled = a.getBoolean(
                R.styleable.CollapsingToolbarLayout_titleEnabled, true);
        mScrimVisibleHeightTrigger = a.getDimensionPixelSize(
                R.styleable.CollapsingToolbarLayout_scrimVisibleHeightTrigger, -1);

        mScrimAnimationDuration = a.getInt(
                R.styleable.CollapsingToolbarLayout_scrimAnimationDuration,
                DEFAULT_SCRIM_ANIMATION_DURATION);


        mToolbarId = a.getResourceId(R.styleable.CollapsingToolbarLayout_toolbarId, -1);

        a.recycle();

        setWillNotDraw(false);

        ViewCompat.setOnApplyWindowInsetsListener(this,
                new android.support.v4.view.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(View v,
                                                                  WindowInsetsCompat insets) {
                        return onWindowInsetChanged(insets);
                    }
                });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Add an OnOffsetChangedListener if possible
        final ViewParent parent = getParent();
        if (parent instanceof HorizontalAppBarLayout) {
            // Copy over from the ABL whether we should fit system windows
            ViewCompat.setFitsSystemWindows(this, ViewCompat.getFitsSystemWindows((View) parent));

            if (mOnOffsetChangedListener == null) {
                mOnOffsetChangedListener = new OffsetUpdateListener();
            }
            ((HorizontalAppBarLayout) parent).addOnOffsetChangedListener(mOnOffsetChangedListener);

            // We're attached, so lets request an inset dispatch
            // 状态栏只有一个，只能被一个View消耗掉，当调用requestApplyInsets 就会重新分配一次WindowInsets，
            // OnApplyWindowInsetsListener就会被回调
            ViewCompat.requestApplyInsets(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Remove our OnOffsetChangedListener if possible and it exists
        final ViewParent parent = getParent();
        if (mOnOffsetChangedListener != null && parent instanceof HorizontalAppBarLayout) {
            ((HorizontalAppBarLayout) parent).removeOnOffsetChangedListener(mOnOffsetChangedListener);
        }

        super.onDetachedFromWindow();
    }

    WindowInsetsCompat onWindowInsetChanged(final WindowInsetsCompat insets) {
        WindowInsetsCompat newInsets = null;

        if (ViewCompat.getFitsSystemWindows(this)) {
            // If we're set to fit system windows, keep the insets
            newInsets = insets;
        }

        // If our insets have changed, keep them and invalidate the scroll ranges...
        if (!ObjectsCompat.equals(mLastInsets, newInsets)) {
            mLastInsets = newInsets;
            requestLayout();
        }

        // Consume the insets. This is done so that child views with fitSystemWindows=true do not
        // get the default padding functionality from View
        return insets.consumeSystemWindowInsets();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        // If we don't have a toolbar, the scrim will be not be drawn in drawChild() below.
        // Instead, we draw it here, before our collapsing text.
        ensureToolbar();

        // Now draw the status bar scrim
        if (mStatusBarScrim != null && mScrimAlpha > 0) {
            final int topInset = mLastInsets != null ? mLastInsets.getSystemWindowInsetLeft() : 0;
            if (topInset > 0) {
                mStatusBarScrim.setBounds(0, -mCurrentOffset, getWidth(),
                        topInset - mCurrentOffset);
                mStatusBarScrim.mutate().setAlpha(mScrimAlpha);
                mStatusBarScrim.draw(canvas);
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        // This is a little weird. Our scrim needs to be behind the Toolbar (if it is present),
        // but in front of any other children which are behind it. To do this we intercept the
        // drawChild() call, and draw our scrim just before the Toolbar is drawn
        boolean invalidated = false;

        return super.drawChild(canvas, child, drawingTime) || invalidated;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

    }

    private void ensureToolbar() {
        if (!mRefreshToolbar) {
            return;
        }

        // First clear out the current Toolbar
        mToolbar = null;
        mToolbarDirectChild = null;

        if (mToolbarId != -1) {
            // If we have an ID set, try and find it and it's direct parent to us
            mToolbar = findViewById(mToolbarId);
            if (mToolbar != null) {
                mToolbarDirectChild = findDirectChild(mToolbar);
            }
        }

        if (mToolbar == null) {
            // If we don't have an ID, or couldn't find a Toolbar with the correct ID, try and find
            // one from our direct children
            Toolbar toolbar = null;
            for (int i = 0, count = getChildCount(); i < count; i++) {
                final View child = getChildAt(i);
                if (child instanceof Toolbar) {
                    toolbar = (Toolbar) child;
                    break;
                }
            }
            mToolbar = toolbar;
        }

        updateDummyView();
        mRefreshToolbar = false;
    }

    private boolean isToolbarChild(View child) {
        return (mToolbarDirectChild == null || mToolbarDirectChild == this)
                ? child == mToolbar
                : child == mToolbarDirectChild;
    }

    /**
     * Returns the direct child of this layout, which itself is the ancestor of the
     * given view.
     */
    private View findDirectChild(final View descendant) {
        View directChild = descendant;
        for (ViewParent p = descendant.getParent(); p != this && p != null; p = p.getParent()) {
            if (p instanceof View) {
                directChild = (View) p;
            }
        }
        return directChild;
    }

    private void updateDummyView() {
        if (!mCollapsingTitleEnabled && mDummyView != null) {
            // If we have a dummy view and we have our title disabled, remove it from its parent
            final ViewParent parent = mDummyView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mDummyView);
            }
        }
        if (mCollapsingTitleEnabled && mToolbar != null) {
            if (mDummyView == null) {
                mDummyView = new View(getContext());
            }
            if (mDummyView.getParent() == null) {
                mToolbar.addView(mDummyView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ensureToolbar();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int mode = MeasureSpec.getMode(heightMeasureSpec);
        final int topInset = mLastInsets != null ? mLastInsets.getSystemWindowInsetLeft() : 0;
        if (mode == MeasureSpec.UNSPECIFIED && topInset > 0) {
            // If we have a top inset and we're set to wrap_content height we need to make sure
            // we add the top inset to our height, therefore we re-measure
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                    getMeasuredHeight() + topInset, MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mLastInsets != null) {
            // Shift down any views which are not set to fit system windows
            final int insetLeft = mLastInsets.getSystemWindowInsetLeft();
            for (int i = 0, z = getChildCount(); i < z; i++) {
                final View child = getChildAt(i);
                if (!ViewCompat.getFitsSystemWindows(child)) {
                    if (child.getLeft() < insetLeft) {
                        // If the child isn't set to fit system windows but is drawing within
                        // the inset offset it down
                        ViewCompat.offsetLeftAndRight(child, insetLeft);
                    }
                }
            }
        }

        // Update the collapsed bounds by getting it's transformed bounds
        if (mCollapsingTitleEnabled && mDummyView != null) {
            // We only draw the title if the dummy view is being displayed (Toolbar removes
            // views if there is no space)
            mDrawCollapsingTitle = ViewCompat.isAttachedToWindow(mDummyView)
                    && mDummyView.getVisibility() == VISIBLE;

            if (mDrawCollapsingTitle) {
                final boolean isRtl = ViewCompat.getLayoutDirection(this)
                        == ViewCompat.LAYOUT_DIRECTION_RTL;

                // Update the collapsed bounds
                final int maxOffset = getMaxOffsetForPinChild(
                        mToolbarDirectChild != null ? mToolbarDirectChild : mToolbar);
//                ViewGroupUtils.getDescendantRect(this, mDummyView, mTmpRect);
            }
        }

        // Update our child view offset helpers. This needs to be done after the title has been
        // setup, so that any Toolbars are in their original position
        for (int i = 0, z = getChildCount(); i < z; i++) {
            getViewOffsetHelper(getChildAt(i)).onViewLayout();
        }

        // Finally, set our minimum height to enable proper AppBarLayout collapsing
        if (mToolbar != null) {
            if (mToolbarDirectChild == null || mToolbarDirectChild == this) {
                setMinimumWidth(getWidthWithMargins(mToolbar));
            } else {
                setMinimumWidth(getWidthWithMargins(mToolbarDirectChild));
            }
        } else {
            for (int i = 0; i< getChildCount(); i++) {
                View view = getChildAt(i);
                LayoutParams lp = (LayoutParams) view.getLayoutParams();
                if (lp.mCollapseMode == LayoutParams.COLLAPSE_MODE_PIN) {
                    setMinimumWidth(getWidthWithMargins(view));
                }
            }
        }
    }

    private static int getWidthWithMargins(@NonNull final View view) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof MarginLayoutParams) {
            final MarginLayoutParams mlp = (MarginLayoutParams) lp;
            return view.getWidth() + mlp.leftMargin + mlp.rightMargin;
        }
        return view.getWidth();
    }

    static ViewOffsetHelper getViewOffsetHelper(View view) {
        ViewOffsetHelper offsetHelper = (ViewOffsetHelper) view.getTag(R.id.view_offset_helper);
        if (offsetHelper == null) {
            offsetHelper = new ViewOffsetHelper(view);
            view.setTag(R.id.view_offset_helper, offsetHelper);
        }
        return offsetHelper;
    }



    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

//        final int[] state = getDrawableState();
//        boolean changed = false;
//
//        Drawable d = mStatusBarScrim;
//        if (d != null && d.isStateful()) {
//            changed |= d.setState(state);
//        }
//        d = mContentScrim;
//        if (d != null && d.isStateful()) {
//            changed |= d.setState(state);
//        }
//        if (changed) {
//            invalidate();
//        }
    }

//    @Override
//    protected boolean verifyDrawable(Drawable who) {
//        return super.verifyDrawable(who) || who == mContentScrim || who == mStatusBarScrim;
//    }

//    @Override
//    public void setVisibility(int visibility) {
//        super.setVisibility(visibility);
//
//        final boolean visible = visibility == VISIBLE;
//        if (mStatusBarScrim != null && mStatusBarScrim.isVisible() != visible) {
//            mStatusBarScrim.setVisible(visible, false);
//        }
//        if (mContentScrim != null && mContentScrim.isVisible() != visible) {
//            mContentScrim.setVisible(visible, false);
//        }
//    }

    /**
     * Set the duration used for scrim visibility animations.
     *
     * @param duration the duration to use in milliseconds
     *
     * @attr ref android.support.design.R.styleable#CollapsingToolbarLayout_scrimAnimationDuration
     */
    public void setScrimAnimationDuration(@IntRange(from = 0) final long duration) {
        mScrimAnimationDuration = duration;
    }

    /**
     * Returns the duration in milliseconds used for scrim visibility animations.
     */
    public long getScrimAnimationDuration() {
        return mScrimAnimationDuration;
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    public FrameLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected FrameLayout.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public static class LayoutParams extends FrameLayout.LayoutParams {

        private static final float DEFAULT_PARALLAX_MULTIPLIER = 0.5f;

        /** @hide */
        @RestrictTo(LIBRARY_GROUP)
        @IntDef({
                COLLAPSE_MODE_OFF,
                COLLAPSE_MODE_PIN,
                COLLAPSE_MODE_PARALLAX
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface CollapseMode {
        }

        /**
         * The view will act as normal with no collapsing behavior.
         */
        public static final int COLLAPSE_MODE_OFF = 0;

        /**
         * The view will pin in place until it reaches the bottom of the
         * {@link HorizontalCollapsingToolbarLayout}.
         */
        public static final int COLLAPSE_MODE_PIN = 1;

        /**
         * The view will scroll in a parallax fashion. See {@link #setParallaxMultiplier(float)}
         * to change the multiplier used.
         */
        public static final int COLLAPSE_MODE_PARALLAX = 2;

        int mCollapseMode = COLLAPSE_MODE_OFF;
        float mParallaxMult = DEFAULT_PARALLAX_MULTIPLIER;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs,
                    R.styleable.CollapsingToolbarLayout_Layout);
            mCollapseMode = a.getInt(
                    R.styleable.CollapsingToolbarLayout_Layout_layout_collapseMode,
                    COLLAPSE_MODE_OFF);
            setParallaxMultiplier(a.getFloat(
                    R.styleable.CollapsingToolbarLayout_Layout_layout_collapseParallaxMultiplier,
                    DEFAULT_PARALLAX_MULTIPLIER));
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height, gravity);
        }

        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        @RequiresApi(19)
        public LayoutParams(FrameLayout.LayoutParams source) {
            // The copy constructor called here only exists on API 19+.
            super(source);
        }

        /**
         * Set the collapse mode.
         *
         * @param collapseMode one of {@link #COLLAPSE_MODE_OFF}, {@link #COLLAPSE_MODE_PIN}
         *                     or {@link #COLLAPSE_MODE_PARALLAX}.
         */
        public void setCollapseMode(@CollapseMode int collapseMode) {
            mCollapseMode = collapseMode;
        }

        /**
         * Returns the requested collapse mode.
         *
         * @return the current mode. One of {@link #COLLAPSE_MODE_OFF}, {@link #COLLAPSE_MODE_PIN}
         * or {@link #COLLAPSE_MODE_PARALLAX}.
         */
        @CollapseMode
        public int getCollapseMode() {
            return mCollapseMode;
        }

        /**
         * Set the parallax scroll multiplier used in conjunction with
         * {@link #COLLAPSE_MODE_PARALLAX}. A value of {@code 0.0} indicates no movement at all,
         * {@code 1.0f} indicates normal scroll movement.
         *
         * @param multiplier the multiplier.
         * @see #getParallaxMultiplier()
         */
        public void setParallaxMultiplier(float multiplier) {
            mParallaxMult = multiplier;
        }

        /**
         * Returns the parallax scroll multiplier used in conjunction with
         * {@link #COLLAPSE_MODE_PARALLAX}.
         *
         * @see #setParallaxMultiplier(float)
         */
        public float getParallaxMultiplier() {
            return mParallaxMult;
        }
    }

    final int getMaxOffsetForPinChild(View child) {
        final ViewOffsetHelper offsetHelper = getViewOffsetHelper(child);
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return getWidth()
                - offsetHelper.getLayoutLeft()
                - child.getWidth()
                - lp.rightMargin;
    }


    private class OffsetUpdateListener implements HorizontalAppBarLayout.OnOffsetChangedListener {
        OffsetUpdateListener() {
        }

        @Override
        public void onOffsetChanged(HorizontalAppBarLayout layout, int verticalOffset) {
            mCurrentOffset = verticalOffset;

            final int insetLeft = mLastInsets != null ? mLastInsets.getSystemWindowInsetLeft() : 0;

            for (int i = 0, z = getChildCount(); i < z; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final ViewOffsetHelper offsetHelper = getViewOffsetHelper(child);

                switch (lp.mCollapseMode) {
                    case LayoutParams.COLLAPSE_MODE_PIN:
                        offsetHelper.setLeftAndRightOffset(MathUtils.clamp(
                                -verticalOffset, 0, getMaxOffsetForPinChild(child)));
                        break;
                    case LayoutParams.COLLAPSE_MODE_PARALLAX:
                        offsetHelper.setLeftAndRightOffset(
                                Math.round(-verticalOffset * lp.mParallaxMult));
                        break;
                }
            }
        }
    }
}
