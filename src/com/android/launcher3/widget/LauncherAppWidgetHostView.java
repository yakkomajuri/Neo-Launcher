/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AdapterView;
import android.widget.Advanceable;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer.TouchCompleteListener;
import com.android.launcher3.widget.custom.CustomAppWidgetProviderInfo;

/**
 * {@inheritDoc}
 */
public class LauncherAppWidgetHostView extends BaseLauncherAppWidgetHostView
        implements TouchCompleteListener, View.OnLongClickListener,
        LocalColorExtractor.Listener {

    private static final String TAG = "LauncherAppWidgetHostView";

    // Related to the auto-advancing of widgets
    private static final long ADVANCE_INTERVAL = 20000;
    private static final long ADVANCE_STAGGER = 250;

    // Maintains a list of widget ids which are supposed to be auto advanced.
    private static final SparseBooleanArray sAutoAdvanceWidgetIds = new SparseBooleanArray();
    // Maximum duration for which updates can be deferred.
    private static final long UPDATE_LOCK_TIMEOUT_MILLIS = 1000;

    private static final String TRACE_METHOD_NAME = "appwidget load-widget ";

    private final Rect mTempRect = new Rect();
    private final CheckLongPressHelper mLongPressHelper;
    protected final Launcher mLauncher;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mReinflateOnConfigChange;

    // Maintain the color manager.
    private final LocalColorExtractor mColorExtractor;

    private boolean mIsScrollable;
    private boolean mIsAttachedToWindow;
    private boolean mIsAutoAdvanceRegistered;
    private Runnable mAutoAdvanceRunnable;

    private long mDeferUpdatesUntilMillis = 0;
    private RemoteViews mDeferredRemoteViews;
    private boolean mHasDeferredColorChange = false;
    private @Nullable SparseIntArray mDeferredColorChange = null;

    // The following member variables are only used during drag-n-drop.
    private boolean mIsInDragMode = false;
    /**
     * The drag content width which is only set when the drag content scale is not 1f.
     */
    private int mDragContentWidth = 0;
    /** The drag content height which is only set when the drag content scale is not 1f. */
    private int mDragContentHeight = 0;

    private boolean mTrackingWidgetUpdate = false;

    public LauncherAppWidgetHostView(Context context) {
        super(context);
        mLauncher = Launcher.getLauncher(context);
        mLongPressHelper = new CheckLongPressHelper(this, this);
        setAccessibilityDelegate(mLauncher.getAccessibilityDelegate());
        setBackgroundResource(R.drawable.widget_internal_focus_bg);

        if (Utilities.ATLEAST_Q && Themes.getAttrBoolean(mLauncher, R.attr.isWorkspaceDarkText)) {
            setOnLightBackground(true);
        }
        mColorExtractor = LocalColorExtractor.newInstance(getContext());
    }

    @Override
    public void setColorResources(@Nullable SparseIntArray colors) {
        if (colors == null) {
            if (Utilities.ATLEAST_S)
                resetColorResources();
        } else {
            super.setColorResources(colors);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (mIsScrollable) {
            DragLayer dragLayer = mLauncher.getDragLayer();
            dragLayer.requestDisallowInterceptTouchEvent(false);
        }
        view.performLongClick();
        return true;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.Q)
    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        super.setAppWidget(appWidgetId, info);
        if (info != null && Utilities.getOmegaPrefs(getContext()).getDesktopAllowFullWidthWidgets().getValue()) {
            setPadding(0, 0, 0, 0);
        } else if (info instanceof CustomAppWidgetProviderInfo) {
            if (((CustomAppWidgetProviderInfo) info).noPadding) {
                setPadding(0, 0, 0, 0);
            }
        }
        if (!mTrackingWidgetUpdate && Utilities.ATLEAST_Q) {
            mTrackingWidgetUpdate = true;
            Trace.beginAsyncSection(TRACE_METHOD_NAME + info.provider, appWidgetId);
            Log.i(TAG, "App widget created with id: " + appWidgetId);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.Q)
    public void updateAppWidget(RemoteViews remoteViews) {
        if (mTrackingWidgetUpdate && remoteViews != null && Utilities.ATLEAST_Q) {
            Log.i(TAG, "App widget with id: " + getAppWidgetId() + " loaded");
            Trace.endAsyncSection(
                    TRACE_METHOD_NAME + getAppWidgetInfo().provider, getAppWidgetId());
            mTrackingWidgetUpdate = false;
        }
        if (isDeferringUpdates()) {
            mDeferredRemoteViews = remoteViews;
            return;
        }
        mDeferredRemoteViews = null;

        super.updateAppWidget(remoteViews);

        // The provider info or the views might have changed.
        checkIfAutoAdvance();

        // It is possible that widgets can receive updates while launcher is not in the foreground.
        // Consequently, the widgets will be inflated for the orientation of the foreground activity
        // (framework issue). On resuming, we ensure that any widgets are inflated for the current
        // orientation.
        mReinflateOnConfigChange = !isSameOrientation();
    }

    private boolean isSameOrientation() {
        return mLauncher.getResources().getConfiguration().orientation ==
                mLauncher.getOrientation();
    }

    private boolean checkScrollableRecursively(ViewGroup viewGroup) {
        if (viewGroup instanceof AdapterView) {
            return true;
        } else {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof ViewGroup) {
                    if (checkScrollableRecursively((ViewGroup) child)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the application of {@link RemoteViews} through {@link #updateAppWidget} and
     * colors through {@link #onColorsChanged} are currently being deferred.
     * @see #beginDeferringUpdates()
     */
    private boolean isDeferringUpdates() {
        return SystemClock.uptimeMillis() < mDeferUpdatesUntilMillis;
    }

    /**
     * Begin deferring the application of any {@link RemoteViews} updates made through
     * {@link #updateAppWidget} and color changes through {@link #onColorsChanged} until
     * {@link #endDeferringUpdates()} has been called or the next {@link #updateAppWidget} or
     * {@link #onColorsChanged} call after {@link #UPDATE_LOCK_TIMEOUT_MILLIS} have elapsed.
     */
    public void beginDeferringUpdates() {
        mDeferUpdatesUntilMillis = SystemClock.uptimeMillis() + UPDATE_LOCK_TIMEOUT_MILLIS;
    }

    /**
     * Stop deferring the application of {@link RemoteViews} updates made through
     * {@link #updateAppWidget} and color changes made through {@link #onColorsChanged} and apply
     * any deferred updates.
     */
    public void endDeferringUpdates() {
        RemoteViews remoteViews;
        SparseIntArray deferredColors;
        boolean hasDeferredColors;
        mDeferUpdatesUntilMillis = 0;
        remoteViews = mDeferredRemoteViews;
        mDeferredRemoteViews = null;
        deferredColors = mDeferredColorChange;
        hasDeferredColors = mHasDeferredColorChange;
        mDeferredColorChange = null;
        mHasDeferredColorChange = false;

        if (remoteViews != null) {
            updateAppWidget(remoteViews);
        }
        if (hasDeferredColors) {
            onColorsChanged(deferredColors);
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            DragLayer dragLayer = mLauncher.getDragLayer();
            if (mIsScrollable) {
                dragLayer.requestDisallowInterceptTouchEvent(true);
            }
            dragLayer.setTouchCompleteListener(this);
        }
        mLongPressHelper.onTouchEvent(ev);
        return mLongPressHelper.hasPerformedLongPress();
    }

    public boolean onTouchEvent(MotionEvent ev) {
        mLongPressHelper.onTouchEvent(ev);
        // We want to keep receiving though events to be able to cancel long press on ACTION_UP
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIsAttachedToWindow = true;
        checkIfAutoAdvance();
        mColorExtractor.setListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // We can't directly use isAttachedToWindow() here, as this is called before the internal
        // state is updated. So isAttachedToWindow() will return true until next frame.
        mIsAttachedToWindow = false;
        checkIfAutoAdvance();
        mColorExtractor.setListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    @Override
    public AppWidgetProviderInfo getAppWidgetInfo() {
        AppWidgetProviderInfo info = super.getAppWidgetInfo();
        if (info != null && !(info instanceof LauncherAppWidgetProviderInfo)) {
            throw new IllegalStateException("Launcher widget must have"
                    + " LauncherAppWidgetProviderInfo");
        }
        return info;
    }

    @Override
    public void onTouchComplete() {
        if (!mLongPressHelper.hasPerformedLongPress()) {
            // If a long press has been performed, we don't want to clear the record of that since
            // we still may be receiving a touch up which we want to intercept
            mLongPressHelper.cancelLongPress();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mIsScrollable = checkScrollableRecursively(this);

        if (!mIsInDragMode && getTag() instanceof LauncherAppWidgetInfo) {
            LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) getTag();
            mTempRect.set(left, top, right, bottom);
            mColorExtractor.setWorkspaceLocation(mTempRect, (View) getParent(), info.screenId);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mIsInDragMode && mDragContentWidth > 0 && mDragContentHeight > 0
                && getChildCount() == 1) {
            measureChild(getChildAt(0), MeasureSpec.getSize(mDragContentWidth),
                    MeasureSpec.getSize(mDragContentHeight));
        }
    }

    /** Starts the drag mode. */
    public void startDrag() {
        mIsInDragMode = true;
        // In the case of dragging a scaled preview from widgets picker, we should reuse the
        // previously measured dimension from WidgetCell#measureAndComputeWidgetPreviewScale, which
        // measures the dimension of a widget preview without its parent's bound before scaling
        // down.
        if ((getScaleX() != 1f || getScaleY() != 1f) && getChildCount() == 1) {
            mDragContentWidth = getChildAt(0).getMeasuredWidth();
            mDragContentHeight = getChildAt(0).getMeasuredHeight();
        }
    }

    /** Handles a drag event occurred on a workspace page corresponding to the {@code screenId}. */
    public void handleDrag(Rect rectInView, View view, int screenId) {
        if (mIsInDragMode) {
            mColorExtractor.setWorkspaceLocation(rectInView, view, screenId);
        }
    }

    /** Ends the drag mode. */
    public void endDrag() {
        mIsInDragMode = false;
        mDragContentWidth = 0;
        mDragContentHeight = 0;
        requestLayout();
    }

    @Override
    public void onColorsChanged(SparseIntArray colors) {
        if (isDeferringUpdates()) {
            mDeferredColorChange = colors;
            mHasDeferredColorChange = true;
            return;
        }
        mDeferredColorChange = null;
        mHasDeferredColorChange = false;

        // setColorResources will reapply the view, which must happen in the UI thread.
        post(() -> setColorResources(colors));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(getClass().getName());
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        maybeRegisterAutoAdvance();
    }

    private void checkIfAutoAdvance() {
        boolean isAutoAdvance = false;
        Advanceable target = getAdvanceable();
        if (target != null) {
            isAutoAdvance = true;
            target.fyiWillBeAdvancedByHostKThx();
        }

        boolean wasAutoAdvance = sAutoAdvanceWidgetIds.indexOfKey(getAppWidgetId()) >= 0;
        if (isAutoAdvance != wasAutoAdvance) {
            if (isAutoAdvance) {
                sAutoAdvanceWidgetIds.put(getAppWidgetId(), true);
            } else {
                sAutoAdvanceWidgetIds.delete(getAppWidgetId());
            }
            maybeRegisterAutoAdvance();
        }
    }

    private Advanceable getAdvanceable() {
        AppWidgetProviderInfo info = getAppWidgetInfo();
        if (info == null || info.autoAdvanceViewId == NO_ID || !mIsAttachedToWindow) {
            return null;
        }
        View v = findViewById(info.autoAdvanceViewId);
        return (v instanceof Advanceable) ? (Advanceable) v : null;
    }

    private void maybeRegisterAutoAdvance() {
        Handler handler = getHandler();
        boolean shouldRegisterAutoAdvance = getWindowVisibility() == VISIBLE && handler != null
                && (sAutoAdvanceWidgetIds.indexOfKey(getAppWidgetId()) >= 0);
        if (shouldRegisterAutoAdvance != mIsAutoAdvanceRegistered) {
            mIsAutoAdvanceRegistered = shouldRegisterAutoAdvance;
            if (mAutoAdvanceRunnable == null) {
                mAutoAdvanceRunnable = this::runAutoAdvance;
            }

            handler.removeCallbacks(mAutoAdvanceRunnable);
            scheduleNextAdvance();
        }
    }

    private void scheduleNextAdvance() {
        if (!mIsAutoAdvanceRegistered) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long advanceTime = now + (ADVANCE_INTERVAL - (now % ADVANCE_INTERVAL)) +
                ADVANCE_STAGGER * sAutoAdvanceWidgetIds.indexOfKey(getAppWidgetId());
        Handler handler = getHandler();
        if (handler != null) {
            handler.postAtTime(mAutoAdvanceRunnable, advanceTime);
        }
    }

    private void runAutoAdvance() {
        Advanceable target = getAdvanceable();
        if (target != null) {
            target.advance();
        }
        scheduleNextAdvance();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Only reinflate when the final configuration is same as the required configuration
        if (mReinflateOnConfigChange && isSameOrientation()) {
            mReinflateOnConfigChange = false;
            reInflate();
        }
    }

    public void reInflate() {
        if (!isAttachedToWindow()) {
            return;
        }
        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) getTag();
        if (info == null) {
            // This occurs when LauncherAppWidgetHostView is used to render a preview layout.
            return;
        }
        // Remove and rebind the current widget (which was inflated in the wrong
        // orientation), but don't delete it from the database
        mLauncher.removeItem(this, info, false  /* deleteFromDb */,
                "widget removed because of configuration change");
        mLauncher.bindAppWidget(info);
    }

    @Override
    protected boolean shouldAllowDirectClick() {
        if (getTag() instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) getTag();
            return item.spanX == 1 && item.spanY == 1;
        }
        return false;
    }
}
