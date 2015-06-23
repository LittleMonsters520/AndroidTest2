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

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This is the backup/restore agent class for the BackupRestore sample
 * application.  This particular agent illustrates using the backup and
 * restore APIs directly, without taking advantage of any helper classes.
 */
 //这是备份/恢复的代理类BackupRestore样品 　　
 //*应用程序。这个特殊的代理说明了备份和使用 　　
 //*恢复api直接,没有任何助手类的利用。
public class ExampleAgent extends BackupAgent {
    /**
     * We put a simple version number into the state files so that we can
     * tell properly how to read "old" versions if at some point we want
     * to change what data we back up and how we store the state blob.
     */
	 //我们把一个简单的版本号为国家文件,
	 //以便我们正确*能告诉如何阅读“旧”版本如果在某种程度上我们想要改变我们的数据备份和我们如何blob存储状态。
    static final int AGENT_VERSION = 1;

    /**
     * Pick an arbitrary string to use as the "key" under which the
     * data is backed up.  This key identifies different data records
     * within this one application's data set.  Since we only maintain
     * one piece of data we don't need to distinguish, so we just pick
     * some arbitrary tag to use. 
     */
	 //选择一个任意字符串作为“关键”的*数据备份。
	 //这个键标识不同的数据记录*在一个应用程序的数据集,
	 //因为我们只维护*一块数据我们不需要区分,
    static final String APP_DATA_KEY = "alldata";

    /** The app's current data, read from the live disk file */
	//应用程序的当前数据,读取磁盘文件
    boolean mAddMayo;
    boolean mAddTomato;
    int mFilling;

    /** The location of the application's persistent data file */
	//应用程序的持久性数据文件的位置
    File mDataFile;

    /** For convenience, we set up the File object for the app's data on creation */
	//为了方便起见,我们设置文件对象用于创建应用程序的数据
    @Override
    public void onCreate() {
        mDataFile = new File(getFilesDir(), BackupRestoreActivity.DATA_FILE_NAME);
    }

    /**
     * The set of data backed up by this application is very small: just
     * two booleans and an integer.  With such a simple dataset, it's
     * easiest to simply store a copy of the backed-up data as the state
     * blob describing the last dataset backed up.  The state file
     * contents can be anything; it is private to the agent class, and
     * is never stored off-device.
     *
     * <p>One thing that an application may wish to do is tag the state
     * blob contents with a version number.  This is so that if the
     * application is upgraded, the next time it attempts to do a backup,
     * it can detect that the last backup operation was performed by an
     * older version of the agent, and might therefore require different
     * handling.
     */
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        // First, get the current data from the application's file.  This
        // may throw an IOException, but in that case something has gone
        // badly wrong with the app's data on disk, and we do not want
        // to back up garbage data.  If we just let the exception go, the
        // Backup Manager will handle it and simply skip the current
        // backup operation.
		//首先,获取当前应用程序的数据文件。
		//这 　　/ /可能抛出IOException,但在这种情况下了 　　/ /
		//严重错误的应用程序的数据在磁盘上,我们不希望 　　/ /
		//备份垃圾数据。如果我们只是l
        synchronized (BackupRestoreActivity.sDataLock) {
            RandomAccessFile file = new RandomAccessFile(mDataFile, "r");
            mFilling = file.readInt();
            mAddMayo = file.readBoolean();
            mAddTomato = file.readBoolean();
        }

        // If the new state file descriptor is null, this is the first time
        // a backup is being performed, so we know we have to write the
        // data.  If there <em>is</em> a previous state blob, we want to
        // double check whether the current data is actually different from
        // our last backup, so that we can avoid transmitting redundant
        // data to the storage backend.
		//如果新状态文件描述符为空,这是第一次/ /
		//执行备份时,我们知道我们必须写/ /数据。
		//如果< em > < / em > blob之前的状态,我们想/ /检查当前是否达两倍
        boolean doBackup = (oldState == null);
        if (!doBackup) {
            doBackup = compareStateFile(oldState);
        }

        // If we decided that we do in fact need to write our dataset, go
        // ahead and do that.  The way this agent backs up the data is to
        // flatten it into a single buffer, then write that to the backup
        // transport under the single key string.
		//如果我们决定,我们实际上需要编写我们的数据集,/ /
		//提前去这样做。这个代理的方式备份数据/ /
		//平它到一个缓冲区,然后写单下的备份/ /传输关键str
        if (doBackup) {
            ByteArrayOutputStream bufStream = new ByteArrayOutputStream();

            // We use a DataOutputStream to write structured data into
            // the buffering stream
			//我们使用DataOutputStream结构化数据写入/ /缓冲流
            DataOutputStream outWriter = new DataOutputStream(bufStream);
            outWriter.writeInt(mFilling);
            outWriter.writeBoolean(mAddMayo);
            outWriter.writeBoolean(mAddTomato);

            // Okay, we've flattened the data for transmission.  Pull it
            // out of the buffering stream object and send it off.
			//好吧,我们已经夷为平地的数据传输。
			//把它/ /缓冲流的对象并将其发送。
            byte[] buffer = bufStream.toByteArray();
            int len = buffer.length;
            data.writeEntityHeader(APP_DATA_KEY, len);
            data.writeEntityData(buffer, len);
        }

        // Finally, in all cases, we need to write the new state blob
		//最后,在所有情况下,我们需要编写新的国家blob
        writeStateFile(newState);
    }

    /**
     * Helper routine - read a previous state file and decide whether to
     * perform a backup based on its contents.
     *
     * @return <code>true</code> if the application's data has changed since
     *   the last backup operation; <code>false</code> otherwise.
     */
    boolean compareStateFile(ParcelFileDescriptor oldState) {
        FileInputStream instream = new FileInputStream(oldState.getFileDescriptor());
        DataInputStream in = new DataInputStream(instream);

        try {
            int stateVersion = in.readInt();
            if (stateVersion > AGENT_VERSION) {
                // Whoops; the last version of the app that backed up
                // data on this device was <em>newer</em> than the current
                // version -- the user has downgraded.  That's problematic.
                // In this implementation, we recover by simply rewriting
                // the backup.
				//哦,最后一个版本的应用程序,备份 　　/ /
				//数据在该设备上更新< em > < / em >比当前 　　/ /
				//版本——用户降级。这是有问题的。 　　/ /
				//在这个实现中,我们恢复
                return true;
            }

            // The state data we store is just a mirror of the app's data;
            // read it from the state file then return 'true' if any of
            // it differs from the current data.
			//我们商店只是一面镜子的状态数据应用的数据; 　　/ /
			//读它从国家文件然后返回如果任何“真正的” 　　/ /
			//它不同于当前数据。
            int lastFilling = in.readInt();
            boolean lastMayo = in.readBoolean();
            boolean lastTomato = in.readBoolean();

            return (lastFilling != mFilling)
                    || (lastTomato != mAddTomato)
                    || (lastMayo != mAddMayo);
        } catch (IOException e) {
            // If something went wrong reading the state file, be safe
            // and back up the data again.
			//如果阅读状态文件出现了错误,是安全的 　　/ /
			//再次和备份数据。
            return true;
        }
    }

    /**
     * Write out the new state file:  the version number, followed by the
     * three bits of data as we sent them off to the backup transport.
     */
	 //写出新的状态文件:版本号,其次是*三位数据,我们送他们去备份传输。
    void writeStateFile(ParcelFileDescriptor stateFile) throws IOException {
        FileOutputStream outstream = new FileOutputStream(stateFile.getFileDescriptor());
        DataOutputStream out = new DataOutputStream(outstream);

        out.writeInt(AGENT_VERSION);
        out.writeInt(mFilling);
        out.writeBoolean(mAddMayo);
        out.writeBoolean(mAddTomato);
    }

    /**
     * This application does not do any "live" restores of its own data,
     * so the only time a restore will happen is when the application is
     * installed.  This means that the activity itself is not going to
     * be running while we change its data out from under it.  That, in
     * turn, means that there is no need to send out any sort of notification
     * of the new data:  we only need to read the data from the stream
     * provided here, build the application's new data file, and then
     * write our new backup state blob that will be consulted at the next
     * backup operation.
     * 
     * <p>We don't bother checking the versionCode of the app who originated
     * the data because we have never revised the backup data format.  If
     * we had, the 'appVersionCode' parameter would tell us how we should
     * interpret the data we're about to read.
     */
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
            ParcelFileDescriptor newState) throws IOException {
        // We should only see one entity in the data stream, but the safest
        // way to consume it is using a while() loop
		//我们应该只看到一个实体的数据流,但最安全的/ /
		//消费方式是使用一段时间()循环
        while (data.readNextHeader()) {
            String key = data.getKey();
            int dataSize = data.getDataSize();

            if (APP_DATA_KEY.equals(key)) {
                // It's our saved data, a flattened chunk of data all in
                // one buffer.  Use some handy structured I/O classes to
                // extract it.
                byte[] dataBuf = new byte[dataSize];
                data.readEntityData(dataBuf, 0, dataSize);
                ByteArrayInputStream baStream = new ByteArrayInputStream(dataBuf);
                DataInputStream in = new DataInputStream(baStream);

                mFilling = in.readInt();
                mAddMayo = in.readBoolean();
                mAddTomato = in.readBoolean();

                // Now we are ready to construct the app's data file based
                // on the data we are restoring from.
				//现在我们准备构建应用程序的数据文件 　　/ /
				//数据我们正在恢复。
                synchronized (BackupRestoreActivity.sDataLock) {
                    RandomAccessFile file = new RandomAccessFile(mDataFile, "rw");
                    file.setLength(0L);
                    file.writeInt(mFilling);
                    file.writeBoolean(mAddMayo);
                    file.writeBoolean(mAddTomato);
                }
            } else {
                // Curious!  This entity is data under a key we do not
                // understand how to process.  Just skip it.
				//好奇!这个实体是数据在一个关键的我们/ /不清楚如何处理。直接跳过它。
                data.skipEntityData();
            }
        }

        // The last thing to do is write the state blob that describes the
        // app's data as restored from backup.
		//要做的最后一件事就是写国家blob描述 　　/ /
		//应用程序备份的数据恢复。
        writeStateFile(newState);
    }
}
