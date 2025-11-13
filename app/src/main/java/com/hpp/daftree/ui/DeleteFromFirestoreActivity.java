package com.hpp.daftree.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.R;

import java.util.HashSet;
import java.util.Set;

public class DeleteFromFirestoreActivity extends AppCompatActivity {
    private EditText etOwnerUID;
    private Button btnDelete,btnUpdate;
    private TextView tvResult;

    private FirebaseFirestore firestore;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_delete_from_firestore);
        etOwnerUID = findViewById(R.id.etOwnerUID);
        btnDelete = findViewById(R.id.btnDelete);
        btnUpdate = findViewById(R.id.btnUpdate);
        tvResult = findViewById(R.id.tvResult);

        firestore = FirebaseFirestore.getInstance();

        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ownerUID = etOwnerUID.getText().toString().trim();

                if (ownerUID.isEmpty()) {
                    Toast.makeText(DeleteFromFirestoreActivity.this, "الرجاء إدخال OwnerUID", Toast.LENGTH_SHORT).show();
                } else {
                   tvResult.setText("");
                    deleteUserDataExceptTransactions(ownerUID);
//                    deleteByOwnerUID("transactions",ownerUID);
//                    deleteByOwnerUID("accountTypes",ownerUID);
//                    deleteByOwnerUID("currencies",ownerUID);
//                    deleteByOwnerUID("accounts",ownerUID);
//                    deleteByOwnerUID("users",ownerUID);
                }
            }
        });
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String ownerUID = FirebaseAuth.getInstance().getCurrentUser().getUid();

                if (ownerUID.isEmpty()) {
                    Toast.makeText(DeleteFromFirestoreActivity.this, "خطا في الحساب ", Toast.LENGTH_SHORT).show();
                } else {
                    validateAndFixCurrencyIds(ownerUID);
                }
            }
        });
    }

    private void deleteByOwnerUID2(String collectionName ,String ownerUID) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        Log.d("Firestore", "Current UID = " + currentUid + ", ownerUID = " + ownerUID);
        firestore.collection(collectionName)
                .whereEqualTo("ownerUID", ownerUID)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        WriteBatch batch = firestore.batch();
                        Log.e("Firestore"," عدد البيانات في جدول " + collectionName + ": "+ queryDocumentSnapshots.size());
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    String msg = "تم حذف كل العمليات الخاصة ب  " + collectionName;
                                    Log.d("Firestore", msg);
                                    tvResult.setText(msg);
                                })
                                .addOnFailureListener(e -> {
                                    String msg = "فشل في الحذف: " + e.getMessage();
                                    Log.e("Firestore", msg, e);
                                    tvResult.setText(msg);
                                });
                    } else {
                        String msg =  "لا توجد عمليات للحذف لهذا الـ ownerUID";
                        Log.d("Firestore", msg);
                        tvResult.setText(msg);
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = "خطأ في الاستعلام: " + e.getMessage();
                    Log.e("Firestore", msg, e);
                    tvResult.setText(msg);
                });
    }
    private void deleteUserDataExceptTransactions(String ownerUID) {
        String[] collections = {"transactions", "accounts", "accountTypes", "currencies","users"};
        String currentUid = FirebaseAuth.getInstance().getUid();

        for (String collectionName : collections) {
            firestore.collection(collectionName)
                    .whereEqualTo("ownerUID", ownerUID)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            WriteBatch batch = firestore.batch();
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                batch.delete(doc.getReference());
                            }
                            batch.commit()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("Firestore", "تم حذف البيانات من: " + collectionName);
                                        tvResult.append("\nتم حذف البيانات من: " + collectionName);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Firestore", "فشل الحذف من " + collectionName + ": " + e.getMessage());
                                        tvResult.append("\nفشل الحذف من " + collectionName);
                                    });
                        } else {
                            Log.d("Firestore", "لا توجد بيانات للحذف في: " + collectionName);
                            tvResult.append("\nلا توجد بيانات في: " + collectionName);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Firestore", "خطأ في الاستعلام من " + collectionName + ": " + e.getMessage());
                        tvResult.append("\nخطأ في الاستعلام من " + collectionName);
                    });
        }
    }

    private void deleteByOwnerUID(String collectionName, String ownerUID) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        Log.d("Firestore", "Current UID = " + currentUid + ", ownerUID = " + ownerUID);

        // أولاً: تحقق هل المستخدم الحالي مدير
        firestore.collection("users").document(currentUid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    boolean isAdmin;
                    if (userDoc.exists()) {
                        String userType = userDoc.getString("userType");
                        isAdmin = false;
                       if (userType != null) {
                           isAdmin = "admin".equals(userType);
                       }
                    } else {
                        isAdmin = false;
                    }

                    boolean finalIsAdmin = isAdmin;
                    firestore.collection(collectionName)
                            .whereEqualTo("ownerUID", ownerUID)
                            .orderBy("firestoreId", Query.Direction.ASCENDING)
                            .get()
                            .addOnSuccessListener(queryDocumentSnapshots -> {
                                if (!queryDocumentSnapshots.isEmpty()) {
                                    WriteBatch batch = firestore.batch();

                                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                                        Log.d("Firestore", "جاري حذف  " + doc.getString("firestoreId") + " من "+  collectionName);

                                        if ("transactions".equals(collectionName)) {
                                            if (finalIsAdmin) {
                                                // المدير يحذف مباشرة
                                                batch.delete(doc.getReference());
                                            } else {
                                                // تحقق من القواعد
                                                String accountFirestoreId = doc.getString("accountFirestoreId");
                                                String transactionOwnerUID = doc.getString("ownerUID");

                                                if (currentUid != null && currentUid.equals(transactionOwnerUID)) {
                                                    batch.delete(doc.getReference());
                                                } else if (accountFirestoreId != null) {
                                                    firestore.collection("accounts")
                                                            .document(accountFirestoreId)
                                                            .get()
                                                            .addOnSuccessListener(accountDoc -> {
                                                                if (accountDoc.exists() &&
                                                                        currentUid.equals(accountDoc.getString("ownerUID"))) {
                                                                    batch.delete(doc.getReference());
                                                                }
                                                            })
                                                            .addOnFailureListener(e -> Log.e("Firestore", "فشل التحقق من الحساب: " + e.getMessage()));
                                                }
                                            }
                                        } else {
                                            // حذف عادي للمجموعات الأخرى
                                            batch.delete(doc.getReference());
                                        }
                                    }

                                    batch.commit()
                                            .addOnSuccessListener(aVoid -> {
                                                String msg = "تم حذف البيانات الخاصة بـ " + collectionName;
                                                Log.d("Firestore", msg);
                                                tvResult.setText(msg);
                                            })
                                            .addOnFailureListener(e -> {
                                                String msg = "فشل في الحذف من : " + collectionName + "\n" + e.getMessage();
                                                Log.e("Firestore", msg, e);
                                                tvResult.setText(msg);
                                            });

                                } else {
                                    String msg = "لا توجد عمليات للحذف لهذا الـ ownerUID";
                                    Log.d("Firestore", msg);
                                    tvResult.setText(msg);
                                }
                            })
                            .addOnFailureListener(e -> {
                                String msg = "خطأ في الاستعلام: " + e.getMessage();
                                Log.e("Firestore", msg, e);
                                tvResult.setText(msg);
                            });
                })
                .addOnFailureListener(e -> {
                    String msg = "خطأ في جلب بيانات المستخدم: " + e.getMessage();
                    Log.e("Firestore", msg);
                    tvResult.setText(msg);
                });
    }

    private void deleteByOwnerUID1(String collectionName, String ownerUID) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        Log.d("Firestore", "Current UID = " + currentUid + ", ownerUID = " + ownerUID);
        firestore.collection(collectionName)
                .whereEqualTo("ownerUID", ownerUID)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        WriteBatch batch = firestore.batch();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            batch.delete(doc.getReference());
                        }

                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    String msg = "تم حذف كل العمليات الخاصة ب " + collectionName;
                                    Log.d("Firestore", msg);
                                    tvResult.setText(msg);
                                    Log.d("Firestore", "تم حذف كل العمليات الخاصة ب " + collectionName);

                                    // إذا المجموعة هي users نحذف الحساب من Auth (الحالي فقط)
//                                    if ("users".equals(collectionName) && currentUid.equals(ownerUID)) {
//                                        FirebaseAuth.getInstance().getCurrentUser()
//                                                .delete()
//                                                .addOnSuccessListener(unused -> {
//                                                    Log.d("Auth", "تم حذف الحساب من Authentication أيضًا");
//                                                })
//                                                .addOnFailureListener(e -> {
//                                                    Log.e("Auth", "فشل حذف الحساب من Authentication: " + e.getMessage());
//                                                });
//                                    }
                                })
                                .addOnFailureListener(e -> {
                                    String msg = "فشل في الحذف من : " + collectionName + "\n" + e.getMessage();
                                    Log.e("Firestore", msg, e);
                                    tvResult.setText(msg);
                                });
                    } else {
                        String msg = "لا توجد عمليات للحذف لهذا الـ ownerUID";
                        Log.d("Firestore", msg);
                        tvResult.setText(msg);
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = "خطأ في الاستعلام: " + e.getMessage();
                    Log.e("Firestore", msg, e);
                    tvResult.setText(msg);
                });
    }

    private void validateAndFixCurrencyIds(String ownerUID) {
        // أولاً: اجلب كل العملات الصحيحة الموجودة في جدول currencies
        firestore.collection("currencies")
                .whereEqualTo("ownerUID", ownerUID)
                .get()
                .addOnSuccessListener(currencySnapshot -> {
                    Set<Long> validCurrencyIds = new HashSet<>();
                    for (DocumentSnapshot currencyDoc : currencySnapshot.getDocuments()) {
                        Long currencyId = currencyDoc.getLong("id");
                        if (currencyId != null) {
                            validCurrencyIds.add(currencyId);
                        }
                    }

                    // ثانيًا: اجلب العمليات الخاصة بالـ ownerUID
                    firestore.collection("transactions")
                            .whereEqualTo("ownerUID", ownerUID)
                            .get()
                            .addOnSuccessListener(transactionSnapshot -> {
                                WriteBatch batch = firestore.batch();

                                for (DocumentSnapshot transactionDoc : transactionSnapshot.getDocuments()) {

                                    Long currencyId = transactionDoc.getLong("currencyId");

                                    if (currencyId == null || !validCurrencyIds.contains(currencyId)) {
                                        // لم يتم العثور على العملة -> عدلها إلى 1
                                        DocumentReference transactionRef = transactionDoc.getReference();
                                        batch.update(transactionRef, "currencyId", 1);

                                        // يمكنك أيضًا إضافة سجل في Log أو تخزين الرقم القديم مثلاً في حقل آخر
                                        Long oldCurrencyId = (currencyId != null) ? currencyId : -1;
                                        batch.update(transactionRef, "oldCurrencyId", oldCurrencyId);
                                    }
                                }

                                // ثالثًا: نفذ عملية التحديث
                                batch.commit()
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d("FirestoreHelper", "تم تحديث العملات الغير صالحة إلى 1 بنجاح.");
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("FirestoreHelper", "خطأ أثناء التحديث: " + e.getMessage());
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e("FirestoreHelper", "فشل في جلب transactions: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("FirestoreHelper", "فشل في جلب currencies: " + e.getMessage());
                });
    }
}