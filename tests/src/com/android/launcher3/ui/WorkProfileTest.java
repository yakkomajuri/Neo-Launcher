/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.ui;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsPagedView;
import com.android.launcher3.allapps.WorkEduCard;
import com.android.launcher3.allapps.WorkPausedCard;
import com.android.launcher3.allapps.WorkProfileManager;
import com.android.launcher3.tapl.LauncherInstrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;
import java.util.function.Predicate;

public class WorkProfileTest extends AbstractLauncherUiTest {

    private static final int WORK_PAGE = ActivityAllAppsContainerView.AdapterHolder.WORK;

    private int mProfileUserId;
    private boolean mWorkProfileSetupSuccessful;
    private final String TAG = "WorkProfileTest";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        String output =
                mDevice.executeShellCommand(
                        "pm create-user --profileOf 0 --managed TestProfile");
        // b/203817455
        updateWorkProfileSetupSuccessful("pm create-user", output);

        String[] tokens = output.split("\\s+");
        mProfileUserId = Integer.parseInt(tokens[tokens.length - 1]);
        output = mDevice.executeShellCommand("am start-user " + mProfileUserId);
        updateWorkProfileSetupSuccessful("am start-user", output);

        if (!mWorkProfileSetupSuccessful) {
            return; // no need to setup launcher since all tests will skip.
        }

        mDevice.pressHome();
        waitForLauncherCondition("Launcher didn't start", Objects::nonNull);
        waitForStateTransitionToEnd("Launcher internal state didn't switch to Normal",
                () -> NORMAL);
        waitForResumed("Launcher internal state is still Background");
        executeOnLauncher(launcher -> launcher.getStateManager().goToState(ALL_APPS));
        waitForStateTransitionToEnd("Launcher internal state didn't switch to All Apps",
                () -> ALL_APPS);
    }

    @After
    public void removeWorkProfile() throws Exception {
        mDevice.executeShellCommand("pm remove-user " + mProfileUserId);
    }

    @After
    public void resumeAppStoreUpdate() {
        executeOnLauncher(launcher -> {
            if (launcher == null || launcher.getAppsView() == null) {
                return;
            }
            launcher.getAppsView().getAppsStore().disableDeferUpdates(DEFER_UPDATES_TEST);
        });
    }

    private void waitForWorkTabSetup() {
        waitForLauncherCondition("Work tab not setup", launcher -> {
            if (launcher.getAppsView().getContentView() instanceof AllAppsPagedView) {
                launcher.getAppsView().getAppsStore().enableDeferUpdates(DEFER_UPDATES_TEST);
                return true;
            }
            return false;
        }, LauncherInstrumentation.WAIT_TIME_MS);
    }

    @Test
    public void workTabExists() {
        assumeTrue(mWorkProfileSetupSuccessful);
        waitForWorkTabSetup();
        waitForLauncherCondition("Personal tab is missing",
                launcher -> launcher.getAppsView().isPersonalTabVisible(),
                LauncherInstrumentation.WAIT_TIME_MS);
        waitForLauncherCondition("Work tab is missing",
                launcher -> launcher.getAppsView().isWorkTabVisible(),
                LauncherInstrumentation.WAIT_TIME_MS);
    }

    @Test
    public void toggleWorks() {
        assumeTrue(mWorkProfileSetupSuccessful);
        waitForWorkTabSetup();
        executeOnLauncher(launcher -> {
            AllAppsPagedView pagedView = (AllAppsPagedView) launcher.getAppsView().getContentView();
            pagedView.setCurrentPage(WORK_PAGE);
        });

        WorkProfileManager manager = getFromLauncher(l -> l.getAppsView().getWorkManager());


        waitForLauncherCondition("work profile initial state check failed", launcher ->
                        manager.getWorkModeSwitch() != null
                                && manager.getCurrentState() == WorkProfileManager.STATE_ENABLED
                                && manager.getWorkModeSwitch().isEnabled(),
                LauncherInstrumentation.WAIT_TIME_MS);

        //start work profile toggle OFF test
        executeOnLauncher(l -> {
            // Ensure updates are not deferred so notification happens when apps pause.
            l.getAppsView().getAppsStore().disableDeferUpdates(DEFER_UPDATES_TEST);
            l.getAppsView().getWorkManager().getWorkModeSwitch().performClick();
        });

        waitForLauncherCondition("Work profile toggle OFF failed", launcher -> {
            manager.reset(); // pulls current state from system
            return manager.getCurrentState() == WorkProfileManager.STATE_DISABLED;
        }, LauncherInstrumentation.WAIT_TIME_MS);

        waitForWorkCard("Work paused card not shown", view -> view instanceof WorkPausedCard);

        // start work profile toggle ON test
        executeOnLauncher(l -> {
            ActivityAllAppsContainerView<?> allApps = l.getAppsView();
            assertEquals("Work tab is not focused", allApps.getCurrentPage(), WORK_PAGE);
            View workPausedCard = allApps.getActiveRecyclerView()
                    .findViewHolderForAdapterPosition(0).itemView;
            workPausedCard.findViewById(R.id.enable_work_apps).performClick();
        });
        waitForLauncherCondition("Work profile toggle ON failed", launcher -> {
            manager.reset(); // pulls current state from system
            return manager.getCurrentState() == WorkProfileManager.STATE_ENABLED;
        }, LauncherInstrumentation.WAIT_TIME_MS);

    }

    @Test
    public void testEdu() {
        assumeTrue(mWorkProfileSetupSuccessful);
        waitForWorkTabSetup();
        executeOnLauncher(l -> {
            l.getSharedPrefs().edit().putInt(WorkProfileManager.KEY_WORK_EDU_STEP, 0).commit();
            ((AllAppsPagedView) l.getAppsView().getContentView()).setCurrentPage(WORK_PAGE);
            l.getAppsView().getWorkManager().reset();
        });

        waitForWorkCard("Work profile education not shown", view -> view instanceof WorkEduCard);
    }

    private void waitForWorkCard(String message, Predicate<View> workCardCheck) {
        waitForLauncherCondition(message, l -> {
            l.getAppsView().getAppsStore().disableDeferUpdates(DEFER_UPDATES_TEST);
            ViewHolder holder = l.getAppsView().getActiveRecyclerView()
                    .findViewHolderForAdapterPosition(0);
            try {
                return holder != null && workCardCheck.test(holder.itemView);
            } finally {
                l.getAppsView().getAppsStore().enableDeferUpdates(DEFER_UPDATES_TEST);
            }
        }, LauncherInstrumentation.WAIT_TIME_MS);
    }

    private void updateWorkProfileSetupSuccessful(String cli, String output) {
        Log.d(TAG, "updateWorkProfileSetupSuccessful, cli=" + cli + " " + "output=" + output);
        if (output.startsWith("Success")) {
            assertTrue(output, output.startsWith("Success"));
            mWorkProfileSetupSuccessful = true;
        } else {
            mWorkProfileSetupSuccessful = false;
        }
    }
}
