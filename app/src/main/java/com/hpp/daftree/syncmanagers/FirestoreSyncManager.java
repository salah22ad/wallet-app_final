package com.hpp.daftree.syncmanagers;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.MainViewModel;
import com.hpp.daftree.R;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.hpp.daftree.MainActivity;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.models.DaftreeRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.RewardManager;
import com.hpp.daftree.utils.SecureLicenseManager;

public class FirestoreSyncManager {
    private static final String TAG = "FirestoreSyncManager";
    private static FirestoreSyncManager instance;
    private final FirebaseFirestore firestore;
    private ListenerRegistration accountsListener;
    private ListenerRegistration transactionsListener;
    private ListenerRegistration licenseListener;
    private DaftreeRepository repository;
    //    private  RewardManager rewardManager;
    private static String notiMessege = "";
    private static String notiMessegeTitel = "";
    private ReferralNotificationListener referralListener;
    private ListenerRegistration referralNotifListener;
    private final Map<String, Boolean> processedNotifs = new HashMap<>();
    // معالج لتأخير معالجة التغييرات
    private final Handler handler = new Handler(Looper.getMainLooper());
    // قوائم مؤقتة لتجميع التغييرات
    private List<Map<String, Object>> pendingAccountChanges = new ArrayList<>();
    private List<Map<String, Object>> pendingTransactionChanges = new ArrayList<>();
    private ListenerRegistration currenciesListener;
    private ListenerRegistration accountTypesListener;
    private ListenerRegistration usersListener;
    private RefreshMainActivity refreshMainActivity;
    private final Map<String, Long> lastKnownRewards = new HashMap<>();


    private List<Map<String, Object>> pendingCurrencyChanges = new ArrayList<>();
    private List<Map<String, Object>> pendingAccountTypeChanges = new ArrayList<>();
    private List<Map<String, Object>> pendingUserChanges = new ArrayList<>();

    // فترة التجميع (500 مللي ثانية)
    private static final long BATCH_DELAY_MS = 500;

    //    private  Application application;
    private FirestoreSyncManager() {
        this.firestore = FirebaseFirestore.getInstance();
        // rewardManager= new RewardManager();
    }

    public static synchronized FirestoreSyncManager getInstance() {
        if (instance == null) {
            instance = new FirestoreSyncManager();

        }
        return instance;
    }

    public void setReferralNotificationListener(ReferralNotificationListener listener) {
        this.referralListener = listener;
    }

    public void setRefreshMainActivity(RefreshMainActivity listener) {
        this.refreshMainActivity = listener;
    }

    /**
     * يبدأ الاستماع للتغييرات فقط.
     *
     * @param repo       المستودع المحلي
     * @param context    سياق التطبيق للحصول على الموارد
     * @param onComplete يُستدعى مباشرة بعد ربط المستمعين
     */
    public void startListening(DaftreeRepository repo, Context context, Runnable onComplete) {
        this.repository = repo;
        boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();
        if(isGuest) {
            if (onComplete != null) onComplete.run();
            return;
        }
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            Log.e(TAG, "المستخدم الحالي غير مسجل الدخول.");
            if (onComplete != null) onComplete.run();
            return;
        }

        // تنظيف أي مستمعين سابقين قبل إعادة الربط
        stopListening();
        int devicesNos = SecureLicenseManager.getInstance(context).getDevicesNos();
//        syncListenForReferralNotifications(userId, context, () -> {
        listenForReferralNotifications(userId, context);
//            listenForUserChanges(userId);
//        });
        if (devicesNos > 1) {


            syncAccountsFirst(userId, () -> {
                syncCurrencies(userId, () -> {
                    syncTransactions(userId, () -> {
                        listenForCurrencyChanges(userId);
                        listenForAccountChanges(userId);
                        listenForTransactionChanges(userId);
                        listenForAccountTypeChanges(userId);
                    });
                });
            });
        }
        Log.d(TAG, "تم تفعيل مستمعي المزامنة.");
        if (onComplete != null) onComplete.run();
    }

    private final AtomicLong lastReferralCheck = new AtomicLong(0L);


    private void checkPendingOperations(AtomicInteger pendingOperations, Runnable nextTask) {
        if (pendingOperations.decrementAndGet() == 0 && nextTask != null) {
            nextTask.run();
        }
    }

    public void listenForReferralNotifications12(String currentUserUid, Context context) {
        referralNotifListener = firestore.collection("referral_notifications")
                .whereEqualTo("targetUid", currentUserUid)
                .whereEqualTo("processed", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    AtomicLong latestTimestamp = new AtomicLong(lastReferralCheck.get());
                    SecureLicenseManager secureManager = SecureLicenseManager.getInstance(context);

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.ADDED) continue;
                        String notifId = dc.getDocument().getId();
                        String senderUid = dc.getDocument().getString("senderUid");
                        if (processedNotifs.containsKey(notifId)) continue;
                        processedNotifs.put(notifId, true);
                        Long createdAt = dc.getDocument().getLong("createdAt");
                        Log.e(TAG, " senderUid: " + senderUid + " createdAt: " + createdAt);
                        if (createdAt == null || createdAt <= lastReferralCheck.get()) continue;
                        Object pointsObj = dc.getDocument().get("points");
                        long points;
                        if (pointsObj instanceof Long) {
                            points = (Long) pointsObj;
                        } else if (pointsObj instanceof Double) {
                            points = ((Double) pointsObj).longValue();
                        } else {
                            Log.e(TAG, "حقل points مفقود أو غير صحيح للإشعار: " + notifId);
                            continue;
                        }

                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        DocumentReference userRef = db.collection("users").document(currentUserUid);

                        // 1. جلب بيانات المستخدم الحالية (لمعرفة آخر مكافأة حصل عليها)
                        userRef.get().addOnSuccessListener(userSnapshot -> {
                                    if (!userSnapshot.exists()) {
                                        return;
                                    }
                                    int currentReferrals = userSnapshot.contains("successfulReferrals") ? ((Number) userSnapshot.getLong("successfulReferrals")).intValue() : 0;

                                    userRef.update("referral_rewards", FieldValue.increment(points),
                                                    "successfulReferrals", FieldValue.increment(1),
                                                    "lastModified", System.currentTimeMillis())
                                            .addOnSuccessListener(aVoid -> {
                                                if (createdAt > latestTimestamp.get())
                                                    latestTimestamp.set(createdAt);

                                                Log.d("ReferralDebug", "إشعار جديد - النقاط: " + points + ", المُرسل: " + senderUid);

                                                // وسم الإشعار كمُعالج
                                                dc.getDocument().getReference().update("processed", true);
//                                                notiMessegeTitel="🎉 مكافأة الدعوة";
//                                                notiMessege="حصلت على " + points + " نقاط جديدة بفضل دخول مستخدم جديد بواسطة رابط دعوتك الخاص!"+"\n"+ " قم بدعوةأصدقاء اكثر لتحصل على النسخة الكاملة مجاناً.";
                                                notiMessege = context.getString(R.string.referral_reward_message, RewardManager.getConstNumber());

                                                // ---------------------- منطق أعطاء مكافئة 5 عمليات لكل 5 دعوات صديق ------------------
                                                int result = RewardManager.checkForMilestoneRewards(currentReferrals + 1);
                                                int newMilestoneCount = RewardManager.getCurrentCounter();
                                                Log.d("RewardManager", "currentReferrals: " + result + " ,result: " + result + " ,newMilestoneCount: " + newMilestoneCount + " ,CONST_NUMBER: " + RewardManager.CONST_NUMBER);
                                                if (newMilestoneCount == 2) {
//                                                    notiMessegeTitel="🎉 مكافأة الدعوة🎉";
//                                                    String msg="حصلت على " + points + " نقاط جديدة بفضل دخول مستخدم جديد بواسطة رابط دعوتك الخاص!"+"\n" + "🎁 مكافأة جديدة!"+"\n";
//                                                    notiMessege =msg+  "لقد وصل أصدقائك إلى " + newMilestoneCount + " صديقاً! شكراً لك، لقد حصلت على " + RewardManager.getConstNumber() + " عملية إضافية."+"\n"+ " قم بدعوةأصدقاء اكثر لتحصل على النسخة الكاملة مجاناً.";
                                                    notiMessege = context.getString(R.string.referral_reward_message, RewardManager.getConstNumber()) + "\n" +
                                                            context.getString(R.string.referral_reward_bonus_message, RewardManager.getConstNumber(), newMilestoneCount);

                                                    Log.d("RewardManager " + "🎁 مكافأة جديدة!", "لقد دعوت " + newMilestoneCount + " صديقاً! شكراً لك، لقد حصلت على " + RewardManager.getConstNumber() + " عملية إضافية.");
                                                    userRef.update("referral_rewards", FieldValue.increment(RewardManager.getConstNumber()),
                                                            "lastModified", System.currentTimeMillis());
                                                } else if (result == RewardManager.CONST_NUMBER) {
//                                                    notiMessegeTitel="🎉 مكافأة الدعوة🎉";
//                                                    String msg="حصلت على " + points + " نقاط جديدة بفضل دخول مستخدم جديد بواسطة رابط دعوتك الخاص!"+"\n" + "🎁 مكافأة جديدة!"+"\n";
//                                                    notiMessege =msg+  "لقد وصل أصدقائك " + newMilestoneCount + " صديقاً! شكراً لك، لقد حصلت على " + RewardManager.getConstNumber() + " عملية إضافية."+"\n"+ " قم بدعوةأصدقاء اكثر لتحصل على النسخة الكاملة مجاناً.";
                                                    notiMessege = context.getString(R.string.referral_reward_message, RewardManager.getConstNumber()) + "\n" +
                                                            context.getString(R.string.referral_reward_bonus_message, RewardManager.getConstNumber(), newMilestoneCount);

                                                    Log.d("RewardManager " + "🎁 مكافأة جديدة!", "لقد دعوت " + newMilestoneCount + " صديقاً! شكراً لك، لقد حصلت على " + RewardManager.getConstNumber() + " عملية إضافية.");
                                                    userRef.update("referral_rewards", FieldValue.increment(RewardManager.getConstNumber()),
                                                            "lastModified", System.currentTimeMillis());
                                                }
                                                notiMessegeTitel = context.getString(R.string.referral_reward_title);
                                                if (referralListener != null) {
                                                    referralListener.onReferralRewardReceived(currentUserUid, points, notiMessegeTitel, notiMessege);
                                                }
                                            })
                                            .addOnFailureListener(ex ->
                                                    Log.e(TAG, "Failed to update referrer rewards", ex));

                                })
                                .addOnFailureListener(ex ->
                                        Log.e(TAG, "Failed to check user rewards", ex));


                    }

                    // حفظ أحدث وقت تحقق بعد اكتمال المعالجة
                    if (latestTimestamp.get() > lastReferralCheck.get()) {
                        lastReferralCheck.set(latestTimestamp.get());
                    }
                });
    }

    public void syncListenForReferralNotifications(String currentUserUid, Context context, Runnable nextTask) {
        referralNotifListener = firestore.collection("referral_notifications")
                .whereEqualTo("targetUid", currentUserUid)
                .whereEqualTo("processed", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) {
                        if (nextTask != null) {
                            nextTask.run();
                        }
                        return;
                    }

                    AtomicLong latestTimestamp = new AtomicLong(lastReferralCheck.get());
                    SecureLicenseManager secureManager = SecureLicenseManager.getInstance(context);
                    AtomicInteger pendingOperations = new AtomicInteger(0);

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.ADDED) continue;
                        String notifId = dc.getDocument().getId();
                        String senderUid = dc.getDocument().getString("senderUid");
                        if (processedNotifs.containsKey(notifId)) continue;
                        processedNotifs.put(notifId, true);
                        Long createdAt = dc.getDocument().getLong("createdAt");
                        Log.e(TAG, " senderUid: " + senderUid + " createdAt: " + createdAt);
                        if (createdAt == null || createdAt <= lastReferralCheck.get()) continue;
                        Object pointsObj = dc.getDocument().get("points");
                        long points;
                        if (pointsObj instanceof Long) {
                            points = (Long) pointsObj;
                        } else if (pointsObj instanceof Double) {
                            points = ((Double) pointsObj).longValue();
                        } else {
                            Log.e(TAG, "حقل points مفقود أو غير صحيح للإشعار: " + notifId);
                            continue;
                        }

                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        DocumentReference userRef = db.collection("users").document(currentUserUid);

                        pendingOperations.incrementAndGet();

                        userRef.get().addOnSuccessListener(userSnapshot -> {
                            if (!userSnapshot.exists()) {
                                checkPendingOperations(pendingOperations, nextTask);
                                return;
                            }
                            AtomicInteger oldReferralRewards = new AtomicInteger();
                            int currentReferrals = userSnapshot.contains("successfulReferrals") ? ((Number) userSnapshot.getLong("successfulReferrals")).intValue() : 0;
                            oldReferralRewards.set(SecureLicenseManager.getInstance(context).getReferralRewards());
                            SecureLicenseManager.getInstance(context).setReferralRewards((int) (oldReferralRewards.get() + points));

                            userRef.update("referral_rewards", FieldValue.increment(points),
                                            "successfulReferrals", FieldValue.increment(1),
                                            "lastModified", System.currentTimeMillis())

                                    .addOnSuccessListener(aVoid -> {
                                        if (createdAt > latestTimestamp.get())
                                            latestTimestamp.set(createdAt);
                                        dc.getDocument().getReference().update("processed", true);
                                        notiMessegeTitel = "🎉 مكافأة الدعوة";
                                        notiMessege = "حصلت على " + points + " نقاط جديدة بفضل دخول مستخدم جديد بواسطة رابط دعوتك الخاص!" + "\n" + " قم بدعوةأصدقاء اكثر لتحصل على النسخة الكاملة مجاناً.";

                                        int result = RewardManager.checkForMilestoneRewards(currentReferrals);
                                        int newMilestoneCount = RewardManager.getCurrentCounter();
                                        Log.d("RewardManager", "currentReferrals: " + result + " ,result: " + result + " ,newMilestoneCount: " + newMilestoneCount + " ,CONST_NUMBER: " + RewardManager.CONST_NUMBER);
                                        if (newMilestoneCount == 2) {
//                                            oldReferralRewards.set(SecureLicenseManager.getInstance(context).getReferralRewards());
//                                            SecureLicenseManager.getInstance(context).setReferralRewards(oldReferralRewards.get() + RewardManager.getConstNumber());

                                            notiMessegeTitel = "🎉 مكافأة الدعوة🎉";
                                            String msg = "حصلت على " + points + " نقاط جديدة بفضل دخول مستخدم جديد بواسطة رابط دعوتك الخاص!" + "\n" + "🎁 مكافأة جديدة!" + "\n";
                                            notiMessege = msg + "لقد وصل أصدقائك إلى " + newMilestoneCount + " صديقاً! شكراً لك، لقد حصلت على " + RewardManager.getConstNumber() + " عملية إضافية." + "\n" + " قم بدعوةأصدقاء اكثر لتحصل على النسخة الكاملة مجاناً.";
                                            Log.d("RewardManager " + "🎁 مكافأة جديدة!", "لقد دعوت " + newMilestoneCount + " صديقاً! شكراً لك، لقد حصلت على " + RewardManager.getConstNumber() + " عملية إضافية.");
                                            userRef.update("referral_rewards", FieldValue.increment(RewardManager.getConstNumber()),
                                                    "lastModified", System.currentTimeMillis());
                                        } else if (result == RewardManager.CONST_NUMBER) {
//                                            oldReferralRewards.set(SecureLicenseManager.getInstance(context).getReferralRewards());
//                                            SecureLicenseManager.getInstance(context).setReferralRewards(oldReferralRewards.get() + RewardManager.getConstNumber());
                                            notiMessegeTitel = "🎉 مكافأة الدعوة🎉";
                                            String msg = "حصلت على " + points + " نقاط جديدة بفضل دخول مستخدم جديد بواسطة رابط دعوتك الخاص!" + "\n" + "🎁 مكافأة جديدة!" + "\n";
                                            notiMessege = msg + "لقد وصل أصدقائك " + newMilestoneCount + " صديقاً! شكراً لك، لقد حصلت على " + RewardManager.getConstNumber() + " عملية إضافية." + "\n" + " قم بدعوةأصدقاء اكثر لتحصل على النسخة الكاملة مجاناً.";
                                            userRef.update("referral_rewards", FieldValue.increment(RewardManager.getConstNumber()),
                                                    "lastModified", System.currentTimeMillis());
                                        }
                                        if (referralListener != null) {
                                            referralListener.onReferralRewardReceived(currentUserUid, points, notiMessegeTitel, notiMessege);
                                        }

                                        checkPendingOperations(pendingOperations, nextTask);
                                    })
                                    .addOnFailureListener(ex -> {
                                        Log.e(TAG, "Failed to update referrer rewards", ex);
                                        checkPendingOperations(pendingOperations, nextTask);
                                    });
                        }).addOnFailureListener(ex -> {
                            Log.e(TAG, "Failed to check user rewards", ex);
                            checkPendingOperations(pendingOperations, nextTask);
                        });
                    }

                    if (snapshots.getDocumentChanges().isEmpty()) {
                        if (nextTask != null) {
                            nextTask.run();
                        }
                    }
                });
    }


    public void listenForReferralNotifications(String currentUserUid, Context context) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        referralNotifListener = firestore.collection("referral_notifications")
                .whereEqualTo("targetUid", currentUserUid)
                .whereEqualTo("processed", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    Log.d(TAG, "New referral reward .");
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() != DocumentChange.Type.ADDED) continue;

                        String notifId = dc.getDocument().getId();
                        Log.d(TAG, "notifId: " + notifId);

                        if (processedNotifs.containsKey(notifId)) continue;
                        processedNotifs.put(notifId, true); // Mark as being processed immediately

                        long points = dc.getDocument().getLong("points") != null ? dc.getDocument().getLong("points") : 0;
                        if (points == 0) continue;
                        Log.d(TAG, "points: " + points);
                        DocumentReference userRef = firestore.collection("users").document(currentUserUid);
                        DocumentReference notifRef = dc.getDocument().getReference();

                        firestore.runTransaction(transaction -> {
                            DocumentSnapshot userSnapshot = transaction.get(userRef);

                            if (!userSnapshot.exists()) {
                                throw new IllegalStateException("User document does not exist!");
                            }

                            long currentSuccessfulReferrals = userSnapshot.contains("successfulReferrals") ?
                                    userSnapshot.getLong("successfulReferrals") : 0;
                            long currentReferrals = userSnapshot.contains("referral_rewards") ?
                                    userSnapshot.getLong("referral_rewards") : 0;
                            long newTotalReferrals = currentSuccessfulReferrals + 1;
                            Log.d(TAG, "currentReferrals" + currentReferrals);

                            // Calculate milestone bonus using the new static method
                            int milestoneBonus = RewardManager.checkForMilestoneRewards((int) newTotalReferrals);

                            long totalRewardForThisReferral = points + milestoneBonus;
                            long newReferrals = 0;


                            // Prepare notification messages
                            if (milestoneBonus > 0) {
                                notiMessegeTitel = context.getString(R.string.referral_reward_title); // "🎉 مكافأة إضافية!"
                                notiMessege = context.getString(R.string.referral_reward_bonus_message, (points), (milestoneBonus));
                                newReferrals = currentReferrals + 2 * points;
                            } else {
                                notiMessegeTitel = context.getString(R.string.referral_reward_title); // "🎉 مكافأة دعوة"
                                notiMessege = context.getString(R.string.referral_reward_message, (points));
                                newReferrals = currentReferrals + points;
                            }
// Update user's rewards and referral count
                            transaction.update(userRef, "referral_rewards", newReferrals);
                            transaction.update(userRef, "successfulReferrals", newTotalReferrals);
                            transaction.update(userRef, "lastModified", System.currentTimeMillis());

                            Log.d(TAG, "تم تحديث النقاط - المستخدم: " + currentUserUid + "\n" +
                                    ", currentReferrals: " + currentReferrals + "\n" +
                                    ", newReferrals: " + newReferrals);
                            // Mark notification as processed
                            transaction.update(notifRef, "processed", true);
                            return null;
                        }).addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully processed referral reward and updated user stats.");
                            if (referralListener != null) {
                                // Use the final calculated messages to show UI feedback
                                referralListener.onReferralRewardReceived(currentUserUid, points, notiMessegeTitel, notiMessege);
                            }
                        }).addOnFailureListener(ex -> {
                            Log.e(TAG, "Failed to process referral reward transaction.", ex);
                            processedNotifs.remove(notifId); // Allow reprocessing if transaction fails
                        });
                    }
                });
    }

    private void listenForUserChanges(String userId) {
        if (userId == null) return;

        usersListener = firestore.collection("users").document(userId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "User listen failed", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "تغيير لحظي في بيانات المستخدم: " + snapshot.getId());
                        Map<String, Object> data = snapshot.getData();

                        Map<String, Object> change = new HashMap<>();
                        change.put("firestoreId", snapshot.getId());
                        change.put("type", DocumentChange.Type.MODIFIED.name());
                        change.put("data", data);

                        pendingUserChanges.add(change);

                        handler.removeCallbacks(processUserChanges);
                        handler.postDelayed(processUserChanges, BATCH_DELAY_MS);
                    }
                });
    }

    private final Runnable processUserChanges = () -> {
        if (pendingUserChanges.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>(pendingUserChanges);
        pendingUserChanges.clear(); // مسح القائمة فوراً
        Log.d(TAG, "جاري معالجة " + batch.size() + " تغيير في بيانات المستخدم.");
        repository.batchUpsertUsers(batch);
    };

    private void listenForReferralChanges(String userId) {
        if (userId == null) return;

        // راقب مستند المستخدم نفسه
        DocumentReference userRef = firestore.collection("users").document(userId);
        usersListener = userRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) {
                Log.e(TAG, "Referral listen failed", e);
                return;
            }

            Long newPoints = snapshot.getLong("referral_rewards");
            Long lastPoints = lastKnownRewards.getOrDefault(userId, 0L);

            if (newPoints != null && newPoints > lastPoints) {
                lastKnownRewards.put(userId, newPoints);

                if (referralListener != null) {
                    referralListener.onReferralRewardReceived(userId, newPoints, "", "");
                }
            }
        });
    }

    public void startListening1(DaftreeRepository repository) {
        this.repository = repository;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (userId == null) return;

        listenForAccountChanges(userId);
        listenForTransactionChanges(userId);
    }

    private void listenForAccountChanges(String userId) {
//        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (userId == null) return;
        // الاستعلام للحسابات التي يملكها المستخدم الحالي
        Query query = firestore.collection("accounts")
                .whereEqualTo("ownerUID", userId);

        accountsListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Accounts listen failed", e);
                return;
            }

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();

                // تجهيز التغيير كخريطة
                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name()); // ADDED, MODIFIED, REMOVED
                change.put("data", data);
                // إضافة التغيير إلى القائمة المؤقتة
                pendingAccountChanges.add(change);
            }

            // إعادة جدولة المعالجة بعد فترة تأخير
            handler.removeCallbacks(processAccountChanges);
            handler.postDelayed(processAccountChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processAccountChanges = () -> {
        if (pendingAccountChanges.isEmpty()) return;

        // إرسال الدفعة الحالية وإعادة تهيئة القائمة المؤقتة
        List<Map<String, Object>> changesBatch = new ArrayList<>(pendingAccountChanges);
        pendingAccountChanges = new ArrayList<>();
        repository.batchUpsertAccounts(changesBatch);
    };

    private void listenForTransactionChanges(String userId) {
        if (userId == null) return;
        // الاستعلام للمعاملات التي يملكها المستخدم الحالي
        Query query = firestore.collection("transactions")
                .whereEqualTo("ownerUID", userId);

        transactionsListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Transactions listen failed", e);
                return;
            }

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();

                // تجهيز التغيير كخريطة
                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name()); // ADDED, MODIFIED, REMOVED
                change.put("data", data);

                // إضافة التغيير إلى القائمة المؤقتة
                pendingTransactionChanges.add(change);
            }

            // إعادة جدولة المعالجة بعد فترة تأخير
            handler.removeCallbacks(processTransactionChanges);
            handler.postDelayed(processTransactionChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processTransactionChanges = () -> {
        if (pendingTransactionChanges.isEmpty()) return;

        // إرسال الدفعة الحالية وإعادة تهيئة القائمة المؤقتة
        List<Map<String, Object>> changesBatch = new ArrayList<>(pendingTransactionChanges);
        pendingTransactionChanges = new ArrayList<>();
        repository.batchUpsertTransactions(changesBatch);
    };

    public void stopListening1() {
        if (accountsListener != null) accountsListener.remove();
        if (transactionsListener != null) transactionsListener.remove();
        handler.removeCallbacksAndMessages(null);
    }

    public void stopListening() {
        if (accountsListener != null) accountsListener.remove();
        if (transactionsListener != null) transactionsListener.remove();
        if (currenciesListener != null) currenciesListener.remove();
        if (accountTypesListener != null) accountTypesListener.remove();
        if (usersListener != null) usersListener.remove();
        if (licenseListener != null) licenseListener.remove();
        handler.removeCallbacksAndMessages(null);
    }

    public void startListening(DaftreeRepository repository, Context context) {
        this.repository = repository;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (userId == null) return;

//        // 1. مزامنة الحسابات أولاً
//        syncAccountsFirst(userId, () -> {
//            // 2. بعد اكتمال مزامنة الحسابات، نبدأ مزامنة المعاملات
//            syncTransactions(userId, () -> {
//                // 3. أخيراً، نبدأ الاستماع للتحديثات في الوقت الحقيقي
//                listenForAccountChanges(userId);
//                listenForTransactionChanges(userId);
//            });
//        });
        syncAccountsFirst(userId, () -> {
            syncCurrencies(userId, () -> {
                syncTransactions(userId, () -> {
                    listenForCurrencyChanges(userId);
                    listenForAccountChanges(userId);
                    listenForTransactionChanges(userId);
                    listenForAccountTypeChanges(userId);
                    syncListenForReferralNotifications(userId, context, () -> {
                        listenForReferralNotifications(userId, context);
                        listenForUserChanges(userId);
                    });
                });
            });
        });
    }

    private void syncAccountsFirst(String userId, Runnable onComplete) {
        Query query = firestore.collection("accounts")
                .whereEqualTo("ownerUID", userId)
                .orderBy("lastModified", Query.Direction.DESCENDING);

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Map<String, Object>> accountsBatch = new ArrayList<>();
                for (DocumentSnapshot doc : task.getResult()) {
                    Map<String, Object> change = new HashMap<>();
//                    change.put("type", "SYNCED");
                    change.put("firestoreId", doc.getId());
                    change.put("data", doc.getData());
                    accountsBatch.add(change);
                }
                repository.batchUpsertAccounts(accountsBatch);
                Log.d(TAG, "Initial accounts sync completed");
            } else {
                Log.e(TAG, "Initial accounts sync failed", task.getException());
            }
            onComplete.run();
        });
    }

    private void syncTransactions(String userId, Runnable onComplete) {
        Query query = firestore.collection("transactions")
                .whereEqualTo("ownerUID", userId)
                .orderBy("lastModified", Query.Direction.DESCENDING);

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Map<String, Object>> transactionsBatch = new ArrayList<>();
                for (DocumentSnapshot doc : task.getResult()) {
                    Map<String, Object> change = new HashMap<>();
//                    change.put("type", "SYNCED");
                    change.put("firestoreId", doc.getId());
                    change.put("data", doc.getData());
                    transactionsBatch.add(change);
                }
                repository.batchUpsertTransactions(transactionsBatch);
                Log.d(TAG, "Initial transactions sync completed");
            } else {
                Log.e(TAG, "Initial transactions sync failed", task.getException());
            }
            onComplete.run();
        });
    }

    private void syncCurrencies(String userId, Runnable onComplete) {
        Query query = firestore.collection("currencies")
                .whereEqualTo("ownerUID", userId);

        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Map<String, Object>> currencyBatch = new ArrayList<>();
                for (DocumentSnapshot doc : task.getResult()) {
                    Map<String, Object> change = new HashMap<>();
//                    change.put("type", "SYNCED");
                    change.put("firestoreId", doc.getId());
                    change.put("data", doc.getData());
                    currencyBatch.add(change);
                }
                repository.batchUpsertCurrencies(currencyBatch);
                Log.d(TAG, "Initial Currencies sync completed");
            } else {
                Log.e(TAG, "Initial Currencies sync failed", task.getException());
            }
            onComplete.run();
        });
    }

    private void listenForCurrencyChanges(String userId) {
        if (userId == null) return;
        Query query = firestore.collection("currencies")
                .whereEqualTo("ownerUID", userId);

        currenciesListener = query.addSnapshotListener((snapshots, e) -> {

            if (e != null) {
                Log.e(TAG, "Currencies listen failed", e);
                return;
            }

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();
                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name());
                change.put("data", data);
                pendingCurrencyChanges.add(change);
            }

            handler.removeCallbacks(processCurrencyChanges);
            handler.postDelayed(processCurrencyChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processCurrencyChanges = () -> {
        if (pendingCurrencyChanges.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>(pendingCurrencyChanges);
        pendingCurrencyChanges = new ArrayList<>();
        repository.batchUpsertCurrencies(batch);
    };

    private void listenForAccountTypeChanges(String userId) {
        if (userId == null) return;
        Query query = firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", userId);
        accountTypesListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "AccountTypes listen failed", e);
                return;
            }

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();
                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name());
                change.put("data", data);
                pendingAccountTypeChanges.add(change);
            }

            handler.removeCallbacks(processAccountTypeChanges);
            handler.postDelayed(processAccountTypeChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processAccountTypeChanges = () -> {
        if (pendingAccountTypeChanges.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>(pendingAccountTypeChanges);
        pendingAccountTypeChanges = new ArrayList<>();
        repository.batchUpsertAccountTypes(batch);
    };

    private void listenForUserChanges1(String userId) {
        if (userId == null) return;
        Query query = firestore.collection("users")
                .whereEqualTo("ownerUID", userId);
        usersListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Users listen failed", e);
                return;
            }
            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String firestoreId = dc.getDocument().getId();
                Map<String, Object> data = dc.getDocument().getData();

                Map<String, Object> change = new HashMap<>();
                change.put("firestoreId", firestoreId);
                change.put("type", dc.getType().name());
                change.put("data", data);
                pendingUserChanges.add(change);
            }

            handler.removeCallbacks(processUserChanges);
            handler.postDelayed(processUserChanges, BATCH_DELAY_MS);
        });
    }

    private final Runnable processUserChanges1 = () -> {
        if (pendingUserChanges.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>(pendingUserChanges);
        pendingUserChanges = new ArrayList<>();
        repository.batchUpsertUsers(batch);
    };
}