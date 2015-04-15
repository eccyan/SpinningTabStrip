/*
 * Copyright (C) 2013 Andreas Stuetz <andreas.stuetz@gmail.com>
 * Modifications Copyright (C) 2015 eccyan <g00.eccyan@gmail.com>
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

package com.eccyan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.support.v4.view.ViewPager.OnPageChangeListener;
import static android.support.v4.view.ViewPager.SCROLL_STATE_DRAGGING;
import static android.support.v4.view.ViewPager.SCROLL_STATE_IDLE;

public class SpinningTabStrip extends HorizontalScrollView {

    private static final String TAG = SpinningTabStrip.class.getSimpleName();

    public class Flinger implements Runnable {

        private final Scroller scroller;

        private Runnable onComplete;

        Flinger() {
            scroller = new Scroller(getContext());
        }

        void start(int x, int velocity) {
            start(x, velocity, null);
        }

        void start(final int x, final int velocity, Runnable onComplete) {
            scrollStopped.set(false);

            this.onComplete = onComplete;
            scroller.fling(x, getScrollY(), velocity, 0,
                    Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
            post(this);
        }

        public void run() {
            if (scroller.isFinished()) {
                if (onComplete != null) {
                    post(onComplete);
                }
                return;
            }

            final boolean shouldUpdate = scroller.computeScrollOffset();
            scrollTo(scroller.getCurrX(), scroller.getCurrY());

            if (shouldUpdate) {
                post(this);
            }
        }

        boolean isFinished() {
            return scroller.isFinished();
        }

        void forceFinished(boolean finished) {
            if (!scroller.isFinished()) {
                scroller.forceFinished(finished);
            }
        }

        float getCurrVelocity() {
            return scroller.getCurrVelocity();
        }
    }

    public interface CustomTabProvider {

        View getCustomTabView(ViewGroup parent, int position);
    }

    public interface OnTabReselectedListener {

        void onTabReselected(int position);
    }

    private static final float OPAQUE = 1.0f;

    private static final float HALF_TRANSP = 0.5f;

    private static final int DUMMY_TAB_RATE = 4;

    // @formatter:off
    private static final int[] ATTRS = new int[]{
            android.R.attr.textSize,
            android.R.attr.textColor,
            android.R.attr.paddingLeft,
            android.R.attr.paddingRight,
            android.R.attr.textColorPrimary,
    };
    // @formatter:on

    private final PagerAdapterObserver adapterObserver = new PagerAdapterObserver();

    //These indexes must be related with the ATTR array above
    private static final int TEXT_SIZE_INDEX = 0;

    private static final int TEXT_COLOR_INDEX = 1;

    private static final int TEXT_COLOR_PRIMARY = 4;

    private LinearLayout.LayoutParams defaultTabLayoutParams;

    private LinearLayout.LayoutParams expandedTabLayoutParams;

    private final PageListener pageListener = new PageListener();

    private OnTabReselectedListener tabReselectedListener = null;

    public OnPageChangeListener delegatePageListener;

    private LinearLayout tabsContainer;

    private GestureDetector gestureDetector;

    // For tracking flinging velocity
    private Scroller flingVelocity;

    private Flinger flinger;

    private ViewPager pager;

    private int tabCount;

    private int realTabCount;

    private int currentPosition = 0;

    private float currentPositionOffset = 0f;

    private Paint rectPaint;

    private Paint dividerPaint;

    private int indicatorColor;

    private int indicatorHeight = 2;

    private int underlineHeight = 0;

    private int underlineColor;

    private int dividerWidth = 0;

    private int dividerPadding = 0;

    private int dividerColor;

    private int tabPadding = 12;

    private int tabTextSize = 14;

    private ColorStateList tabTextColor = null;

    private float tabTextAlpha = HALF_TRANSP;

    private float tabTextSelectedAlpha = OPAQUE;

    private boolean shouldExpand = false;

    private boolean textAllCaps = true;

    private Typeface tabTypeface = null;

    private int tabTypefaceStyle = Typeface.BOLD;

    private int tabTypefaceSelectedStyle = Typeface.BOLD;

    private int scrollOffset;

    private int lastScrollX = 0;

    private AtomicBoolean scrollStopped = new AtomicBoolean();

    private int tabBackgroundResId = R.drawable.background_tab;

    private Locale locale;

    public SpinningTabStrip(Context context) {
        this(context, null);
    }

    public SpinningTabStrip(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpinningTabStrip(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFillViewport(true);
        setWillNotDraw(false);
        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(tabsContainer);

        flingVelocity = new Scroller(getContext());
        flinger = new Flinger();
        gestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                            float velocityX, float velocityY) {

                        flingVelocity.forceFinished(true);
                        flingVelocity.fling(getScrollX(), getScrollY(),
                                -(int) velocityX, -(int) velocityY,
                                Integer.MIN_VALUE, Integer.MAX_VALUE, 0, getHeight());

                        scrollStopped.set(false);
                        Log.d(TAG, "scroll fling started");

                        return true;
                    }

                    @Override
                    public boolean onDown(MotionEvent e) {
                        if (!flingVelocity.isFinished()) {
                            flingVelocity.forceFinished(true);
                        }

                        if (!flinger.isFinished()) {
                            flinger.forceFinished(true);
                        }

                        return super.onDown(e);
                    }
                });

        DisplayMetrics dm = getResources().getDisplayMetrics();
        scrollOffset = (int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrollOffset, dm);
        indicatorHeight = (int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, indicatorHeight, dm);
        underlineHeight = (int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, underlineHeight, dm);
        dividerPadding = (int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerPadding, dm);
        tabPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, tabPadding, dm);
        dividerWidth = (int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerWidth, dm);
        tabTextSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tabTextSize, dm);

        // get system attrs (android:textSize and android:textColor)
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS);
        tabTextSize = a.getDimensionPixelSize(TEXT_SIZE_INDEX, tabTextSize);
        ColorStateList colorStateList = a.getColorStateList(TEXT_COLOR_INDEX);
        int textPrimaryColor = a.getColor(TEXT_COLOR_PRIMARY, android.R.color.white);
        if (colorStateList != null) {
            tabTextColor = colorStateList;
        } else {
            tabTextColor = getColorStateList(textPrimaryColor);
        }

        underlineColor = textPrimaryColor;
        dividerColor = textPrimaryColor;
        indicatorColor = textPrimaryColor;

        // get custom attrs
        a = context.obtainStyledAttributes(attrs, R.styleable.SpinningTabStrip);
        indicatorColor = a
                .getColor(R.styleable.SpinningTabStrip_pstsIndicatorColor, indicatorColor);
        underlineColor = a
                .getColor(R.styleable.SpinningTabStrip_pstsUnderlineColor, underlineColor);
        dividerColor = a.getColor(R.styleable.SpinningTabStrip_pstsDividerColor, dividerColor);
        dividerWidth = a.getDimensionPixelSize(R.styleable.SpinningTabStrip_pstsDividerWidth,
                dividerWidth);
        indicatorHeight = a
                .getDimensionPixelSize(R.styleable.SpinningTabStrip_pstsIndicatorHeight,
                        indicatorHeight);
        underlineHeight = a
                .getDimensionPixelSize(R.styleable.SpinningTabStrip_pstsUnderlineHeight,
                        underlineHeight);
        dividerPadding = a
                .getDimensionPixelSize(R.styleable.SpinningTabStrip_pstsDividerPadding,
                        dividerPadding);
        tabPadding = a
                .getDimensionPixelSize(R.styleable.SpinningTabStrip_pstsTabPaddingLeftRight,
                        tabPadding);
        tabBackgroundResId = a.getResourceId(R.styleable.SpinningTabStrip_pstsTabBackground,
                tabBackgroundResId);
        shouldExpand = a
                .getBoolean(R.styleable.SpinningTabStrip_pstsShouldExpand, shouldExpand);
        scrollOffset = a.getDimensionPixelSize(R.styleable.SpinningTabStrip_pstsScrollOffset,
                scrollOffset);
        textAllCaps = a.getBoolean(R.styleable.SpinningTabStrip_pstsTextAllCaps, textAllCaps);
        tabTypefaceStyle = a.getInt(R.styleable.SpinningTabStrip_pstsTextStyle, Typeface.BOLD);
        tabTypefaceSelectedStyle = a
                .getInt(R.styleable.SpinningTabStrip_pstsTextSelectedStyle, Typeface.BOLD);
        tabTextAlpha = a.getFloat(R.styleable.SpinningTabStrip_pstsTextAlpha, HALF_TRANSP);
        tabTextSelectedAlpha = a
                .getFloat(R.styleable.SpinningTabStrip_pstsTextSelectedAlpha, OPAQUE);

        a.recycle();

        setMarginBottomTabContainer();

        rectPaint = new Paint();
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Style.FILL);

        dividerPaint = new Paint();
        dividerPaint.setAntiAlias(true);
        dividerPaint.setStrokeWidth(dividerWidth);

        defaultTabLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT);
        expandedTabLayoutParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);

        if (locale == null) {
            locale = getResources().getConfiguration().locale;
        }

    }

    private void setMarginBottomTabContainer() {
        ViewGroup.MarginLayoutParams mlp = (MarginLayoutParams) tabsContainer.getLayoutParams();
        int bottomMargin = indicatorHeight >= underlineHeight ? indicatorHeight : underlineHeight;
        mlp.setMargins(mlp.leftMargin, mlp.topMargin, mlp.rightMargin, bottomMargin);
        tabsContainer.setLayoutParams(mlp);
    }

    public void setViewPager(ViewPager pager) {
        this.pager = pager;
        if (pager.getAdapter() == null) {
            throw new IllegalStateException("ViewPager does not have adapter instance.");
        }

        pager.setOnPageChangeListener(pageListener);
        pager.getAdapter().registerDataSetObserver(adapterObserver);
        adapterObserver.setAttached(true);
        notifyDataSetChanged();
    }

    public void notifyDataSetChanged() {
        tabsContainer.removeAllViews();
        tabCount = pager.getAdapter().getCount();
        realTabCount = tabCount * DUMMY_TAB_RATE;
        View tabView;
        for (int i = 0; i < realTabCount; i++) {
            if (pager.getAdapter() instanceof CustomTabProvider) {
                tabView = ((CustomTabProvider) pager.getAdapter())
                        .getCustomTabView(this, i % tabCount);
            } else {
                tabView = LayoutInflater.from(getContext()).inflate(R.layout.tab, this, false);
            }

            CharSequence title = pager.getAdapter().getPageTitle(i % tabCount);

            addTab(i, title, tabView);
        }

        updateTabStyles();
        getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            @SuppressWarnings("deprecation")
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }

                currentPosition = pager.getCurrentItem();
                currentPositionOffset = 0f;
                scrollToChild(currentPosition, 0);
                updateSelection(currentPosition);
            }
        });
    }

    private void addTab(final int position, CharSequence title, View tabView) {
        TextView tabTitle = (TextView) tabView.findViewById(R.id.tab_title);
        if (tabTitle != null) {
            if (title != null) {
                tabTitle.setText(title);
            }
            float alpha = pager.getCurrentItem() == position ? tabTextSelectedAlpha : tabTextAlpha;
            tabTitle.setAlpha(alpha);
        }

        tabView.setFocusable(true);
        tabView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pager.getCurrentItem() != position) {
                    notSelectedItem(pager.getCurrentItem());
                    pager.setCurrentItem(position % tabCount);
                } else if (tabReselectedListener != null) {
                    tabReselectedListener.onTabReselected(position);
                }
            }
        });

        tabsContainer.addView(tabView, position,
                shouldExpand ? expandedTabLayoutParams : defaultTabLayoutParams);
    }

    private void updateTabStyles() {
        for (int i = 0; i < realTabCount; i++) {
            View v = tabsContainer.getChildAt(i);
            if (!(pager.getAdapter() instanceof CustomTabProvider)) {
                v.setBackgroundResource(tabBackgroundResId);
            }
            v.setPadding(tabPadding, v.getPaddingTop(), tabPadding, v.getPaddingBottom());

            TextView tabTitle = (TextView) v.findViewById(R.id.tab_title);
            if (tabTitle != null) {
                tabTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, tabTextSize);
                tabTitle.setTypeface(tabTypeface,
                        pager.getCurrentItem() == i ? tabTypefaceSelectedStyle : tabTypefaceStyle);
                if (tabTextColor != null) {
                    tabTitle.setTextColor(tabTextColor);
                }
                // setAllCaps() is only available from API 14, so the upper case is made manually if we are on a
                // pre-ICS-build
                if (textAllCaps) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        tabTitle.setAllCaps(true);
                    } else {
                        tabTitle.setText(tabTitle.getText().toString().toUpperCase(locale));
                    }
                }
            }
        }

    }

    private void scrollToChild(int position, int offset) {
        if (tabCount == 0) {
            return;
        }

        final int realPosition = position + tabCount;
        int newScrollX = tabsContainer.getChildAt(realPosition).getLeft() + offset;

        //Half screen offset.
        //- Either tabs start at the middle of the view scrolling straight away
        //- Or tabs start at the begging (no padding) scrolling when indicator gets
        //  to the middle of the view width
        newScrollX -= scrollOffset;
        Pair<Float, Float> lines = getIndicatorCoordinates();
        newScrollX += ((lines.second - lines.first) / 2);

        if (newScrollX != lastScrollX) {
            lastScrollX = newScrollX;
            scrollTo(newScrollX, 0);
        }
    }

    private Pair<Float, Float> getIndicatorCoordinates() {
        // default: line below current tab

        View currentTab = tabsContainer.getChildAt(getRealCurrentPosition());
        float lineLeft = currentTab.getLeft();
        float lineRight = currentTab.getRight();

        // if there is an offset, start interpolating left and right coordinates between current and next tab
        if (currentPositionOffset > 0f && getRealCurrentPosition() < realTabCount - 1) {

            View nextTab = tabsContainer.getChildAt(getRealCurrentPosition() + 1);
            final float nextTabLeft = nextTab.getLeft();
            final float nextTabRight = nextTab.getRight();

            lineLeft = (currentPositionOffset * nextTabLeft
                    + (1f - currentPositionOffset) * lineLeft);
            lineRight = (currentPositionOffset * nextTabRight
                    + (1f - currentPositionOffset) * lineRight);
        }
        return new Pair<Float, Float>(lineLeft, lineRight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (tabsContainer.getChildCount() > 0) {
            tabsContainer
                    .getChildAt(0 + tabCount)
                    .getViewTreeObserver()
                    .addOnGlobalLayoutListener(firstTabGlobalLayoutListener);

        }
        super.onLayout(changed, l, t, r, b);
    }

    private OnGlobalLayoutListener firstTabGlobalLayoutListener = new OnGlobalLayoutListener() {

        @Override
        public void onGlobalLayout() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                getViewTreeObserver().removeGlobalOnLayoutListener(this);
            } else {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }

            scrollOffset = getWidth() / 2;
            scrollToChild(currentPosition, 0);
            selectedItem(currentPosition);
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isInEditMode() || tabCount == 0) {
            return;
        }

        final int height = getHeight();
        // draw indicator line, and draw indicator line for next dummy tab
        rectPaint.setColor(indicatorColor);
        Pair<Float, Float> lines = getIndicatorCoordinates();
        final float left = lines.first;
        final float right = lines.second;
        final float tabsWidth = getTabsWidth();
        for (int i = 0; i < DUMMY_TAB_RATE; ++i) {
            final float padding = tabsWidth * i;
            canvas.drawRect(left + padding, height - indicatorHeight, right + padding, height,
                    rectPaint);
        }
        // draw underline
        rectPaint.setColor(underlineColor);
        canvas.drawRect(0, height - underlineHeight, tabsContainer.getWidth(), height, rectPaint);
        // draw divider
        if (dividerWidth != 0) {
            dividerPaint.setStrokeWidth(dividerWidth);
            dividerPaint.setColor(dividerColor);
            for (int i = 0; i < realTabCount - 1; i++) {
                View tab = tabsContainer.getChildAt(i);
                canvas.drawLine(tab.getRight(), dividerPadding, tab.getRight(),
                        height - dividerPadding, dividerPaint);
            }
        }
    }

    @Override
    protected void onScrollChanged(final int l, final int t, final int oldl, final int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        if (isInfiniteStartPoint(l)) {
            onInfiniteScrollStart(l, t, oldl, oldt);
        }

        if (isInfiniteEndPoint(l)) {
            onInfiniteScrollEnd(l, t, oldl, oldt);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev) | gestureDetector.onTouchEvent(ev);
    }

    protected void onInfiniteScrollStart(int l, int t, int oldl, int oldt) {
        flingVelocity.computeScrollOffset();

        final boolean overScrolled = l <= 0;
        final int tabsWidth = getTabsWidth();
        // Use finalX to difference when scroll is over.
        final int difference = (!overScrolled) ? l : Math.abs(flingVelocity.getFinalX());
        final int moveTo = tabsContainer.getWidth() - (tabsWidth * 2 - difference % tabsWidth);

        scrollTo(moveTo, t);

        // First flinging
        if (!flingVelocity.isFinished()) {
            Log.d(TAG, String.format("Start start infinite"));
            final int velocity = (int) flingVelocity.getCurrVelocity();
            flingVelocity.forceFinished(true);
            flinger.start(moveTo, -velocity);
        }

        // Next flinging
        if (overScrolled) {
            Log.d(TAG, String.format("Start over scrolled"));
            final int velocity = (int) flinger.getCurrVelocity();
            flinger.forceFinished(true);
            flinger.start(moveTo, -velocity);
        }
    }

    protected void onInfiniteScrollEnd(int l, int t, int oldl, int oldt) {
        flingVelocity.computeScrollOffset();

        final boolean overScrolled = l >= tabsContainer.getWidth() - getWidth();
        final int tabsWidth = getTabsWidth();
        // Use finalX to difference when scroll is over.
        final int difference = (!overScrolled) ? l : Math.abs(flingVelocity.getFinalX());
        final int moveTo = tabsWidth + difference % tabsWidth;
        Log.d(TAG, String.format("End l :%d, moveTo: %d", l, moveTo));

        scrollTo(moveTo, t);

        // First flinging
        if (!flingVelocity.isFinished()) {
            Log.d(TAG, String.format("End start infinite"));
            final int velocity = (int) flingVelocity.getCurrVelocity();
            flingVelocity.forceFinished(true);
            flinger.start(moveTo, velocity);
        }

        // Next flinging
        if (overScrolled) {
            Log.d(TAG, String.format("End over scrolled"));
            final int velocity = (int) flinger.getCurrVelocity();
            flinger.forceFinished(true);
            flinger.start(moveTo, velocity);
        }
    }

    public void setOnTabReselectedListener(OnTabReselectedListener tabReselectedListener) {
        this.tabReselectedListener = tabReselectedListener;
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        this.delegatePageListener = listener;
    }

    private class PageListener implements OnPageChangeListener {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            currentPosition = position;
            currentPositionOffset = positionOffset;
            int offset = tabCount > 0 ? (int) (positionOffset * tabsContainer
                    .getChildAt(position + tabCount).getWidth()) : 0;
            scrollToChild(currentPosition, offset);
            invalidate();
            if (delegatePageListener != null) {
                delegatePageListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (state == SCROLL_STATE_DRAGGING) {
                Log.d(TAG, "scroll state dragging");
                flinger.forceFinished(true);
            }
            if (state == SCROLL_STATE_IDLE) {
                scrollToChild(pager.getCurrentItem(), 0);
            }
            //Half transparent for prev item
            notSelectedItem(pager.getCurrentItem() - 1);
            //Half transparent for next item
            notSelectedItem(pager.getCurrentItem() + 1);
            //Full alpha for current item
            selectedItem(pager.getCurrentItem());

            if (delegatePageListener != null) {
                delegatePageListener.onPageScrollStateChanged(state);
            }
        }

        @Override
        public void onPageSelected(int position) {
            updateSelection(position);
            if (delegatePageListener != null) {
                delegatePageListener.onPageSelected(position);
            }
            Log.d(TAG, String.format("selected tab position: %d", position));
        }

    }

    private void updateSelection(int position) {
        for (int i = 0; i < realTabCount; ++i) {
            View tv = tabsContainer.getChildAt(i);
            tv.setSelected(i % tabCount == position);
        }
    }

    private void notSelectedItem(int position) {
        position = (position < 0) ? tabCount - 1 : position;

        for (int i = 0; i < DUMMY_TAB_RATE; i++) {
            final int index = i * tabCount + position;
            notSelected(tabsContainer.getChildAt(index % realTabCount));
        }
    }

    private void selectedItem(int position) {
        position = (position < 0) ? tabCount - 1 : position;

        for (int i = 0; i < DUMMY_TAB_RATE; i++) {
            final int index = i * tabCount + position;
            final View tab = tabsContainer.getChildAt(index % realTabCount);
            selected(tab);
        }
    }

    private void notSelected(View tab) {
        TextView title = (TextView) tab.findViewById(R.id.tab_title);
        if (title != null) {
            title.setTypeface(tabTypeface, tabTypefaceStyle);
            title.setAlpha(tabTextAlpha);
        }
    }

    private void selected(View tab) {
        TextView title = (TextView) tab.findViewById(R.id.tab_title);
        if (title != null) {
            title.setTypeface(tabTypeface, tabTypefaceSelectedStyle);
            title.setAlpha(tabTextSelectedAlpha);
        }
    }


    private class PagerAdapterObserver extends DataSetObserver {

        private boolean attached = false;

        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }

        public void setAttached(boolean attached) {
            this.attached = attached;
        }

        public boolean isAttached() {
            return attached;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pager != null) {
            if (!adapterObserver.isAttached()) {
                pager.getAdapter().registerDataSetObserver(adapterObserver);
                adapterObserver.setAttached(true);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pager != null) {
            if (adapterObserver.isAttached()) {
                pager.getAdapter().unregisterDataSetObserver(adapterObserver);
                adapterObserver.setAttached(false);
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        currentPosition = savedState.currentPosition;
        if (currentPosition != 0 && tabsContainer.getChildCount() > 0) {
            notSelectedItem(0);
            selectedItem(currentPosition);
        }
        requestLayout();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.currentPosition = currentPosition;
        return savedState;
    }

    static class SavedState extends BaseSavedState {

        int currentPosition;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentPosition = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(currentPosition);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

    }

    public int getIndicatorColor() {
        return this.indicatorColor;
    }

    public int getIndicatorHeight() {
        return indicatorHeight;
    }

    public int getUnderlineColor() {
        return underlineColor;
    }

    public int getDividerColor() {
        return dividerColor;
    }

    public int getDividerWidth() {
        return dividerWidth;
    }

    public int getUnderlineHeight() {
        return underlineHeight;
    }

    public int getDividerPadding() {
        return dividerPadding;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public boolean getShouldExpand() {
        return shouldExpand;
    }

    public int getTextSize() {
        return tabTextSize;
    }

    public boolean isTextAllCaps() {
        return textAllCaps;
    }

    public ColorStateList getTextColor() {
        return tabTextColor;
    }

    public int getTabBackground() {
        return tabBackgroundResId;
    }

    public int getTabPaddingLeftRight() {
        return tabPadding;
    }

    public void setIndicatorColor(int indicatorColor) {
        this.indicatorColor = indicatorColor;
        invalidate();
    }

    public void setIndicatorColorResource(int resId) {
        this.indicatorColor = getResources().getColor(resId);
        invalidate();
    }

    public void setIndicatorHeight(int indicatorLineHeightPx) {
        this.indicatorHeight = indicatorLineHeightPx;
        invalidate();
    }

    public void setUnderlineColor(int underlineColor) {
        this.underlineColor = underlineColor;
        invalidate();
    }

    public void setUnderlineColorResource(int resId) {
        this.underlineColor = getResources().getColor(resId);
        invalidate();
    }

    public void setDividerColor(int dividerColor) {
        this.dividerColor = dividerColor;
        invalidate();
    }

    public void setDividerColorResource(int resId) {
        this.dividerColor = getResources().getColor(resId);
        invalidate();
    }

    public void setDividerWidth(int dividerWidthPx) {
        this.dividerWidth = dividerWidthPx;
        invalidate();
    }

    public void setUnderlineHeight(int underlineHeightPx) {
        this.underlineHeight = underlineHeightPx;
        invalidate();
    }

    public void setDividerPadding(int dividerPaddingPx) {
        this.dividerPadding = dividerPaddingPx;
        invalidate();
    }

    public void setScrollOffset(int scrollOffsetPx) {
        this.scrollOffset = scrollOffsetPx;
        invalidate();
    }

    public void setShouldExpand(boolean shouldExpand) {
        this.shouldExpand = shouldExpand;
        if (pager != null) {
            requestLayout();
        }
    }

    public void setAllCaps(boolean textAllCaps) {
        this.textAllCaps = textAllCaps;
    }

    public void setTextSize(int textSizePx) {
        this.tabTextSize = textSizePx;
        updateTabStyles();
    }

    public void setTextColor(int textColor) {
        setTextColor(getColorStateList(textColor));
    }

    private ColorStateList getColorStateList(int textColor) {
        return new ColorStateList(new int[][]{new int[]{}}, new int[]{textColor});
    }

    public void setTextColor(ColorStateList colorStateList) {
        this.tabTextColor = colorStateList;
        updateTabStyles();
    }

    public void setTextColorResource(int resId) {
        setTextColor(getResources().getColor(resId));
    }

    public void setTextColorStateListResource(int resId) {
        setTextColor(getResources().getColorStateList(resId));
    }

    public void setTypeface(Typeface typeface, int style) {
        this.tabTypeface = typeface;
        this.tabTypefaceSelectedStyle = style;
        updateTabStyles();
    }

    public void setTabBackground(int resId) {
        this.tabBackgroundResId = resId;
    }

    public void setTabPaddingLeftRight(int paddingPx) {
        this.tabPadding = paddingPx;
        updateTabStyles();
    }

    protected int getRealCurrentPosition() {
        return currentPosition + tabCount;
    }

    protected int getTabsWidth() {
        return tabsContainer.getWidth() / DUMMY_TAB_RATE;
    }

    protected boolean isInfiniteStartPoint(int point) {
        return point <= getTabsWidth();
    }

    protected boolean isInfiniteEndPoint(int point) {
        return point >= (tabsContainer.getWidth() - getTabsWidth());
    }
}
