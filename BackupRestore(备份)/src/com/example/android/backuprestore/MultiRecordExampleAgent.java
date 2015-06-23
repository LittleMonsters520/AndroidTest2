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

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

/**
 * This agent implementation is similar to the {@link ExampleAgent} one, but
 * stores each distinct piece of application data in a separate record within
 * the backup data set.  These records are updated independently: if the user
 * changes the state of one of the UI's checkboxes, for example, only that
 * datum's backup record is updated, not the entire data file.
 */
public class MultiRecordExampleAgent extends BackupAgent {
    // Key strings for each record in the backup set
	//关键字符串中的每个记录的备份集
    static final String FILLING_KEY = "filling";
    static final String MAYO_KEY = "mayo";
    static final String TOMATO_KEY = "tomato";

    // Current live data, read from the application's data file
	//当前实时数据,读取应用程序的数据文件
    int mFilling;
    boolean mAddMayo;
    boolean mAddTomato;

    /** The location of the application's persistent data file */
	//应用程序的持久性数据文件的位置
    File mDataFile;

    @Override
    public void onCreate() {
        // Cache a File for the app's data
		//缓存文件应用程序的数据
        mDataFile = new File(getFilesDir(), BackupRestoreActivity.DATA_FILE_NAME);
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        // First, get the current data from the application's file.  This
        // may throw an IOException, but in that case something has gone
        // badly wrong with the app's data on disk, and we do not want
        // to back up garbage data.  If we just let the exception go, the
        // Backup Manager will handle it and simply skip the current
        // backup operation.
		//首先,获取当前应用程序的数据文件。这
		//可能抛出IOException,但在这种情况下了 　　
		//严重错误的应用程序的数据在磁盘上,我们不希望 　　
		//备份垃圾数据。如果我们只是l
        synchronized (BackupRestoreActivity.sDataLock) {
            RandomAccessFile file = new RandomAccessFile(mDataFile, "r");
            mFilling = file.readInt();
            mAddMayo = file.readBoolean();
            mAddTomato = file.readBoolean();
        }

        // If this is the first backup ever, we have to back up everything
		//如果这是第一次备份,我们得备份一切
        boolean forceBackup = (oldState == null);

        // Now read the state as of the previous backup pass, if any
		//现在读的国家以前的备份,如果任何
        int lastFilling = 0;
        boolean lastMayo = false;
        boolean lastTomato = false;

        if (!forceBackup) {

            FileInputStream instream = new FileInputStream(oldState.getFileDescriptor());
            DataInputStream in = new DataInputStream(instream);

            try {
                // Read the state as of the last backup
				//读取上次备份的状态
                lastFilling = in.readInt();
                lastMayo = in.readBoolean();
                lastTomato = in.readBoolean();
            } catch (IOException e) {
                // If something went wrong reading the state file, be safe and
                // force a backup of all the data again.
				//如果阅读状态文件出现了错误,安全的强制备份所有的数据。
                forceBackup = true;
            }
        }

        // Okay, now check each datum to see whether we need to back up a new value.  We'll
        // reuse the bytearray buffering stream for each datum.  We also use a little
        // helper routine to avoid some code duplication when writing the two boolean
        // records.
		//好了,现在检查每个数据,是否我们需要一个新值。
		//我们将重用中bytearray缓冲对每个数据流。
		//我们也使用辅助例程来避免一些代码重复写作时两个布尔
        ByteArrayOutputStream bufStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bufStream);

        if (forceBackup || (mFilling != lastFilling)) {
            // bufStream.reset();   // not necessary the first time, but good to remember
			                        //第一次没有必要,但好记住
            out.writeInt(mFilling);
            writeBackupEntity(data, bufStream, FILLING_KEY);
        }

        if (forceBackup || (mAddMayo != lastMayo)) {
            bufStream.reset();
            out.writeBoolean(mAddMayo);
            writeBackupEntity(data, bufStream, MAYO_KEY);
        }

        if (forceBackup || (mAddTomato != lastTomato)) {
            bufStream.reset();
            out.writeBoolean(mAddTomato);
            writeBackupEntity(data, bufStream, TOMATO_KEY);
        }

        // Finally, write the state file that describes our data as of this backup pass
		//最后,编写状态文件,描述了我们的数据的备份
        writeStateFile(newState);
    }

    /**
     * Write out the new state file:  the version number, followed by the
     * three bits of data as we sent them off to the backup transport.
     */
	 //写出新的状态文件:版本号,紧随其后的是 　　*三位的数据,我们送他们去备份传输。
    void writeStateFile(ParcelFileDescriptor stateFile) throws IOException {
        FileOutputStream outstream = new FileOutputStream(stateFile.getFileDescriptor());
        DataOutputStream out = new DataOutputStream(outstream);

        out.writeInt(mFilling);
        out.writeBoolean(mAddMayo);
        out.writeBoolean(mAddTomato);
    }

    // Helper: write the boolean 'value' as a backup record under the given 'key',
    // reusing the given buffering stream & data writer objects to do so.
	//　助手:写布尔“价值”作为备份记录下给定的“关键”重用给定的缓冲流&数据作家对象。
    void writeBackupEntity(BackupDataOutput data, ByteArrayOutputStream bufStream, String key)
            throws IOException {
        byte[] buf = bufStream.toByteArray();
        data.writeEntityHeader(key, buf.length);
        data.writeEntityData(buf, buf.length);
    }

    /**
     * On restore, we pull the various bits of data out of the restore stream,
     * then reconstruct the application's data file inside the shared lock.  A
     * restore data set will always be the full set of records supplied by the
     * application's backup operations.
     */
	 //在恢复,我们将出恢复的各种数据位流,然后重构应用程序的数据文件在共享锁。总是会*恢复数据集提供的全套记录应用程序backu *
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
            ParcelFileDescriptor newState) throws IOException {

        // Consume the restore data set, remembering each bit of application state
        // that we see along the way
		//使用恢复数据集,记住每个应用程序状态我们看到;\
        while (data.readNextHeader()) {
            String key = data.getKey();
            int dataSize = data.getDataSize();

            // In this implementation, we trust that we won't see any record keys
            // that we don't understand.  Since we expect to handle them all, we
            // go ahead and extract the data for each record before deciding how
            // it will be handled.
			//在这个实现中,我们相信,我们不会看到任何记录键 　　/ /
			//我们不明白。因为我们希望处理它们,我们 　　/ /
			//继续为每一个记录,然后再决定如何提取数据 　　/ / h
            byte[] dataBuf = new byte[dataSize];
            data.readEntityData(dataBuf, 0, dataSize);
            ByteArrayInputStream instream = new ByteArrayInputStream(dataBuf);
            DataInputStream in = new DataInputStream(instream);

            if (FILLING_KEY.equals(key)) {
                mFilling = in.readInt();
            } else if (MAYO_KEY.equals(key)) {
                mAddMayo = in.readBoolean();
            } else if (TOMATO_KEY.equals(key)) {
                mAddTomato = in.readBoolean();
            }
        }

        // Now we're ready to write out a full new dataset for the application.  Note that
        // the restore process is intended to *replace* any existing or default data, so
        // we can just go ahead and overwrite it all.
		//现在我们准备写出一个完整的新数据集的应用程序。请注意, 　　/ /
		//恢复过程是为了* *替换任何现有的或默认的数据,所以 　　/ /
		//我们可以继续,覆盖一切。
        synchronized (BackupRestoreActivity.sDataLock) {
            RandomAccessFile file = new RandomAccessFile(mDataFile, "rw");
            file.setLength(0L);
            file.writeInt(mFilling);
            file.writeBoolean(mAddMayo);
            file.writeBoolean(mAddTomato);
        }

        // Finally, write the state file that describes our data as of this restore pass.
		//最后,写的状态文件,描述了我们的数据恢复
        writeStateFile(newState);
    }
}
