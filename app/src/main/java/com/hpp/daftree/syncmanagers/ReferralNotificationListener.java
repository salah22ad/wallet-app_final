package com.hpp.daftree.syncmanagers;

import androidx.lifecycle.ViewModel;

public interface ReferralNotificationListener {
    void onReferralRewardReceived(String userId, long newPoints,String notiMessegeTitel,String notiMessege);
}
