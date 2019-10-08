package com.agehua.horizontalcoordinatordemo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.v4.math.MathUtils;
import android.support.v4.util.ObjectsCompat;
import android.support.v4.view.AbsSavedState;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

@HorizontalCoordinatorLayout.DefaultBehavior(HorizontalAppBarLayout.Behavior.class)
public class HorizontalAppBarLayout extends LinearLayout {

    static final int PENDING_ACTION_NONE = 0x0;
    static final int PENDING_ACTION_EXPANDED = 0x1;
    static final int PENDING_ACTION_COLLAPSED = 0x2;
    static final int PENDING_ACTION_ANIMATE_ENABLED = 0x4;
    static final int PENDING_ACTION_FORCE = 0x8;

    public interface OnOffsetChangedListener {
        void onOffsetChanged(HorizontalAppBarLayout appBarLayout, int verticalOffset);
    }

    private static final int INVALID_SCROLL_RANGE = -1;

    private int mTotalScrollRange = INVALID_SCROLL_RANGE;
    private int mDownPreScrollRange = INVALID_SCROLL_RANGE;
    private int mDownScrollRange = INVALID_SCROLL_RANGE;

    private boolean mHaveChildWithInterpolator;

    private int mPendingAction = PENDING_ACTION_NONE;

    private WindowInsetsCompat mLastInsets;

    private List<OnOffsetChangedListener> mListeners;

    private boolean mCollapsible;
    private boolean mCollapsed;

    private int[] mTmpStatesArray;

    public HorizontalAppBarLayout(Context context) {
        this(context, null);
    }

    public HorizontalAppBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppBarLayout,
                0, R.style.Widget_Design_AppBarLayout);
        ViewCompat.setBackground(this, a.getDrawable(R.styleable.AppBarLayout_android_background));
        if (a.hasValue(R.styleable.AppBarLayout_expanded)) {
            setExpanded(a.getBoolean(R.styleable.AppBarLayout_expanded, false), false, false);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            // In O+, we have these values set in the style. Since there is no defStyleAttr for
            // AppBarLayout at the AppCompat level, check for these attributes here.
            if (a.hasValue(R.styleable.AppBarLayout_android_keyboardNavigationCluster)) {
                this.setKeyboardNavigationCluster(a.getBoolean(
                        R.styleable.AppBarLayout_android_keyboardNavigationCluster, false));
            }
            if (a.hasValue(R.styleable.AppBarLayout_android_touchscreenBlocksFocus)) {
                this.setTouchscreenBlocksFocus(a.getBoolean(
                        R.styleable.AppBarLayout_android_touchscreenBlocksFocus, false));
            }
        }
        a.recycle();

        ViewCompat.setOnApplyWindowInsetsListener(this,
                new android.support.v4.view.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(View v,
                                                                  WindowInsetsCompat insets) {
                        return onWindowInsetChanged(insets);
                    }
                });
    }

    /**
     * Add a listener that will be called when the offset of this {@link HorizontalAppBarLayout} changes.
     *
     * @param listener The listener that will be called when the offset changes.]
     *
     * @see #removeOnOffsetChangedListener(HorizontalAppBarLayout.OnOffsetChangedListener)
     */
    public void addOnOffsetChangedListener(HorizontalAppBarLayout.OnOffsetChangedListener listener) {
        if (mListeners == null) {
            mListeners = new ArrayList<>();
        }
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Remove the previously added {@link HorizontalAppBarLayout.OnOffsetChangedListener}.
     *
     * @param listener the listener to remove.
     */
    public void removeOnOffsetChangedListener(HorizontalAppBarLayout.OnOffsetChangedListener listener) {
        if (mListeners != null && listener != null) {
            mListeners.remove(listener);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        invalidateScrollRanges();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        invalidateScrollRanges();

        mHaveChildWithInterpolator = false;
        for (int i = 0, z = getChildCount(); i < z; i++) {
            final View child = getChildAt(i);
            final HorizontalAppBarLayout.LayoutParams
                    childLp = (HorizontalAppBarLayout.LayoutParams) child.getLayoutParams();
            final Interpolator interpolator = childLp.getScrollInterpolator();

            if (interpolator != null) {
                mHaveChildWithInterpolator = true;
                break;
            }
        }

        updateCollapsible();

//        for (int i = 0, z = getChildCount(); i < z; i++) {
//            getViewOffsetHelper(getChildAt(i)).onViewLayout();
//        }
    }

    private void updateCollapsible() {
        boolean haveCollapsibleChild = false;
        for (int i = 0, z = getChildCount(); i < z; i++) {
            if (((HorizontalAppBarLayout.LayoutParams) getChildAt(i).getLayoutParams()).isCollapsible()) {
                haveCollapsibleChild = true;
                break;
            }
        }
        setCollapsibleState(haveCollapsibleChild);
    }

    private void invalidateScrollRanges() {
        // Invalidate the scroll ranges
        mTotalScrollRange = INVALID_SCROLL_RANGE;
        mDownPreScrollRange = INVALID_SCROLL_RANGE;
        mDownScrollRange = INVALID_SCROLL_RANGE;
    }

    @Override
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL) {
            throw new IllegalArgumentException("AppBarLayout is always vertical and does"
                    + " not support horizontal orientation");
        }
        super.setOrientation(orientation);
    }

    /**
     * Sets whether this {@link HorizontalAppBarLayout} is expanded or not, animating if it has already
     * been laid out.
     *
     * <p>As with {@link HorizontalAppBarLayout}'s scrolling, this method relies on this layout being a
     * direct child of a {@link HorizontalCoordinatorLayout}.</p>
     *
     * @param expanded true if the layout should be fully expanded, false if it should
     *                 be fully collapsed
     *
     * @attr ref android.support.design.R.styleable#AppBarLayout_expanded
     */
    public void setExpanded(boolean expanded) {
        setExpanded(expanded, ViewCompat.isLaidOut(this));
    }

    /**
     * Sets whether this {@link HorizontalAppBarLayout} is expanded or not.
     *
     * <p>As with {@link HorizontalAppBarLayout}'s scrolling, this method relies on this layout being a
     * direct child of a {@link HorizontalCoordinatorLayout}.</p>
     *
     * @param expanded true if the layout should be fully expanded, false if it should
     *                 be fully collapsed
     * @param animate Whether to animate to the new state
     *
     * @attr ref android.support.design.R.styleable#AppBarLayout_expanded
     */
    public void setExpanded(boolean expanded, boolean animate) {
        setExpanded(expanded, animate, true);
    }

    private void setExpanded(boolean expanded, boolean animate, boolean force) {
        mPendingAction = (expanded ? PENDING_ACTION_EXPANDED : PENDING_ACTION_COLLAPSED)
                | (animate ? PENDING_ACTION_ANIMATE_ENABLED : 0)
                | (force ? PENDING_ACTION_FORCE : 0);
        requestLayout();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof HorizontalAppBarLayout.LayoutParams;
    }

    @Override
    protected HorizontalAppBarLayout.LayoutParams generateDefaultLayoutParams() {
        return new HorizontalAppBarLayout.LayoutParams(
                HorizontalAppBarLayout.LayoutParams.MATCH_PARENT, HorizontalAppBarLayout.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public HorizontalAppBarLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new HorizontalAppBarLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected HorizontalAppBarLayout.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (Build.VERSION.SDK_INT >= 19 && p instanceof LinearLayout.LayoutParams) {
            return new HorizontalAppBarLayout.LayoutParams((LinearLayout.LayoutParams) p);
        } else if (p instanceof MarginLayoutParams) {
            return new HorizontalAppBarLayout.LayoutParams((MarginLayoutParams) p);
        }
        return new HorizontalAppBarLayout.LayoutParams(p);
    }

    boolean hasChildWithInterpolator() {
        return mHaveChildWithInterpolator;
    }

    /**
     * Returns the scroll range of all children.
     *
     * @return the scroll range in px
     */
    public final int getTotalScrollRange() {
        if (mTotalScrollRange != INVALID_SCROLL_RANGE) {
            return mTotalScrollRange;
        }

        int range = 0;
        for (int i = 0, z = getChildCount(); i < z; i++) {
            final View child = getChildAt(i);
            final HorizontalAppBarLayout.LayoutParams
                    lp = (HorizontalAppBarLayout.LayoutParams) child.getLayoutParams();
            final int childWidth = child.getMeasuredWidth();
            final int flags = lp.mScrollFlags;

            if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL) != 0) {
                // We're set to scroll so add the child's height
                range += childWidth + lp.leftMargin + lp.rightMargin;

                if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED) != 0) {
                    // For a collapsing scroll, we to take the collapsed height into account.
                    // We also break straight away since later views can't scroll beneath
                    // us
                    range -= ViewCompat.getMinimumWidth(child);
                    break;
                }
            } else {
                // As soon as a view doesn't have the scroll flag, we end the range calculation.
                // This is because views below can not scroll under a fixed view.
                break;
            }
        }
        return mTotalScrollRange = Math.max(0, range - getLeftInset());
    }

    boolean hasScrollableChildren() {
        return getTotalScrollRange() != 0;
    }

    /**
     * Return the scroll range when scrolling up from a nested pre-scroll.
     */
    int getUpNestedPreScrollRange() {
        return getTotalScrollRange();
    }

    /**
     * Return the scroll range when scrolling down from a nested pre-scroll.
     */
    int getDownNestedPreScrollRange() {
        if (mDownPreScrollRange != INVALID_SCROLL_RANGE) {
            // If we already have a valid value, return it
            return mDownPreScrollRange;
        }

        int range = 0;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            final HorizontalAppBarLayout.LayoutParams
                    lp = (HorizontalAppBarLayout.LayoutParams) child.getLayoutParams();
            final int childWidth = child.getMeasuredWidth();
            final int flags = lp.mScrollFlags;

            if ((flags & HorizontalAppBarLayout.LayoutParams.FLAG_QUICK_RETURN) == HorizontalAppBarLayout.LayoutParams.FLAG_QUICK_RETURN) {
                // First take the margin into account
                range += lp.leftMargin + lp.rightMargin;
                // The view has the quick return flag combination...
                if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED) != 0) {
                    // If they're set to enter collapsed, use the minimum height
                    range += ViewCompat.getMinimumWidth(child);
                } else if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED) != 0) {
                    // Only enter by the amount of the collapsed height
                    range += childWidth - ViewCompat.getMinimumWidth(child);
                } else {
                    // Else use the full Width (minus the top inset)
                    range += childWidth - getLeftInset();
                }
            } else if (range > 0) {
                // If we've hit an non-quick return scrollable view, and we've already hit a
                // quick return view, return now
                break;
            }
        }
        return mDownPreScrollRange = Math.max(0, range);
    }

    /**
     * Return the scroll range when scrolling down from a nested scroll.
     */
    int getDownNestedScrollRange() {
        if (mDownScrollRange != INVALID_SCROLL_RANGE) {
            // If we already have a valid value, return it
            return mDownScrollRange;
        }

        int range = 0;
        for (int i = 0, z = getChildCount(); i < z; i++) {
            final View child = getChildAt(i);
            final HorizontalAppBarLayout.LayoutParams
                    lp = (HorizontalAppBarLayout.LayoutParams) child.getLayoutParams();
            int childWidth = child.getMeasuredWidth();
            childWidth += lp.leftMargin + lp.rightMargin;

            final int flags = lp.mScrollFlags;

            if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL) != 0) {
                // We're set to scroll so add the child's Width
                range += childWidth;

                if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED) != 0) {
                    // For a collapsing exit scroll, we to take the collapsed Width into account.
                    // We also break the range straight away since later views can't scroll
                    // beneath us
                    range -= ViewCompat.getMinimumWidth(child) + getLeftInset();
                    break;
                }
            } else {
                // As soon as a view doesn't have the scroll flag, we end the range calculation.
                // This is because views below can not scroll under a fixed view.
                break;
            }
        }
        return mDownScrollRange = Math.max(0, range);
    }

    void dispatchOffsetUpdates(int offset) {
        // Iterate backwards through the list so that most recently added listeners
        // get the first chance to decide
//        if (null == mChildOffsetListener) {
//            mChildOffsetListener = new OffsetUpdateListener();
//            addOnOffsetChangedListener(mChildOffsetListener);
//        }
        if (mListeners != null) {
            for (int i = 0, z = mListeners.size(); i < z; i++) {
                final HorizontalAppBarLayout.OnOffsetChangedListener listener = mListeners.get(i);
                if (listener != null) {
                    listener.onOffsetChanged(this, offset);
                }
            }
        }
    }

    final int getMinimumWidthForVisibleOverlappingContent() {
        final int topInset = getLeftInset();
        final int minWidth = ViewCompat.getMinimumWidth(this);
        if (minWidth != 0) {
            // If this layout has a min Width, use it (doubled)
            return (minWidth * 2) + topInset;
        }

        // Otherwise, we'll use twice the min Width of our last child
        final int childCount = getChildCount();
        final int lastChildMinWidth = childCount >= 1
                ? ViewCompat.getMinimumWidth(getChildAt(childCount - 1)) : 0;
        if (lastChildMinWidth != 0) {
            return (lastChildMinWidth * 2) + topInset;
        }

        // If we reach here then we don't have a min height explicitly set. Instead we'll take a
        // guess at 1/3 of our Width being visible
        return getWidth() / 3;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (mTmpStatesArray == null) {
            // Note that we can't allocate this at the class level (in declaration) since
            // some paths in super View constructor are going to call this method before
            // that
            mTmpStatesArray = new int[2];
        }
        final int[] extraStates = mTmpStatesArray;
        final int[] states = super.onCreateDrawableState(extraSpace + extraStates.length);

        extraStates[0] = mCollapsible ? R.attr.state_collapsible : -R.attr.state_collapsible;
        extraStates[1] = mCollapsible && mCollapsed
                ? R.attr.state_collapsed : -R.attr.state_collapsed;

        return mergeDrawableStates(states, extraStates);
    }

    /**
     * Sets whether the AppBarLayout has collapsible children or not.
     *
     * @return true if the collapsible state changed
     */
    private boolean setCollapsibleState(boolean collapsible) {
        if (mCollapsible != collapsible) {
            mCollapsible = collapsible;
            refreshDrawableState();
            return true;
        }
        return false;
    }

    /**
     * Sets whether the AppBarLayout is in a collapsed state or not.
     *
     * @return true if the collapsed state changed
     */
    boolean setCollapsedState(boolean collapsed) {
        if (mCollapsed != collapsed) {
            mCollapsed = collapsed;
            refreshDrawableState();
            return true;
        }
        return false;
    }




    int getPendingAction() {
        return mPendingAction;
    }

    void resetPendingAction() {
        mPendingAction = PENDING_ACTION_NONE;
    }

    @VisibleForTesting
    final int getLeftInset() {
        return mLastInsets != null ? mLastInsets.getSystemWindowInsetLeft() : 0;
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
            invalidateScrollRanges();
        }

        return insets;
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {

        /** @hide */
        @RestrictTo(LIBRARY_GROUP)
        @IntDef(flag=true, value={
                SCROLL_FLAG_SCROLL,
                SCROLL_FLAG_EXIT_UNTIL_COLLAPSED,
                SCROLL_FLAG_ENTER_ALWAYS,
                SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED,
                SCROLL_FLAG_SNAP
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface ScrollFlags {}

        @IntDef({
                COLLAPSE_MODE_OFF,
                COLLAPSE_MODE_PIN,
                COLLAPSE_MODE_PARALLAX
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface CollapseMode {}

        public static final int COLLAPSE_MODE_OFF = 0;
        public static final int COLLAPSE_MODE_PIN = 1;
        public static final int COLLAPSE_MODE_PARALLAX = 2;

        int mCollapseMode = COLLAPSE_MODE_OFF;
        float mParallaxMult = 0.5f;

        ///////////////// 添加collapseMode支持 /////////////////
        public void setCollapseMode(@LayoutParams.CollapseMode int collapseMode) {
            mCollapseMode = collapseMode;
        }


        @LayoutParams.CollapseMode
        public int getCollapseMode() {
            return mCollapseMode;
        }


        public void setParallaxMultiplier(float multiplier) {
            mParallaxMult = multiplier;
        }

        public float getParallaxMultiplier() {
            return mParallaxMult;
        }
        /**
         * The view will be scroll in direct relation to scroll events. This flag needs to be
         * set for any of the other flags to take effect. If any sibling views
         * before this one do not have this flag, then this value has no effect.
         */
        public static final int SCROLL_FLAG_SCROLL = 0x1;

        /**
         * When exiting (scrolling off screen) the view will be scrolled until it is
         * 'collapsed'. The collapsed height is defined by the view's minimum height.
         *
         * @see ViewCompat#getMinimumHeight(View)
         * @see View#setMinimumHeight(int)
         */
        public static final int SCROLL_FLAG_EXIT_UNTIL_COLLAPSED = 0x2;

        /**
         * When entering (scrolling on screen) the view will scroll on any downwards
         * scroll event, regardless of whether the scrolling view is also scrolling. This
         * is commonly referred to as the 'quick return' pattern.
         */
        public static final int SCROLL_FLAG_ENTER_ALWAYS = 0x4;

        /**
         * An additional flag for 'enterAlways' which modifies the returning view to
         * only initially scroll back to it's collapsed height. Once the scrolling view has
         * reached the end of it's scroll range, the remainder of this view will be scrolled
         * into view. The collapsed height is defined by the view's minimum height.
         *
         * @see ViewCompat#getMinimumHeight(View)
         * @see View#setMinimumHeight(int)
         */
        public static final int SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED = 0x8;

        /**
         * Upon a scroll ending, if the view is only partially visible then it will be snapped
         * and scrolled to it's closest edge. For example, if the view only has it's bottom 25%
         * displayed, it will be scrolled off screen completely. Conversely, if it's bottom 75%
         * is visible then it will be scrolled fully into view.
         */
        public static final int SCROLL_FLAG_SNAP = 0x10;

        /**
         * Internal flags which allows quick checking features
         */
        static final int FLAG_QUICK_RETURN = SCROLL_FLAG_SCROLL | SCROLL_FLAG_ENTER_ALWAYS;
        static final int FLAG_SNAP = SCROLL_FLAG_SCROLL | SCROLL_FLAG_SNAP;
        static final int COLLAPSIBLE_FLAGS = SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                | SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED;

        int mScrollFlags = SCROLL_FLAG_SCROLL;
        Interpolator mScrollInterpolator;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.AppBarLayout_Layout);
            mScrollFlags = a.getInt(R.styleable.AppBarLayout_Layout_layout_scrollFlags, 0);
            if (a.hasValue(R.styleable.AppBarLayout_Layout_layout_scrollInterpolator)) {
                int resId = a.getResourceId(
                        R.styleable.AppBarLayout_Layout_layout_scrollInterpolator, 0);
                mScrollInterpolator = android.view.animation.AnimationUtils.loadInterpolator(
                        c, resId);
            }
            TypedArray b = c.obtainStyledAttributes(attrs,
                    R.styleable.CollapsingToolbarLayout_Layout);
            mCollapseMode = b.getInt(
                    R.styleable.CollapsingToolbarLayout_Layout_layout_collapseMode,
                    COLLAPSE_MODE_OFF);
            setParallaxMultiplier(b.getFloat(
                    R.styleable.CollapsingToolbarLayout_Layout_layout_collapseParallaxMultiplier,
                    0.5f));
            a.recycle();
            b.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height, weight);
        }

        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        @RequiresApi(19)
        public LayoutParams(LinearLayout.LayoutParams source) {
            // The copy constructor called here only exists on API 19+.
            super(source);
        }

        @RequiresApi(19)
        public LayoutParams(HorizontalAppBarLayout.LayoutParams source) {
            // The copy constructor called here only exists on API 19+.
            super(source);
            mScrollFlags = source.mScrollFlags;
            mScrollInterpolator = source.mScrollInterpolator;
        }

        /**
         * Set the scrolling flags.
         *
         * @param flags bitwise int of {@link #SCROLL_FLAG_SCROLL},
         *             {@link #SCROLL_FLAG_EXIT_UNTIL_COLLAPSED}, {@link #SCROLL_FLAG_ENTER_ALWAYS},
         *             {@link #SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED} and {@link #SCROLL_FLAG_SNAP }.
         *
         * @see #getScrollFlags()
         *
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollFlags
         */
        public void setScrollFlags(@HorizontalAppBarLayout.LayoutParams.ScrollFlags int flags) {
            mScrollFlags = flags;
        }

        /**
         * Returns the scrolling flags.
         *
         * @see #setScrollFlags(int)
         *
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollFlags
         */
        @HorizontalAppBarLayout.LayoutParams.ScrollFlags
        public int getScrollFlags() {
            return mScrollFlags;
        }

        /**
         * Set the interpolator to when scrolling the view associated with this
         * {@link HorizontalAppBarLayout.LayoutParams}.
         *
         * @param interpolator the interpolator to use, or null to use normal 1-to-1 scrolling.
         *
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollInterpolator
         * @see #getScrollInterpolator()
         */
        public void setScrollInterpolator(Interpolator interpolator) {
            mScrollInterpolator = interpolator;
        }

        /**
         * Returns the {@link Interpolator} being used for scrolling the view associated with this
         * {@link HorizontalAppBarLayout.LayoutParams}. Null indicates 'normal' 1-to-1 scrolling.
         *
         * @attr ref android.support.design.R.styleable#AppBarLayout_Layout_layout_scrollInterpolator
         * @see #setScrollInterpolator(Interpolator)
         */
        public Interpolator getScrollInterpolator() {
            return mScrollInterpolator;
        }

        /**
         * Returns true if the scroll flags are compatible for 'collapsing'
         */
        boolean isCollapsible() {
            return (mScrollFlags & SCROLL_FLAG_SCROLL) == SCROLL_FLAG_SCROLL
                    && (mScrollFlags & COLLAPSIBLE_FLAGS) != 0;
        }
    }

    /**
     * The default {@link HorizontalAppBarLayout.Behavior} for {@link HorizontalAppBarLayout}. Implements the necessary nested
     * scroll handling with offsetting.
     */
    public static class Behavior extends HorizontalHeaderBehavior<HorizontalAppBarLayout> {
        private static final int MAX_OFFSET_ANIMATION_DURATION = 600; // ms
        private static final int INVALID_POSITION = -1;

        /**
         * Callback to allow control over any {@link HorizontalAppBarLayout} dragging.
         */
        public static abstract class DragCallback {
            /**
             * Allows control over whether the given {@link HorizontalAppBarLayout} can be dragged or not.
             *
             * <p>Dragging is defined as a direct touch on the AppBarLayout with movement. This
             * call does not affect any nested scrolling.</p>
             *
             * @return true if we are in a position to scroll the AppBarLayout via a drag, false
             *         if not.
             */
            public abstract boolean canDrag(@NonNull HorizontalAppBarLayout appBarLayout);
        }

        private int mOffsetDelta;
        private ValueAnimator mOffsetAnimator;

        private int mOffsetToChildIndexOnLayout = INVALID_POSITION;
        private boolean mOffsetToChildIndexOnLayoutIsMinWidth;
        private float mOffsetToChildIndexOnLayoutPerc;

        private WeakReference<View> mLastNestedScrollingChildRef;
        private HorizontalAppBarLayout.Behavior.DragCallback mOnDragCallback;

        public Behavior() {}

        public Behavior(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        /**
         * NestedScrollView作为子View滑动时候会首先调用startNestedScroll(...)方法来询问父View即CoordinatorLayout是否需要消费事件，
         * CoordinatorLayout作为代理做发给对应Behavior，这里就分发给了AppBarLayout.Behavior
         * @return true 说明CoordinatorLayout需要进行消费事件的处理，然后回调AppBarLayout.Behavior.onNestedPreScroll()
         */
        @Override
        public boolean onStartNestedScroll(HorizontalCoordinatorLayout parent, HorizontalAppBarLayout child,
                                           View directTargetChild, View target, int nestedScrollAxes, int type) {
            // Return true if we're nested scrolling vertically, and we have scrollable children
            // and the scrolling view is big enough to scroll
            final boolean started = (nestedScrollAxes & ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0
                    && child.hasScrollableChildren()
                    && parent.getWidth() - directTargetChild.getWidth() <= child.getWidth();

            if (started && mOffsetAnimator != null) {
                // Cancel any offset animation
                mOffsetAnimator.cancel();
            }

            // A new nested scroll has started so clear out the previous ref
            mLastNestedScrollingChildRef = null;

            return started;
        }

        @Override
        public void onNestedPreScroll(HorizontalCoordinatorLayout horizontalCoordinatorLayout, HorizontalAppBarLayout child,
                                      View target, int dx, int dy, int[] consumed, int type) {
            if (dx != 0) {
                int min, max;
                if (dx < 0) {
                    // We're scrolling down
                    min = -child.getTotalScrollRange();
                    max = min + child.getDownNestedPreScrollRange();
                } else {
                    // We're scrolling up
                    min = -child.getUpNestedPreScrollRange();
                    max = 0;
                }
                if (min != max) {
                    consumed[0] = scroll(horizontalCoordinatorLayout, child, dx, min, max);
                }
            }
        }

        @Override
        public void onNestedScroll(HorizontalCoordinatorLayout horizontalCoordinatorLayout, HorizontalAppBarLayout child,
                                   View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed,
                                   int type) {
            if (dxUnconsumed < 0) {
                // If the scrolling view is scrolling down but not consuming, it's probably be at
                // the top of it's content
                scroll(horizontalCoordinatorLayout, child, dxUnconsumed,
                        -child.getDownNestedScrollRange(), 0);
            }
        }

        @Override
        public void onStopNestedScroll(HorizontalCoordinatorLayout horizontalCoordinatorLayout, HorizontalAppBarLayout abl,
                                       View target, int type) {
            if (type == ViewCompat.TYPE_TOUCH) {
                // If we haven't been flung then let's see if the current view has been set to snap
                snapToChildIfNeeded(horizontalCoordinatorLayout, abl);
            }

            // Keep a reference to the previous nested scrolling child
            mLastNestedScrollingChildRef = new WeakReference<>(target);
        }

        /**
         * Set a callback to control any {@link HorizontalAppBarLayout} dragging.
         *
         * @param callback the callback to use, or {@code null} to use the default behavior.
         */
        public void setDragCallback(@Nullable HorizontalAppBarLayout.Behavior.DragCallback callback) {
            mOnDragCallback = callback;
        }

        private void animateOffsetTo(final HorizontalCoordinatorLayout horizontalCoordinatorLayout,
                final HorizontalAppBarLayout child, final int offset, float velocity) {
            final int distance = Math.abs(getLeftRightOffsetForScrollingSibling() - offset);

            final int duration;
            velocity = Math.abs(velocity);
            if (velocity > 0) {
                duration = 3 * Math.round(1000 * (distance / velocity));
            } else {
                final float distanceRatio = (float) distance / child.getWidth();
                duration = (int) ((distanceRatio + 1) * 150);
            }

            animateOffsetWithDuration(horizontalCoordinatorLayout, child, offset, duration);
        }

        private void animateOffsetWithDuration(final HorizontalCoordinatorLayout horizontalCoordinatorLayout,
                final HorizontalAppBarLayout child, final int offset, final int duration) {
            final int currentOffset = getLeftRightOffsetForScrollingSibling();
            if (currentOffset == offset) {
                if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
                    mOffsetAnimator.cancel();
                }
                return;
            }

            if (mOffsetAnimator == null) {
                mOffsetAnimator = new ValueAnimator();
                mOffsetAnimator.setInterpolator(new DecelerateInterpolator());
                mOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        setHeaderLeftRightOffset(horizontalCoordinatorLayout, child,
                                (int) animation.getAnimatedValue());
                    }
                });
            } else {
                mOffsetAnimator.cancel();
            }

            mOffsetAnimator.setDuration(Math.min(duration, MAX_OFFSET_ANIMATION_DURATION));
            mOffsetAnimator.setIntValues(currentOffset, offset);
            mOffsetAnimator.start();
        }

        private int getChildIndexOnOffset(HorizontalAppBarLayout abl, final int offset) {
            for (int i = 0, count = abl.getChildCount(); i < count; i++) {
                View child = abl.getChildAt(i);
                if (child.getTop() <= -offset && child.getBottom() >= -offset) {
                    return i;
                }
            }
            return -1;
        }

        private void snapToChildIfNeeded(HorizontalCoordinatorLayout horizontalCoordinatorLayout, HorizontalAppBarLayout abl) {
            final int offset = getLeftRightOffsetForScrollingSibling();
            final int offsetChildIndex = getChildIndexOnOffset(abl, offset);
            if (offsetChildIndex >= 0) {
                final View offsetChild = abl.getChildAt(offsetChildIndex);
                final HorizontalAppBarLayout.LayoutParams
                        lp = (HorizontalAppBarLayout.LayoutParams) offsetChild.getLayoutParams();
                final int flags = lp.getScrollFlags();

                if ((flags & HorizontalAppBarLayout.LayoutParams.FLAG_SNAP) == HorizontalAppBarLayout.LayoutParams.FLAG_SNAP) {
                    // We're set the snap, so animate the offset to the nearest edge
                    int snapTop = -offsetChild.getTop();
                    int snapBottom = -offsetChild.getBottom();

                    if (offsetChildIndex == abl.getChildCount() - 1) {
                        // If this is the last child, we need to take the top inset into account
                        snapBottom += abl.getLeftInset();
                    }

                    if (checkFlag(flags, HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED)) {
                        // If the view is set only exit until it is collapsed, we'll abide by that
                        snapBottom += ViewCompat.getMinimumWidth(offsetChild);
                    } else if (checkFlag(flags, HorizontalAppBarLayout.LayoutParams.FLAG_QUICK_RETURN
                            | HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS)) {
                        // If it's set to always enter collapsed, it actually has two states. We
                        // select the state and then snap within the state
                        final int seam = snapBottom + ViewCompat.getMinimumWidth(offsetChild);
                        if (offset < seam) {
                            snapTop = seam;
                        } else {
                            snapBottom = seam;
                        }
                    }

                    final int newOffset = offset < (snapBottom + snapTop) / 2
                            ? snapBottom
                            : snapTop;
                    animateOffsetTo(horizontalCoordinatorLayout, abl,
                            MathUtils.clamp(newOffset, -abl.getTotalScrollRange(), 0), 0);
                }
            }
        }

        private static boolean checkFlag(final int flags, final int check) {
            return (flags & check) == check;
        }

        @Override
        public boolean onMeasureChild(HorizontalCoordinatorLayout parent, HorizontalAppBarLayout child,
                                      int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec,
                                      int WidthUsed) {
            final HorizontalCoordinatorLayout.LayoutParams lp =
                    (HorizontalCoordinatorLayout.LayoutParams) child.getLayoutParams();
            if (lp.width == HorizontalCoordinatorLayout.LayoutParams.WRAP_CONTENT) {
                // If the view is set to wrap on it's Width, CoordinatorLayout by default will
                // cap the view at the CoL's Width. Since the AppBarLayout can scroll, this isn't
                // what we actually want, so we measure it ourselves with an unspecified spec to
                // allow the child to be larger than it's parent
                parent.onMeasureChild(child, parentWidthMeasureSpec, widthUsed,
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), WidthUsed);
                return true;
            }

            // Let the parent handle it as normal
            return super.onMeasureChild(parent, child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, WidthUsed);
        }

        @Override
        public boolean onLayoutChild(HorizontalCoordinatorLayout parent, HorizontalAppBarLayout abl,
                                     int layoutDirection) {
            boolean handled = super.onLayoutChild(parent, abl, layoutDirection);

            // The priority for for actions here is (first which is true wins):
            // 1. forced pending actions
            // 2. offsets for restorations
            // 3. non-forced pending actions
            final int pendingAction = abl.getPendingAction();
            if (mOffsetToChildIndexOnLayout >= 0 && (pendingAction & PENDING_ACTION_FORCE) == 0) {
                View child = abl.getChildAt(mOffsetToChildIndexOnLayout);
                int offset = -child.getBottom();
                if (mOffsetToChildIndexOnLayoutIsMinWidth) {
                    offset += ViewCompat.getMinimumWidth(child) + abl.getLeftInset();
                } else {
                    offset += Math.round(child.getWidth() * mOffsetToChildIndexOnLayoutPerc);
                }
                setHeaderLeftRightOffset(parent, abl, offset);
            } else if (pendingAction != PENDING_ACTION_NONE) {
                final boolean animate = (pendingAction & PENDING_ACTION_ANIMATE_ENABLED) != 0;
                if ((pendingAction & PENDING_ACTION_COLLAPSED) != 0) {
                    final int offset = -abl.getUpNestedPreScrollRange();
                    if (animate) {
                        animateOffsetTo(parent, abl, offset, 0);
                    } else {
                        setHeaderLeftRightOffset(parent, abl, offset);
                    }
                } else if ((pendingAction & PENDING_ACTION_EXPANDED) != 0) {
                    if (animate) {
                        animateOffsetTo(parent, abl, 0, 0);
                    } else {
                        setHeaderLeftRightOffset(parent, abl, 0);
                    }
                }
            }

            // Finally reset any pending states
            abl.resetPendingAction();
            mOffsetToChildIndexOnLayout = INVALID_POSITION;

            // We may have changed size, so let's constrain the top and bottom offset correctly,
            // just in case we're out of the bounds
            setLeftAndRightOffset(
                    MathUtils.clamp(getLeftAndRightOffset(), -abl.getTotalScrollRange(), 0));

            // Update the AppBarLayout's drawable state for any elevation changes.
            // This is needed so that the elevation is set in the first layout, so that
            // we don't get a visual elevation jump pre-N (due to the draw dispatch skip)
            updateAppBarLayoutDrawableState(parent, abl, getLeftAndRightOffset(), 0, true);

            // Make sure we dispatch the offset update
            abl.dispatchOffsetUpdates(getLeftAndRightOffset());

            return handled;
        }

        @Override
        boolean canDragView(HorizontalAppBarLayout view) {
            if (mOnDragCallback != null) {
                // If there is a drag callback set, it's in control
                return mOnDragCallback.canDrag(view);
            }

            // Else we'll use the default behaviour of seeing if it can scroll down
            if (mLastNestedScrollingChildRef != null) {
                // If we have a reference to a scrolling view, check it
                final View scrollingView = mLastNestedScrollingChildRef.get();
                return scrollingView != null && scrollingView.isShown()
                        && !scrollingView.canScrollVertically(-1);
            } else {
                // Otherwise we assume that the scrolling view hasn't been scrolled and can drag.
                return true;
            }
        }

        @Override
        void onFlingFinished(HorizontalCoordinatorLayout parent, HorizontalAppBarLayout layout) {
            // At the end of a manual fling, check to see if we need to snap to the edge-child
            snapToChildIfNeeded(parent, layout);
        }

        @Override
        int getMaxDragOffset(HorizontalAppBarLayout view) {
            return -view.getDownNestedScrollRange();
        }

        @Override
        int getScrollRangeForDragFling(HorizontalAppBarLayout view) {
            return view.getTotalScrollRange();
        }

        @Override
        int setHeaderLeftRightOffset(HorizontalCoordinatorLayout horizontalCoordinatorLayout,
                                     HorizontalAppBarLayout appBarLayout, int newOffset, int minOffset, int maxOffset) {
            final int curOffset = getLeftRightOffsetForScrollingSibling();
            int consumed = 0;
            // minOffset等于AppBarLayout的负的right，maxOffset等于0。//AppBarLayout滑动的距离如果超出了minOffset或者maxOffset，则直接返回0
            if (minOffset != 0 && curOffset >= minOffset && curOffset <= maxOffset) {
                // If we have some scrolling range, and we're currently within the min and max
                // offsets, calculate a new offset //矫正newOffset，使其minOffset<=newOffset<=maxOffset
                newOffset = MathUtils.clamp(newOffset, minOffset, maxOffset);
                if (curOffset != newOffset) {
                    final int interpolatedOffset = appBarLayout.hasChildWithInterpolator()
                            ? interpolateOffset(appBarLayout, newOffset)
                            : newOffset; //由于默认没设置Interpolator，所以interpolatedOffset=newOffset;
                    //调用ViewOffsetBehvaior的方法setTopAndBottomOffset(...)，最终通过
                    // ViewCompat.offsetTopAndBottom()移动AppBarLayout
                    final boolean offsetChanged = setLeftAndRightOffset(interpolatedOffset);

                    // Update how much dy we have consumed //记录下消费了多少的dy。
                    consumed = curOffset - newOffset;
                    // Update the stored sibling offset //没设置Interpolator的情况， mOffsetDelta永远=0
                    mOffsetDelta = newOffset - interpolatedOffset;

                    if (!offsetChanged && appBarLayout.hasChildWithInterpolator()) {
                        // If the offset hasn't changed and we're using an interpolated scroll
                        // then we need to keep any dependent views updated. CoL will do this for
                        // us when we move, but we need to do it manually when we don't (as an
                        // interpolated scroll may finish early).
                        horizontalCoordinatorLayout.dispatchDependentViewsChanged(appBarLayout);
                    }

                    // Dispatch the updates to any listeners //分发回调OnOffsetChangedListener.onOffsetChanged(...)
                    appBarLayout.dispatchOffsetUpdates(getLeftAndRightOffset());

                    // Update the AppBarLayout's drawable state (for any elevation changes)
                    updateAppBarLayoutDrawableState(horizontalCoordinatorLayout, appBarLayout, newOffset,
                            newOffset < curOffset ? -1 : 1, false);
                }
            } else {
                // Reset the offset delta
                mOffsetDelta = 0;
            }

            return consumed;
        }

        @VisibleForTesting
        boolean isOffsetAnimatorRunning() {
            return mOffsetAnimator != null && mOffsetAnimator.isRunning();
        }

        private int interpolateOffset(HorizontalAppBarLayout layout, final int offset) {
            final int absOffset = Math.abs(offset);

            for (int i = 0, z = layout.getChildCount(); i < z; i++) {
                final View child = layout.getChildAt(i);
                final HorizontalAppBarLayout.LayoutParams childLp = (HorizontalAppBarLayout.LayoutParams) child.getLayoutParams();
                final Interpolator interpolator = childLp.getScrollInterpolator();

                if (absOffset >= child.getTop() && absOffset <= child.getBottom()) {
                    if (interpolator != null) {
                        int childScrollableWidth = 0;
                        final int flags = childLp.getScrollFlags();
                        if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL) != 0) {
                            // We're set to scroll so add the child's Width plus margin
                            childScrollableWidth += child.getWidth() + childLp.topMargin
                                    + childLp.bottomMargin;

                            if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED) != 0) {
                                // For a collapsing scroll, we to take the collapsed Width
                                // into account.
                                childScrollableWidth -= ViewCompat.getMinimumWidth(child);
                            }
                        }

                        if (ViewCompat.getFitsSystemWindows(child)) {
                            childScrollableWidth -= layout.getLeftInset();
                        }

                        if (childScrollableWidth > 0) {
                            final int offsetForView = absOffset - child.getTop();
                            final int interpolatedDiff = Math.round(childScrollableWidth *
                                    interpolator.getInterpolation(
                                            offsetForView / (float) childScrollableWidth));

                            return Integer.signum(offset) * (child.getTop() + interpolatedDiff);
                        }
                    }

                    // If we get to here then the view on the offset isn't suitable for interpolated
                    // scrolling. So break out of the loop
                    break;
                }
            }

            return offset;
        }

        private void updateAppBarLayoutDrawableState(final HorizontalCoordinatorLayout parent,
                final HorizontalAppBarLayout layout, final int offset, final int direction,
                final boolean forceJump) {
            final View child = getAppBarChildOnOffset(layout, offset);
            if (child != null) {
                final HorizontalAppBarLayout.LayoutParams childLp = (HorizontalAppBarLayout.LayoutParams) child.getLayoutParams();
                final int flags = childLp.getScrollFlags();
                boolean collapsed = false;

                if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL) != 0) {
                    final int minWidth = ViewCompat.getMinimumWidth(child);

                    if (direction > 0 && (flags & (HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                            | HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED)) != 0) {
                        // We're set to enter always collapsed so we are only collapsed when
                        // being scrolled down, and in a collapsed offset
                        collapsed = -offset >= child.getBottom() - minWidth - layout.getLeftInset();
                    } else if ((flags & HorizontalAppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED) != 0) {
                        // We're set to exit until collapsed, so any offset which results in
                        // the minimum Width (or less) being shown is collapsed
                        collapsed = -offset >= child.getBottom() - minWidth - layout.getLeftInset();
                    }
                }

                final boolean changed = layout.setCollapsedState(collapsed);

                if (Build.VERSION.SDK_INT >= 11 && (forceJump
                        || (changed && shouldJumpElevationState(parent, layout)))) {
                    // If the collapsed state changed, we may need to
                    // jump to the current state if we have an overlapping view
                    layout.jumpDrawablesToCurrentState();
                }
            }
        }

        private boolean shouldJumpElevationState(HorizontalCoordinatorLayout parent, HorizontalAppBarLayout layout) {
            // We should jump the elevated state if we have a dependent scrolling view which has
            // an overlapping top (i.e. overlaps us)
            final List<View> dependencies = parent.getDependents(layout);
            for (int i = 0, size = dependencies.size(); i < size; i++) {
                final View dependency = dependencies.get(i);
                final HorizontalCoordinatorLayout.LayoutParams lp =
                        (HorizontalCoordinatorLayout.LayoutParams) dependency.getLayoutParams();
                final HorizontalCoordinatorLayout.Behavior behavior = lp.getBehavior();

                if (behavior instanceof HorizontalAppBarLayout.ScrollingViewBehavior) {
                    return ((HorizontalAppBarLayout.ScrollingViewBehavior) behavior).getOverlayLeft() != 0;
                }
            }
            return false;
        }

        private static View getAppBarChildOnOffset(final HorizontalAppBarLayout layout, final int offset) {
            final int absOffset = Math.abs(offset);
            for (int i = 0, z = layout.getChildCount(); i < z; i++) {
                final View child = layout.getChildAt(i);
                if (absOffset >= child.getTop() && absOffset <= child.getBottom()) {
                    return child;
                }
            }
            return null;
        }

        @Override
        int getLeftRightOffsetForScrollingSibling() {
            return getLeftAndRightOffset() + mOffsetDelta;
        }

        @Override
        public Parcelable onSaveInstanceState(HorizontalCoordinatorLayout parent, HorizontalAppBarLayout abl) {
            final Parcelable superState = super.onSaveInstanceState(parent, abl);
            final int offset = getLeftAndRightOffset();

            // Try and find the first visible child...
            for (int i = 0, count = abl.getChildCount(); i < count; i++) {
                View child = abl.getChildAt(i);
                final int visBottom = child.getBottom() + offset;

                if (child.getTop() + offset <= 0 && visBottom >= 0) {
                    final HorizontalAppBarLayout.Behavior.SavedState
                            ss = new HorizontalAppBarLayout.Behavior.SavedState(superState);
                    ss.firstVisibleChildIndex = i;
                    ss.firstVisibleChildAtMinimumWidth =
                            visBottom == (ViewCompat.getMinimumWidth(child) + abl.getLeftInset());
                    ss.firstVisibleChildPercentageShown = visBottom / (float) child.getWidth();
                    return ss;
                }
            }

            // Else we'll just return the super state
            return superState;
        }

        @Override
        public void onRestoreInstanceState(HorizontalCoordinatorLayout parent, HorizontalAppBarLayout appBarLayout,
                                           Parcelable state) {
            if (state instanceof HorizontalAppBarLayout.Behavior.SavedState) {
                final HorizontalAppBarLayout.Behavior.SavedState
                        ss = (HorizontalAppBarLayout.Behavior.SavedState) state;
                super.onRestoreInstanceState(parent, appBarLayout, ss.getSuperState());
                mOffsetToChildIndexOnLayout = ss.firstVisibleChildIndex;
                mOffsetToChildIndexOnLayoutPerc = ss.firstVisibleChildPercentageShown;
                mOffsetToChildIndexOnLayoutIsMinWidth = ss.firstVisibleChildAtMinimumWidth;
            } else {
                super.onRestoreInstanceState(parent, appBarLayout, state);
                mOffsetToChildIndexOnLayout = INVALID_POSITION;
            }
        }

        protected static class SavedState extends AbsSavedState {
            int firstVisibleChildIndex;
            float firstVisibleChildPercentageShown;
            boolean firstVisibleChildAtMinimumWidth;

            public SavedState(Parcel source, ClassLoader loader) {
                super(source, loader);
                firstVisibleChildIndex = source.readInt();
                firstVisibleChildPercentageShown = source.readFloat();
                firstVisibleChildAtMinimumWidth = source.readByte() != 0;
            }

            public SavedState(Parcelable superState) {
                super(superState);
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                super.writeToParcel(dest, flags);
                dest.writeInt(firstVisibleChildIndex);
                dest.writeFloat(firstVisibleChildPercentageShown);
                dest.writeByte((byte) (firstVisibleChildAtMinimumWidth ? 1 : 0));
            }

            public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.ClassLoaderCreator<SavedState>() {
                @Override
                public HorizontalAppBarLayout.Behavior.SavedState createFromParcel(Parcel source, ClassLoader loader) {
                    return new HorizontalAppBarLayout.Behavior.SavedState(source, loader);
                }

                @Override
                public HorizontalAppBarLayout.Behavior.SavedState createFromParcel(Parcel source) {
                    return new HorizontalAppBarLayout.Behavior.SavedState(source, null);
                }

                @Override
                public HorizontalAppBarLayout.Behavior.SavedState[] newArray(int size) {
                    return new HorizontalAppBarLayout.Behavior.SavedState[size];
                }
            };
        }
    }

    /**
     * Behavior which should be used by {@link View}s which can scroll vertically and support
     * nested scrolling to automatically scroll any {@link HorizontalAppBarLayout} siblings.
     */
    public static class ScrollingViewBehavior extends HorizontalHeaderScrollingViewBehavior {

        public ScrollingViewBehavior() {}

        public ScrollingViewBehavior(Context context, AttributeSet attrs) {
            super(context, attrs);

            final TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.ScrollingViewBehavior_Layout);
            setOverlayLeft(a.getDimensionPixelSize(
                    R.styleable.ScrollingViewBehavior_Layout_behavior_overlapTop, 0));
            a.recycle();
        }

        @Override
        public boolean layoutDependsOn(HorizontalCoordinatorLayout parent, View child, View dependency) {
            // We depend on any AppBarLayouts
            return dependency instanceof HorizontalAppBarLayout;
        }

        @Override
        public boolean onDependentViewChanged(HorizontalCoordinatorLayout parent, View child,
                                              View dependency) {
            offsetChildAsNeeded(parent, child, dependency);
            return false;
        }

        @Override
        public boolean onRequestChildRectangleOnScreen(HorizontalCoordinatorLayout parent, View child,
                                                       Rect rectangle, boolean immediate) {
            final HorizontalAppBarLayout header = findFirstDependency(parent.getDependencies(child));
            if (header != null) {
                // Offset the rect by the child's left/top
                rectangle.offset(child.getLeft(), child.getTop());

                final Rect parentRect = mTempRect1;
                parentRect.set(0, 0, parent.getWidth(), parent.getHeight());

                if (!parentRect.contains(rectangle)) {
                    // If the rectangle can not be fully seen the visible bounds, collapse
                    // the AppBarLayout
                    header.setExpanded(false, !immediate);
                    return true;
                }
            }
            return false;
        }

        private void offsetChildAsNeeded(HorizontalCoordinatorLayout parent, View child, View dependency) {
            final HorizontalCoordinatorLayout.Behavior behavior =
                    ((HorizontalCoordinatorLayout.LayoutParams) dependency.getLayoutParams()).getBehavior();
            if (behavior instanceof HorizontalAppBarLayout.Behavior) {
                // Offset the child, pinning it to the bottom the header-dependency, maintaining
                // any vertical gap and overlap
                final HorizontalAppBarLayout.Behavior
                        ablBehavior = (HorizontalAppBarLayout.Behavior) behavior;
                ViewCompat.offsetLeftAndRight(child, (dependency.getRight() - child.getLeft())
                        + ablBehavior.mOffsetDelta
                        + getHorizontalLayoutGap()
                        - getOverlapPixelsForOffset(dependency));
            }
        }

        @Override
        float getOverlapRatioForOffset(final View header) {
            if (header instanceof HorizontalAppBarLayout) {
                final HorizontalAppBarLayout abl = (HorizontalAppBarLayout) header;
                final int totalScrollRange = abl.getTotalScrollRange();
                final int preScrollDown = abl.getDownNestedPreScrollRange();
                final int offset = getAppBarLayoutOffset(abl);

                if (preScrollDown != 0 && (totalScrollRange + offset) <= preScrollDown) {
                    // If we're in a pre-scroll down. Don't use the offset at all.
                    return 0;
                } else {
                    final int availScrollRange = totalScrollRange - preScrollDown;
                    if (availScrollRange != 0) {
                        // Else we'll use a interpolated ratio of the overlap, depending on offset
                        return 1f + (offset / (float) availScrollRange);
                    }
                }
            }
            return 0f;
        }

        private static int getAppBarLayoutOffset(HorizontalAppBarLayout abl) {
            final HorizontalCoordinatorLayout.Behavior behavior =
                    ((HorizontalCoordinatorLayout.LayoutParams) abl.getLayoutParams()).getBehavior();
            if (behavior instanceof HorizontalAppBarLayout.Behavior) {
                return ((HorizontalAppBarLayout.Behavior) behavior).getLeftRightOffsetForScrollingSibling();
            }
            return 0;
        }

        @Override
        HorizontalAppBarLayout findFirstDependency(List<View> views) {
            for (int i = 0, z = views.size(); i < z; i++) {
                View view = views.get(i);
                if (view instanceof HorizontalAppBarLayout) {
                    return (HorizontalAppBarLayout) view;
                }
            }
            return null;
        }

        @Override
        int getScrollRange(View v) {
            if (v instanceof HorizontalAppBarLayout) {
                return ((HorizontalAppBarLayout) v).getTotalScrollRange();
            } else {
                return super.getScrollRange(v);
            }
        }
    }
//
//    static ViewOffsetHelper getViewOffsetHelper(View view) {
//        ViewOffsetHelper offsetHelper = (ViewOffsetHelper) view.getTag(R.id.view_offset_helper);
//        if (offsetHelper == null) {
//            offsetHelper = new ViewOffsetHelper(view);
//            view.setTag(R.id.view_offset_helper, offsetHelper);
//        }
//        return offsetHelper;
//    }

//    private class OffsetUpdateListener implements HorizontalAppBarLayout.OnOffsetChangedListener {
//        OffsetUpdateListener() {
//        }
//
//        @Override
//        public void onOffsetChanged(HorizontalAppBarLayout layout, int verticalOffset) {
//
//            final int insetTop = mLastInsets != null ? mLastInsets.getSystemWindowInsetTop() : 0;
//
//            for (int i = 0, z = getChildCount(); i < z; i++) {
//                final View child = getChildAt(i);
//                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
//                final ViewOffsetHelper offsetHelper = getViewOffsetHelper(child);
//
//                switch (lp.mCollapseMode) {
//                    case LayoutParams.COLLAPSE_MODE_PIN:
//                        offsetHelper.setLeftAndRightOffset(MathUtils.clamp(
//                                -verticalOffset, 0, getMaxOffsetForPinChild(child)));
//                        break;
//                    case LayoutParams.COLLAPSE_MODE_PARALLAX:
//                        offsetHelper.setLeftAndRightOffset((int) Math.round(-verticalOffset * 0.5));
//                        break;
//                }
//            }
//        }
//    }

//    final int getMaxOffsetForPinChild(View child) {
//        final ViewOffsetHelper offsetHelper = getViewOffsetHelper(child);
//        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
//        return getWidth()
//                - offsetHelper.getLayoutLeft()
//                - child.getWidth()
//                - lp.rightMargin;
//    }
}

