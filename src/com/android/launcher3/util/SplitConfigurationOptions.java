/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.launcher3.util;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.graphics.Rect;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;

public final class SplitConfigurationOptions {

    ///////////////////////////////////
    // Taken from
    // frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/splitscreen/SplitScreen.java
    /**
     * Stage position isn't specified normally meaning to use what ever it is currently set to.
     */
    public static final int STAGE_POSITION_UNDEFINED = -1;
    /**
     * Specifies that a stage is positioned at the top half of the screen if
     * in portrait mode or at the left half of the screen if in landscape mode.
     */
    public static final int STAGE_POSITION_TOP_OR_LEFT = 0;

    /**
     * Specifies that a stage is positioned at the bottom half of the screen if
     * in portrait mode or at the right half of the screen if in landscape mode.
     */
    public static final int STAGE_POSITION_BOTTOM_OR_RIGHT = 1;

    @Retention(SOURCE)
    @IntDef({STAGE_POSITION_UNDEFINED, STAGE_POSITION_TOP_OR_LEFT, STAGE_POSITION_BOTTOM_OR_RIGHT})
    public @interface StagePosition {
    }

    /**
     * Stage type isn't specified normally meaning to use what ever the default is.
     * E.g. exit split-screen and launch the app in fullscreen.
     */
    public static final int STAGE_TYPE_UNDEFINED = -1;
    /**
     * The main stage type.
     */
    public static final int STAGE_TYPE_MAIN = 0;

    /**
     * The side stage type.
     */
    public static final int STAGE_TYPE_SIDE = 1;

    @IntDef({STAGE_TYPE_UNDEFINED, STAGE_TYPE_MAIN, STAGE_TYPE_SIDE})
    public @interface StageType {
    }
    ///////////////////////////////////

    /**
     * Default split ratio for launching app pair from overview.
     */
    public static final float DEFAULT_SPLIT_RATIO = 0.5f;

    public static class SplitPositionOption {
        public final int iconResId;
        public final int textResId;
        @StagePosition
        public final int stagePosition;

        @StageType
        public final int mStageType;

        public SplitPositionOption(int iconResId, int textResId, int stagePosition, int stageType) {
            this.iconResId = iconResId;
            this.textResId = textResId;
            this.stagePosition = stagePosition;
            mStageType = stageType;
        }
    }

    /**
     * NOTE: Engineers complained about too little ambiguity in the last survey, so there is a class
     * with the same name/functionality in wm.shell.util (which launcher3 cannot be built against)
     * <p>
     * If you make changes here, consider making the same changes there
     */
    public static class StagedSplitBounds {
        public final Rect leftTopBounds;
        public final Rect rightBottomBounds;
        /** This rect represents the actual gap between the two apps */
        public final Rect visualDividerBounds;
        // This class is orientation-agnostic, so we compute both for later use
        public final float topTaskPercent;
        public final float leftTaskPercent;
        public final float dividerWidthPercent;
        public final float dividerHeightPercent;
        /**
         * If {@code true}, that means at the time of creation of this object, the
         * split-screened apps were vertically stacked. This is useful in scenarios like
         * rotation where the bounds won't change, but this variable can indicate what orientation
         * the bounds were originally in
         */
        public final boolean appsStackedVertically;
        /**
         * If {@code true}, that means at the time of creation of this object, the phone was in
         * seascape orientation. This is important on devices with insets, because they do not split
         * evenly -- one of the insets must be slightly larger to account for the inset.
         * From landscape, it is the leftTop task that expands slightly.
         * From seascape, it is the rightBottom task that expands slightly.
         */
        public final boolean initiatedFromSeascape;
        public final int leftTopTaskId;
        public final int rightBottomTaskId;

        public StagedSplitBounds(Rect leftTopBounds, Rect rightBottomBounds, int leftTopTaskId,
                                 int rightBottomTaskId) {
            this.leftTopBounds = leftTopBounds;
            this.rightBottomBounds = rightBottomBounds;
            this.leftTopTaskId = leftTopTaskId;
            this.rightBottomTaskId = rightBottomTaskId;

            if (rightBottomBounds.top > leftTopBounds.top) {
                // vertical apps, horizontal divider
                this.visualDividerBounds = new Rect(leftTopBounds.left, leftTopBounds.bottom,
                        leftTopBounds.right, rightBottomBounds.top);
                appsStackedVertically = true;
                initiatedFromSeascape = false;
            } else {
                // horizontal apps, vertical divider
                this.visualDividerBounds = new Rect(leftTopBounds.right, leftTopBounds.top,
                        rightBottomBounds.left, leftTopBounds.bottom);
                appsStackedVertically = false;
                // The following check is unreliable on devices without insets
                // (initiatedFromSeascape will always be set to false.) This happens to be OK for
                // all our current uses, but should be refactored.
                // TODO: Create a more reliable check, or refactor how splitting works on devices
                //  with insets.
                if (rightBottomBounds.width() > leftTopBounds.width()) {
                    initiatedFromSeascape = true;
                } else {
                    initiatedFromSeascape = false;
                }
            }

            float totalWidth = rightBottomBounds.right - leftTopBounds.left;
            float totalHeight = rightBottomBounds.bottom - leftTopBounds.top;
            leftTaskPercent = leftTopBounds.width() / totalWidth;
            topTaskPercent = leftTopBounds.height() / totalHeight;
            dividerWidthPercent = visualDividerBounds.width() / totalWidth;
            dividerHeightPercent = visualDividerBounds.height() / totalHeight;
        }
    }

    public static class StagedSplitTaskPosition {
        public int taskId = -1;
        @StagePosition
        public int stagePosition = STAGE_POSITION_UNDEFINED;
        @StageType
        public int stageType = STAGE_TYPE_UNDEFINED;
    }
}
