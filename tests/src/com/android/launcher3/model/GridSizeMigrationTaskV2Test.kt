/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherSettings.Favorites.*
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.model.GridSizeMigrationTaskV2.DbReader
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.*
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [GridSizeMigrationTaskV2]  */
@SmallTest
@RunWith(AndroidJUnit4::class)
class GridSizeMigrationTaskV2Test {
    private lateinit var modelHelper: LauncherModelHelper
    private lateinit var context: Context
    private lateinit var db: SQLiteDatabase
    private lateinit var validPackages: Set<String>
    private lateinit var idp: InvariantDeviceProfile
    private val testPackage1 = "com.android.launcher3.validpackage1"
    private val testPackage2 = "com.android.launcher3.validpackage2"
    private val testPackage3 = "com.android.launcher3.validpackage3"
    private val testPackage4 = "com.android.launcher3.validpackage4"
    private val testPackage5 = "com.android.launcher3.validpackage5"
    private val testPackage6 = "com.android.launcher3.validpackage6"
    private val testPackage7 = "com.android.launcher3.validpackage7"
    private val testPackage8 = "com.android.launcher3.validpackage8"
    private val testPackage9 = "com.android.launcher3.validpackage9"
    private val testPackage10 = "com.android.launcher3.validpackage10"

    @Before
    fun setUp() {
        modelHelper = LauncherModelHelper()
        context = modelHelper.sandboxContext
        db = modelHelper.provider.db

        validPackages = setOf(
            TEST_PACKAGE,
            testPackage1,
            testPackage2,
            testPackage3,
            testPackage4,
            testPackage5,
            testPackage6,
            testPackage7,
            testPackage8,
            testPackage9,
            testPackage10
        )

        idp = InvariantDeviceProfile.INSTANCE[context]
        val userSerial = UserCache.INSTANCE[context].getSerialNumberForUser(Process.myUserHandle())
        LauncherDbUtils.dropTable(db, TMP_TABLE)
        addTableToDb(db, userSerial, false, TMP_TABLE)
    }

    @After
    fun tearDown() {
        modelHelper.destroy()
    }

    /**
     * Old migration logic, should be modified once [FeatureFlags.ENABLE_NEW_MIGRATION_LOGIC] is
     * not needed anymore
     */
    @Test
    @Throws(Exception::class)
    fun testMigration() {
        // Src Hotseat icons
        modelHelper.addItem(APP_ICON, 0, HOTSEAT, 0, 0, testPackage1, 1, TMP_CONTENT_URI)
        modelHelper.addItem(SHORTCUT, 1, HOTSEAT, 0, 0, testPackage2, 2, TMP_CONTENT_URI)
        modelHelper.addItem(SHORTCUT, 3, HOTSEAT, 0, 0, testPackage3, 3, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 4, HOTSEAT, 0, 0, testPackage4, 4, TMP_CONTENT_URI)
        // Src grid icons
        // _ _ _ _ _
        // _ _ _ _ 5
        // _ _ 6 _ 7
        // _ _ 8 _ 9
        // _ _ _ _ _
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 4, 1, testPackage5, 5, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 2, testPackage6, 6, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 4, 2, testPackage7, 7, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 3, testPackage8, 8, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 4, 3, testPackage9, 9, TMP_CONTENT_URI)

        // Dest hotseat icons
        modelHelper.addItem(SHORTCUT, 1, HOTSEAT, 0, 0, testPackage2)
        // Dest grid icons
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 2, testPackage10)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        val task = GridSizeMigrationTaskV2(
            context,
            db,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows)
        )
        task.migrate(DeviceGridState(context), DeviceGridState(idp))

        // Check hotseat items
        var c = context.contentResolver.query(
            CONTENT_URI,
            arrayOf(SCREEN, INTENT),
            "container=$CONTAINER_HOTSEAT",
            null,
            SCREEN,
            null
        ) ?: throw IllegalStateException()

        assertThat(c.count).isEqualTo(idp.numDatabaseHotseatIcons)

        val screenIndex = c.getColumnIndex(SCREEN)
        var intentIndex = c.getColumnIndex(INTENT)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(0)
        assertThat(c.getString(intentIndex)).contains(testPackage1)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(1)
        assertThat(c.getString(intentIndex)).contains(testPackage2)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(2)
        assertThat(c.getString(intentIndex)).contains(testPackage3)
        c.moveToNext()
        assertThat(c.getInt(screenIndex).toLong()).isEqualTo(3)
        assertThat(c.getString(intentIndex)).contains(testPackage4)
        c.close()

        // Check workspace items
        c = context.contentResolver.query(
            CONTENT_URI,
            arrayOf(CELLX, CELLY, INTENT),
            "container=$CONTAINER_DESKTOP",
            null,
            null,
            null
        ) ?: throw IllegalStateException()

        intentIndex = c.getColumnIndex(INTENT)
        val cellXIndex = c.getColumnIndex(CELLX)
        val cellYIndex = c.getColumnIndex(CELLY)
        val locMap = HashMap<String, Point>()
        while (c.moveToNext()) {
            c.getString(intentIndex)?.let {
                locMap[it] = Point(c.getInt(cellXIndex), c.getInt(cellYIndex))
            }
            //locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
            //    Point(c.getInt(cellXIndex), c.getInt(cellYIndex))
        }
        c.close()
        // Expected dest grid icons
        // _ _ _ _
        // 5 6 7 8
        // 9 _ 10_
        // _ _ _ _
        assertThat(locMap.size.toLong()).isEqualTo(6)
        assertThat(locMap[testPackage5]).isEqualTo(Point(0, 1))
        assertThat(locMap[testPackage6]).isEqualTo(Point(1, 1))
        assertThat(locMap[testPackage7]).isEqualTo(Point(2, 1))
        assertThat(locMap[testPackage8]).isEqualTo(Point(3, 1))
        assertThat(locMap[testPackage9]).isEqualTo(Point(0, 2))
        assertThat(locMap[testPackage10]).isEqualTo(Point(2, 2))
    }

    @Test
    fun migrateToLargerHotseat() {
        val srcHotseatItems = intArrayOf(
            modelHelper.addItem(APP_ICON, 0, HOTSEAT, 0, 0, testPackage1, 1, TMP_CONTENT_URI),
            modelHelper.addItem(SHORTCUT, 1, HOTSEAT, 0, 0, testPackage2, 2, TMP_CONTENT_URI),
            modelHelper.addItem(APP_ICON, 2, HOTSEAT, 0, 0, testPackage3, 3, TMP_CONTENT_URI),
            modelHelper.addItem(SHORTCUT, 3, HOTSEAT, 0, 0, testPackage4, 4, TMP_CONTENT_URI)
        )
        val numSrcDatabaseHotseatIcons = srcHotseatItems.size
        idp.numDatabaseHotseatIcons = 6
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        val task = GridSizeMigrationTaskV2(
            context,
            db,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows)
        )
        task.migrate(DeviceGridState(context), DeviceGridState(idp))

        // Check hotseat items
        val c = context.contentResolver.query(
            CONTENT_URI,
            arrayOf(SCREEN, INTENT),
            "container=$CONTAINER_HOTSEAT",
            null,
            SCREEN,
            null
        ) ?: throw IllegalStateException()

        assertThat(c.count.toLong()).isEqualTo(numSrcDatabaseHotseatIcons.toLong())
        val screenIndex = c.getColumnIndex(SCREEN)
        val intentIndex = c.getColumnIndex(INTENT)
        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(0)
        assertThat(c.getString(intentIndex)).contains(testPackage1)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(1)
        assertThat(c.getString(intentIndex)).contains(testPackage2)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(2)
        assertThat(c.getString(intentIndex)).contains(testPackage3)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(3)
        assertThat(c.getString(intentIndex)).contains(testPackage4)

        c.close()
    }

    @Test
    fun migrateFromLargerHotseat() {
        modelHelper.addItem(APP_ICON, 0, HOTSEAT, 0, 0, testPackage1, 1, TMP_CONTENT_URI)
        modelHelper.addItem(SHORTCUT, 2, HOTSEAT, 0, 0, testPackage2, 2, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 3, HOTSEAT, 0, 0, testPackage3, 3, TMP_CONTENT_URI)
        modelHelper.addItem(SHORTCUT, 4, HOTSEAT, 0, 0, testPackage4, 4, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 5, HOTSEAT, 0, 0, testPackage5, 5, TMP_CONTENT_URI)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        val task = GridSizeMigrationTaskV2(
            context,
            db,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows)
        )
        task.migrate(DeviceGridState(context), DeviceGridState(idp))

        // Check hotseat items
        val c = context.contentResolver.query(
            CONTENT_URI,
            arrayOf(SCREEN, INTENT),
            "container=$CONTAINER_HOTSEAT",
            null,
            SCREEN,
            null
        ) ?: throw IllegalStateException()

        assertThat(c.count.toLong()).isEqualTo(idp.numDatabaseHotseatIcons.toLong())
        val screenIndex = c.getColumnIndex(SCREEN)
        val intentIndex = c.getColumnIndex(INTENT)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(0)
        assertThat(c.getString(intentIndex)).contains(testPackage1)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(1)
        assertThat(c.getString(intentIndex)).contains(testPackage2)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(2)
        assertThat(c.getString(intentIndex)).contains(testPackage3)

        c.moveToNext()
        assertThat(c.getInt(screenIndex)).isEqualTo(3)
        assertThat(c.getString(intentIndex)).contains(testPackage4)

        c.close()
    }

    /**
     * Migrating from a smaller grid to a large one should keep the pages
     * if the column difference is less than 2
     */
    @Test
    @Throws(Exception::class)
    fun migrateFromSmallerGridSmallDifference() {
        enableNewMigrationLogic("4,4")

        // Setup src grid
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 2, testPackage1, 5, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 2, 3, testPackage2, 6, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 1, DESKTOP, 3, 1, testPackage3, 7, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 1, DESKTOP, 3, 2, testPackage4, 8, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 2, DESKTOP, 3, 3, testPackage5, 9, TMP_CONTENT_URI)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 6
        idp.numRows = 5

        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        val task = GridSizeMigrationTaskV2(
            context,
            db,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows)
        )
        task.migrate(DeviceGridState(context), DeviceGridState(idp))

        // Get workspace items
        val c = context.contentResolver.query(
            CONTENT_URI,
            arrayOf(INTENT, SCREEN),
            "container=$CONTAINER_DESKTOP",
            null,
            null,
            null
        ) ?: throw IllegalStateException()
        val intentIndex = c.getColumnIndex(INTENT)
        val screenIndex = c.getColumnIndex(SCREEN)

        // Get in which screen the icon is
        val locMap = HashMap<String, Int>()
        while (c.moveToNext()) {
            c.getString(intentIndex)?.let { locMap[it] = c.getInt(screenIndex) }

            // locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
            //     c.getInt(screenIndex)
        }
        c.close()
        assertThat(locMap.size).isEqualTo(5)
        assertThat(locMap[testPackage1]).isEqualTo(0)
        assertThat(locMap[testPackage2]).isEqualTo(0)
        assertThat(locMap[testPackage3]).isEqualTo(1)
        assertThat(locMap[testPackage4]).isEqualTo(1)
        assertThat(locMap[testPackage5]).isEqualTo(2)

        disableNewMigrationLogic()
    }

    /**
     * Migrating from a smaller grid to a large one should reflow the pages
     * if the column difference is more than 2
     */
    @Test
    @Throws(Exception::class)
    fun migrateFromSmallerGridBigDifference() {
        enableNewMigrationLogic("2,2")

        // Setup src grid
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 0, 1, testPackage1, 5, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 1, 1, testPackage2, 6, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 1, DESKTOP, 0, 0, testPackage3, 7, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 1, DESKTOP, 1, 0, testPackage4, 8, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 2, DESKTOP, 0, 0, testPackage5, 9, TMP_CONTENT_URI)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 5
        idp.numRows = 5
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        val task = GridSizeMigrationTaskV2(
            context,
            db,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows)
        )
        task.migrate(DeviceGridState(context), DeviceGridState(idp))

        // Get workspace items
        val c = context.contentResolver.query(
            CONTENT_URI,
            arrayOf(INTENT, SCREEN),
            "container=$CONTAINER_DESKTOP",
            null,
            null,
            null
        ) ?: throw IllegalStateException()

        val intentIndex = c.getColumnIndex(INTENT)
        val screenIndex = c.getColumnIndex(SCREEN)

        // Get in which screen the icon is
        val locMap = HashMap<String, Int>()
        while (c.moveToNext()) {
            c.getString(intentIndex)?.let { locMap[it] = c.getInt(screenIndex) }
            //locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =
            //    c.getInt(screenIndex)
        }
        c.close()

        // All icons fit the first screen
        assertThat(locMap.size).isEqualTo(5)
        assertThat(locMap[testPackage1]).isEqualTo(0)
        assertThat(locMap[testPackage2]).isEqualTo(0)
        assertThat(locMap[testPackage3]).isEqualTo(0)
        assertThat(locMap[testPackage4]).isEqualTo(0)
        assertThat(locMap[testPackage5]).isEqualTo(0)
        disableNewMigrationLogic()
    }

    /**
     * Migrating from a larger grid to a smaller, we reflow from page 0
     */
    @Test
    @Throws(Exception::class)
    fun migrateFromLargerGrid() {
        enableNewMigrationLogic("5,5")

        // Setup src grid
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 0, 1, testPackage1, 5, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 0, DESKTOP, 1, 1, testPackage2, 6, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 1, DESKTOP, 0, 0, testPackage3, 7, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 1, DESKTOP, 1, 0, testPackage4, 8, TMP_CONTENT_URI)
        modelHelper.addItem(APP_ICON, 2, DESKTOP, 0, 0, testPackage5, 9, TMP_CONTENT_URI)

        idp.numDatabaseHotseatIcons = 4
        idp.numColumns = 4
        idp.numRows = 4
        val srcReader = DbReader(db, TMP_TABLE, context, validPackages)
        val destReader = DbReader(db, TABLE_NAME, context, validPackages)
        val task = GridSizeMigrationTaskV2(
            context,
            db,
            srcReader,
            destReader,
            idp.numDatabaseHotseatIcons,
            Point(idp.numColumns, idp.numRows)
        )
        task.migrate(DeviceGridState(context), DeviceGridState(idp))

        // Get workspace items
        val c = context.contentResolver.query(
            CONTENT_URI,
            arrayOf(INTENT, SCREEN),
            "container=$CONTAINER_DESKTOP",
            null,
            null,
            null
        ) ?: throw IllegalStateException()
        val intentIndex = c.getColumnIndex(INTENT)
        val screenIndex = c.getColumnIndex(SCREEN)

        // Get in which screen the icon is
        val locMap = HashMap<String, Int>()
        while (c.moveToNext()) {
            c.getString(intentIndex)?.let { locMap[it] = c.getInt(screenIndex) }
            //locMap[Intent.parseUri(c.getString(intentIndex), 0).getPackage()] =  c.getInt(screenIndex)
        }
        c.close()

        // All icons fit the first screen
        assertThat(locMap.size).isEqualTo(5)
        assertThat(locMap[testPackage1]).isEqualTo(0)
        assertThat(locMap[testPackage2]).isEqualTo(0)
        assertThat(locMap[testPackage3]).isEqualTo(0)
        assertThat(locMap[testPackage4]).isEqualTo(0)
        assertThat(locMap[testPackage5]).isEqualTo(0)

        disableNewMigrationLogic()
    }

    private fun enableNewMigrationLogic(srcGridSize: String) {
        context.getSharedPreferences(FeatureFlags.FLAGS_PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(FeatureFlags.ENABLE_NEW_MIGRATION_LOGIC.key, true)
            .commit()
        context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            .edit()
            .putString(DeviceGridState.KEY_WORKSPACE_SIZE, srcGridSize)
            .commit()
        FeatureFlags.initialize(context)
    }

    private fun disableNewMigrationLogic() {
        context.getSharedPreferences(FeatureFlags.FLAGS_PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(FeatureFlags.ENABLE_NEW_MIGRATION_LOGIC.key, false)
            .commit()
    }
}