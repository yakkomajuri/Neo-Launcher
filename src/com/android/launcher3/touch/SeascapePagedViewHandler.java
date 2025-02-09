/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.touch;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.HORIZONTAL;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN;

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Pair;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.StagedSplitBounds;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Collections;
import java.util.List;

public class SeascapePagedViewHandler extends LandscapePagedViewHandler {

    @Override
    public int getSecondaryTranslationDirectionFactor() {
        return -1;
    }

    @Override
    public int getSplitTranslationDirectionFactor(int stagePosition, DeviceProfile deviceProfile) {
        if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return Utilities.isRtl(resources);
    }

    @Override
    public float getDegreesRotated() {
        return 270;
    }

    @Override
    public int getRotation() {
        return Surface.ROTATION_270;
    }

    @Override
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        float oldX = velocity.x;
        float oldY = velocity.y;
        velocity.set(oldY, -oldX);
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView, int overScroll,
                              DeviceProfile deviceProfile) {
        return x;
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int overScroll) {
        return y + overScroll +
                (thumbnailView.getMeasuredHeight() + thumbnailView.getMeasuredWidth()) / 2f;
    }

    @Override
    public void setTaskMenuAroundTaskView(LinearLayout taskView, float margin) {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) taskView.getLayoutParams();
        lp.bottomMargin += margin;
    }

    @Override
    public PointF getAdditionalInsetForTaskMenu(float margin) {
        return new PointF(-margin, margin);
    }


    @Override
    public Pair<Float, Float> getDwbLayoutTranslations(int taskViewWidth,
                                                       int taskViewHeight, StagedSplitBounds splitBounds, DeviceProfile deviceProfile,
                                                       View[] thumbnailViews, int desiredTaskId, View banner) {
        boolean isRtl = banner.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float translationX = 0;
        float translationY = 0;
        FrameLayout.LayoutParams bannerParams = (FrameLayout.LayoutParams) banner.getLayoutParams();
        banner.setPivotX(0);
        banner.setPivotY(0);
        banner.setRotation(getDegreesRotated());
        translationX = taskViewWidth - banner.getHeight();
        FrameLayout.LayoutParams snapshotParams =
                (FrameLayout.LayoutParams) thumbnailViews[0]
                        .getLayoutParams();
        bannerParams.gravity = BOTTOM | (isRtl ? END : START);

        if (splitBounds == null) {
            // Single, fullscreen case
            bannerParams.width = taskViewHeight - snapshotParams.topMargin;
            translationY = banner.getHeight();
            return new Pair<>(translationX, translationY);
        }

        // Set correct width
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            bannerParams.width = thumbnailViews[0].getMeasuredHeight();
        } else {
            bannerParams.width = thumbnailViews[1].getMeasuredHeight();
        }

        // Set translations
        if (desiredTaskId == splitBounds.rightBottomTaskId) {
            translationY = banner.getHeight();
        }
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            float bottomRightTaskPlusDividerPercent = splitBounds.appsStackedVertically
                    ? (1f - splitBounds.topTaskPercent)
                    : (1f - splitBounds.leftTaskPercent);
            translationY = banner.getHeight()
                    - ((taskViewHeight - snapshotParams.topMargin)
                    * bottomRightTaskPlusDividerPercent);
        }
        return new Pair<>(translationX, translationY);
    }

    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return dp.widthPx - rect.right;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        // Add "right" option which is actually the top
        return Collections.singletonList(new SplitPositionOption(
                R.drawable.ic_split_right, R.string.split_screen_position_right,
                STAGE_POSITION_BOTTOM_OR_RIGHT, STAGE_TYPE_MAIN));
    }

    @Override
    public void setTaskIconParams(FrameLayout.LayoutParams iconParams,
                                  int taskIconMargin, int taskIconHeight, int thumbnailTopMargin, boolean isRtl) {
        iconParams.gravity = (isRtl ? END : START) | CENTER_VERTICAL;
        iconParams.leftMargin = -taskIconHeight - taskIconMargin / 2;
        iconParams.rightMargin = 0;
        iconParams.topMargin = thumbnailTopMargin / 2;
    }

    @Override
    public void setSplitIconParams(View primaryIconView, View secondaryIconView,
                                   int taskIconHeight, int primarySnapshotWidth, int primarySnapshotHeight,
                                   int groupedTaskViewHeight, int groupedTaskViewWidth, boolean isRtl,
                                   DeviceProfile deviceProfile, StagedSplitBounds splitConfig) {
        super.setSplitIconParams(primaryIconView, secondaryIconView, taskIconHeight,
                primarySnapshotWidth, primarySnapshotHeight, groupedTaskViewHeight,
                groupedTaskViewWidth, isRtl, deviceProfile, splitConfig);
        FrameLayout.LayoutParams primaryIconParams =
                (FrameLayout.LayoutParams) primaryIconView.getLayoutParams();
        FrameLayout.LayoutParams secondaryIconParams =
                (FrameLayout.LayoutParams) secondaryIconView.getLayoutParams();

        // We calculate the "midpoint" of the thumbnail area, and place the icons there.
        // This is the place where the thumbnail area splits by default, in a near-50/50 split.
        // It is usually not exactly 50/50, due to insets/screen cutouts.
        int fullscreenInsetThickness = deviceProfile.getInsets().top;
        int fullscreenMidpointFromBottom = ((deviceProfile.heightPx
                - fullscreenInsetThickness) / 2);
        float midpointFromBottomPct = (float) fullscreenMidpointFromBottom / deviceProfile.heightPx;
        float insetPct = (float) fullscreenInsetThickness / deviceProfile.heightPx;
        int spaceAboveSnapshots = deviceProfile.overviewTaskThumbnailTopMarginPx;
        int overviewThumbnailAreaThickness = groupedTaskViewHeight - spaceAboveSnapshots;
        int bottomToMidpointOffset = (int) (overviewThumbnailAreaThickness * midpointFromBottomPct);
        int insetOffset = (int) (overviewThumbnailAreaThickness * insetPct);

        primaryIconParams.gravity = BOTTOM | (isRtl ? END : START);
        secondaryIconParams.gravity = BOTTOM | (isRtl ? END : START);
        primaryIconView.setTranslationX(0);
        secondaryIconView.setTranslationX(0);
        if (splitConfig.initiatedFromSeascape) {
            // if the split was initiated from seascape,
            // the task on the right (secondary) is slightly larger
            primaryIconView.setTranslationY(-bottomToMidpointOffset - insetOffset);
            secondaryIconView.setTranslationY(-bottomToMidpointOffset - insetOffset
                    + taskIconHeight);
        } else {
            // if not,
            // the task on the left (primary) is slightly larger
            primaryIconView.setTranslationY(-bottomToMidpointOffset);
            secondaryIconView.setTranslationY(-bottomToMidpointOffset + taskIconHeight);
        }

        primaryIconView.setLayoutParams(primaryIconParams);
        secondaryIconView.setLayoutParams(secondaryIconParams);
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    @Override
    public SingleAxisSwipeDetector.Direction getUpDownSwipeDirection() {
        return HORIZONTAL;
    }

    @Override
    public int getUpDirection(boolean isRtl) {
        return isRtl ? SingleAxisSwipeDetector.DIRECTION_POSITIVE
                : SingleAxisSwipeDetector.DIRECTION_NEGATIVE;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        return isRtl ? displacement > 0 : displacement < 0;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        return isRtl ? -1 : 1;
    }

    /* -------------------- */
}
