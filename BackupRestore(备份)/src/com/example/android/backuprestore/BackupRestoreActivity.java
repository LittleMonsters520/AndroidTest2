/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.example.android.backuprestore;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.app.backup.RestoreObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This example is intended to demonstrate a few approaches that an Android
 * application developer can take when implementing a
 * {@link android.app.backup.BackupAgent BackupAgent}.  This feature, added
 * to the Android platform with API version 8, allows the application to
 * back up its data to a device-provided storage location, transparently to
 * the user.  If the application is uninstalled and then reinstalled, or if
 * the user starts using a new Android device, the backed-up information
 * can be provided automatically when the application is reinstalled.
 *
 * <p>Participating in the backup/restore mechanism is simple.  The application
 * provides a class that extends {@link android.app.backup.BackupAgent}, and
 * overrides the two core callback methods
 * {@link android.app.backup.BackupAgent#onBackup(android.os.ParcelFileDescriptor, android.app.backup.BackupDataOutput, android.os.ParcelFileDescriptor) onBackup()}
 * and
 * {@link android.app.backup.BackupAgent#onRestore(android.app.backup.BackupDataInput, int, android.os.ParcelFileDescriptor) onRestore()}.
 * It also publishes the agent class to the operating system by naming the class
 * with the <code>android:backupAgent</code> attribute of the
 * <code>&lt;application&gt;</code> tag in the application's manifest.
 * When a backup or restore operation is performed, the application's agent class
 * is instantiated within the application's execution context and the corresponding
 * method invoked.  Please see the documentation on the
 * {@link android.app.backup.BackupAgent BackupAgent} class for details about the
 * data interchange between the agent and the backup mechanism.
 *
 * <p>This example application maintains a few pieces of simple data, and provides
 * three different sample agent implementations, each illustrating an alternative
 * approach.  The three sample agent classes are:
 *
 * <p><ol type="1">
 * <li>{@link ExampleAgent} - this agent backs up the application's data in a single
 *     record.  It illustrates the direct "by hand" processes of saving backup state for
 *     future reference, sending data to the backup transport, and reading it from a restore
 *     dataset.</li>
 * <li>{@link FileHelperExampleAgent} - this agent takes advantage of the suite of
 *     helper classes provided along with the core BackupAgent API.  By extending
 *     {@link android.app.backup.BackupHelperAgent} and using the targeted
 *     {link android.app.backup.FileBackupHelper FileBackupHelper} class, it achieves
 *     the same result as {@link ExampleAgent} - backing up the application's saved
 *     data file in a single chunk, and restoring it upon request -- in only a few lines
 *     of code.</li>
 * <li>{@link MultiRecordExampleAgent} - this agent stores each separate bit of data
 *     managed by the UI in separate records within the backup dataset.  It illustrates
 *     how an application's backup agent can do selective updates of only what information
 *     has changed since the last backup.</li></ol>
 *
 * <p>You can build the application to use any of these agent implementations simply by
 * changing the class name supplied in the <code>android:backupAgent</code> manifest
 * attribute to indicate the agent you wish to use.  <strong>Note:</strong> the backed-up
 * data and backup-state tracking of these agents are not compatible!  If you change which
 * agent the application uses, you should also wipe the backup state associated with
 * the application on your handset.  The 'bmgr' shell application on the device can
 * do this; simply run the following command from your desktop computer while attached
 * to the device via adb:
 *
 * <p><code>adb shell bmgr wipe com.example.android.backuprestore</code>
 *
 * <p>You can then install the new version of the application, and its next backup pass
 * will start over from scratch with the new agent.
 */
public class BackupRestoreActivity extends Activity {
    static final String TAG = "BRActivity";

    /**
     * We serialize access to our persistent data through a global static
     * object.  This ensures that in the unlikely event of the our backup/restore
     * agent running to perform a backup while our UI is updating the file, the
     * agent will not accidentally read partially-written data.
     *
     * <p>Curious but true: a zero-length array is slightly lighter-weight than
     * merely allocating an Object, and can still be synchronized on.
     */
    static final Object[] sDataLock = new Object[0];

    /** Also supply a global standard file name for everyone to use */
	//也提供一个全球标准文件名称供大家使用
    static final String DATA_FILE_NAME = "saved_data";

    /** The various bits of UI that the user can manipulate */
	//不同的UI,用户可以操作
    RadioGroup mFillingGroup;
    CheckBox mAddMayoCheckbox;
    CheckBox mAddTomatoCheckbox;

    /** Cache a reference to our persistent data file */
	//缓存引用我们的持久数据文件
    File mDataFile;

    /** Also cache a reference to the Backup Manager */
	//缓存备份管理器的引用
    BackupManager mBackupManager;

    /** Set up the activity and populate its UI from the persistent data. */
	//设置活动和持久数据填充它的UI
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /** Establish the activity's UI */
		//建立活动的UI
        setContentView(R.layout.backup_restore);

        /** Once the UI has been inflated, cache the controls for later */
		//一旦UI被夸大,缓存的控制
        mFillingGroup = (RadioGroup) findViewById(R.id.filling_group);
        mAddMayoCheckbox = (CheckBox) findViewById(R.id.mayo);
        mAddTomatoCheckbox = (CheckBox) findViewById(R.id.tomato);

        /** Set up our file bookkeeping */
		//设置文件记账
        mDataFile = new File(getFilesDir(), BackupRestoreActivity.DATA_FILE_NAME);

        /** It is handy to keep a BackupManager cached */
		//它可以非常方便地保持BackupManager缓存
        mBackupManager = new BackupManager(this);

        /**
         * Finally, build the UI from the persistent store
         */
		 //最后,构建UI从持久存储
        populateUI();
    }

    /**
     * Configure the UI based on our persistent data, creating the
     * data file and establishing defaults if necessary.
     */
	 //配置用户界面基于我们的持久数据,创建*数据文件和建立违约,如果必要的话。
    void populateUI() {
        RandomAccessFile file;

        // Default values in case there's no data file yet
		//默认值,以防还没有数据文件
        int whichFilling = R.id.pastrami;
        boolean addMayo = false;
        boolean addTomato = false;

        /** Hold the data-access lock around access to the file */
		//数据访问锁在访问文件
        synchronized (BackupRestoreActivity.sDataLock) {
            boolean exists = mDataFile.exists();
            try {
                file = new RandomAccessFile(mDataFile, "rw");
                if (exists) {
                    Log.v(TAG, "datafile exists");
                    whichFilling = file.readInt();
                    addMayo = file.readBoolean();
                    addTomato = file.readBoolean();
                    Log.v(TAG, "  mayo=" + addMayo
                            + " tomato=" + addTomato
                            + " filling=" + whichFilling);
                } else {
                    // The default values were configured above: write them
                    // to the newly-created file.
					//上面的配置默认值是:写他们 　　/ /
					//新创建的文件。
                    Log.v(TAG, "creating default datafile");
                    writeDataToFileLocked(file,
                            addMayo, addTomato, whichFilling);

                    // We also need to perform an initial backup; ask for one
					//我们还需要执行一个初始备份;要求
                    mBackupManager.dataChanged();
                }
            } catch (IOException ioe) {
                
            }
        }

        /** Now that we've processed the file, build the UI outside the lock */
		//既然我们已经处理文件,构建用户界面外的锁
        mFillingGroup.check(whichFilling);
        mAddMayoCheckbox.setChecked(addMayo);
        mAddTomatoCheckbox.setChecked(addTomato);

        /**
         * We also want to record the new state when the user makes changes,
         * so install simple observers that do this
         */
		 //我们也想记录新状态当用户进行更改时, 　　*安装简单的观察家,做到这一点
        mFillingGroup.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    public void onCheckedChanged(RadioGroup group,
                            int checkedId) {
                        // As with the checkbox listeners, rewrite the
                        // entire state file
						//与听众的复选框,重写 　　/ /整个状态文件
                        Log.v(TAG, "New radio item selected: " + checkedId);
                        recordNewUIState();
                    }
                });

        CompoundButton.OnCheckedChangeListener checkListener
                = new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                // Whichever one is altered, we rewrite the entire UI state
				//哪一个是改变,我们重写整个UI状态
                Log.v(TAG, "Checkbox toggled: " + buttonView);
                recordNewUIState();
            }
        };
        mAddMayoCheckbox.setOnCheckedChangeListener(checkListener);
        mAddTomatoCheckbox.setOnCheckedChangeListener(checkListener);
    }

    /**
     * Handy helper routine to write the UI data to a file.
     */
	 //方便的辅助例程编写UI数据到一个文件。
    void writeDataToFileLocked(RandomAccessFile file,
            boolean addMayo, boolean addTomato, int whichFilling)
        throws IOException {
            file.setLength(0L);
            file.writeInt(whichFilling);
            file.writeBoolean(addMayo);
            file.writeBoolean(addTomato);
            Log.v(TAG, "NEW STATE: mayo=" + addMayo
                    + " tomato=" + addTomato
                    + " filling=" + whichFilling);
    }

    /**
     * Another helper; this one reads the current UI state and writes that
     * to the persistent store, then tells the backup manager that we need
     * a backup.
     */
	 //另一个助手;这一读当前的UI状态和写 　　*持久性存储,然后告诉我们需要的备份管理器 　　*一个备份。
    void recordNewUIState() {
        boolean addMayo = mAddMayoCheckbox.isChecked();
        boolean addTomato = mAddTomatoCheckbox.isChecked();
        int whichFilling = mFillingGroup.getCheckedRadioButtonId();
        try {
            synchronized (BackupRestoreActivity.sDataLock) {
                RandomAccessFile file = new RandomAccessFile(mDataFile, "rw");
                writeDataToFileLocked(file, addMayo, addTomato, whichFilling);
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to record new UI state");
        }

        mBackupManager.dataChanged();
    }

    /**
     * Click handler, designated in the layout, that runs a restore of the app's
     * most recent data when the button is pressed.
     */
	 //单击处理程序,指定的布局,恢复应用程序的运行 　　*最新数据,当按钮被按下。
    public void onRestoreButtonClick(View v) {
        Log.v(TAG, "Requesting restore of our most recent data");
        mBackupManager.requestRestore(
                new RestoreObserver() {
                    public void restoreFinished(int error) {
                        /** Done with the restore!  Now draw the new state of our data */
                        Log.v(TAG, "Restore finished, error = " + error);
                        populateUI();
                    }
                }
        );
    }
}
