package com.hpp.daftree.syncmanagers;

import android.content.Context;

import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.hpp.daftree.utils.SecureLicenseManager;

public class SyncCoordinator {
    public static void triggerSyncWithCallback(Context context, Runnable onSuccessCallback) {
        boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();
        if(isGuest)  return;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager workManager = WorkManager.getInstance(context);
        String uniqueWorkName = "UNIQUE_SYNC_WORK";

        workManager.enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                syncRequest
        );

        // مراقبة النتيجة
        Observer<WorkInfo> observer = new Observer<WorkInfo>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    onSuccessCallback.run();
                    workManager.getWorkInfoByIdLiveData(syncRequest.getId()).removeObserver(this);
                } else if (workInfo != null && workInfo.getState().isFinished()) {
                    // حتى في حالة الفشل، نحذف المراقب
                    workManager.getWorkInfoByIdLiveData(syncRequest.getId()).removeObserver(this);
                }
            }
        };

        workManager.getWorkInfoByIdLiveData(syncRequest.getId()).observeForever(observer);
    }
}
