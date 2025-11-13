package com.hpp.daftree.syncmanagers;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hpp.daftree.NotificationService;
import com.hpp.daftree.R;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.notifications.NotificationHelper;
import com.hpp.daftree.utils.SecureLicenseManager;

public class PendingTxCheckWorker extends Worker {
    private static final String TAG = "PendingTxCheckWorker";

    public PendingTxCheckWorker(@NonNull Context ctx, @NonNull WorkerParameters params){super(ctx,params);}
    @NonNull @Override
    public Result doWork(){

        boolean isGuest = SecureLicenseManager.getInstance(getApplicationContext()).isGuest();
        if(isGuest)  return Result.success();
        TransactionUploadController controller=new TransactionUploadController(getApplicationContext());
        NotificationService notificationService = new NotificationService(getApplicationContext());
        if(controller.hasBlocked()) {
            String message = getApplicationContext().getString(R.string.daily_plan_msg);
            String title=getApplicationContext().getString(R.string.daily_plan_tit);

            DaftreeRepository repository = new DaftreeRepository((Application) getApplicationContext());
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
//                notificationService.showGeneralNotification(title, message, null);
//                controller.resetBlocked();
//                repository.triggerSync();
                NotificationHelper.get().showLocalNotification(title, message,
                        1001,true);
                repository.triggerSync();
            }

        }

        return Result.success();
    }

}

