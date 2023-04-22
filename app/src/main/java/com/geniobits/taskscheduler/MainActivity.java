package com.geniobits.taskscheduler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.wafflecopter.multicontactpicker.ContactResult;
import com.wafflecopter.multicontactpicker.LimitColumn;
import com.wafflecopter.multicontactpicker.MultiContactPicker;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog.OnTimeSetListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final int CONTACT_PICKER_REQUEST = 202;
    private EditText txt_message;
    private EditText txt_number;
    private EditText txt_count;
    private Button btn_whatsapp;
    private Button btn_choose;
    List<ContactResult> results=new ArrayList<>();
    private Button btn_time;
    private int hr=100;
    private int min=100;
    private int sec=100;
    private int days=1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txt_message = findViewById(R.id.txt_message);
        txt_number = findViewById(R.id.txt_mobile_number);
        txt_count  = findViewById(R.id.txt_count);
        btn_whatsapp = findViewById(R.id.btn_whatsapp);
        btn_choose = findViewById(R.id.button_choose_contacts);
        btn_time = findViewById(R.id.btn_time);

        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_CONTACTS
                ).withListener(new MultiplePermissionsListener() {
            @Override public void onPermissionsChecked(MultiplePermissionsReport report) {/* ... */}
            @Override public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {/* ... */}
        }).check();

        if(!isAccessibilityOn(getApplicationContext())){
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        btn_choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MultiContactPicker.Builder(MainActivity.this)
                        .hideScrollbar(false)
                        .showTrack(true)
                        .searchIconColor(Color.WHITE)
                        .setChoiceMode(MultiContactPicker.CHOICE_MODE_MULTIPLE)
                        .handleColor(ContextCompat.getColor(MainActivity.this, R.color.colorPrimary))
                        .bubbleColor(ContextCompat.getColor(MainActivity.this, R.color.colorPrimary))
                        .bubbleTextColor(Color.WHITE)
                        .setTitleText("Select Contacts")
                        .setLoadingType(MultiContactPicker.LOAD_ASYNC)
                        .limitToColumn(LimitColumn.NONE)
                        .setActivityAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out)
                        .showPickerForResult(CONTACT_PICKER_REQUEST);
            }
        });

        btn_time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimePickerDialog dpd = TimePickerDialog.newInstance(
                        new OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second) {
                                hr=hourOfDay;
                                min=minute;
                                sec=second;
                            }
                        },
                        false
                );
                dpd.show(getSupportFragmentManager(), "Datepickerdialog");
            }
        });

        btn_whatsapp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hr!=100 && sec!=100 && min!=100){
                   if(!results.isEmpty()){
                       if(!txt_message.getText().toString().isEmpty()){
                           List<String> numbersList = new ArrayList<String>();
                           for (int i=0;i<results.size();i++){
                               numbersList.add(results.get(i).getPhoneNumbers().get(0).getNumber());
                           }
                           String[] numbers = numbersList.toArray(new String[0]);
                           long flexTime = calculateFlex(hr,min,sec, days);

                           Data messageData = new Data.Builder()
                                   .putString("message", txt_message.getText().toString())
                                   .putStringArray("contacts",numbers)
                                   .putString("count",txt_count.getText().toString())
                                   .build();

                           PeriodicWorkRequest sendMessagework = new PeriodicWorkRequest.Builder(sendMessageWorker.class,days,
                                   TimeUnit.DAYS,
                                   flexTime, TimeUnit.MILLISECONDS)
                                   .setInputData(messageData)
                                   .addTag("send_message_work")
                                   .build();

                           WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork("send_message_work",
                                   ExistingPeriodicWorkPolicy.REPLACE,sendMessagework);
                           Toast.makeText(MainActivity.this, "Message is scheduled", Toast.LENGTH_SHORT).show();


                       }else{
                           Toast.makeText(MainActivity.this, "Please add message", Toast.LENGTH_SHORT).show();
                       }
                   }else{
                       Toast.makeText(MainActivity.this, "Select Contact Number", Toast.LENGTH_SHORT).show();
                   }
                }else{
                    Toast.makeText(MainActivity.this, "Please select time", Toast.LENGTH_SHORT).show();
                }

            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CONTACT_PICKER_REQUEST){
            if(resultCode == RESULT_OK) {
                results = MultiContactPicker.obtainResult(data);
                StringBuilder names = new StringBuilder(results.get(0).getDisplayName());
                for (int j=0;j<results.size();j++){
                    if(j!=0)
                        names.append(", ").append(results.get(j).getDisplayName());
                }
                txt_number.setText(names);
                Log.d("MyTag", results.get(0).getDisplayName());
            } else if(resultCode == RESULT_CANCELED){
                System.out.println("User closed the picker without selecting items.");
            }
        }
    }





    private boolean isAccessibilityOn(Context context) {
        int accessibilityEnabled = 0;
        final String service = context.getPackageName () + "/" + WhatAppAccessibilityService.class.getCanonicalName ();
        try {
            accessibilityEnabled = Settings.Secure.getInt (context.getApplicationContext ().getContentResolver (), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {  }

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter (':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString (context.getApplicationContext ().getContentResolver (), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                colonSplitter.setString (settingValue);
                while (colonSplitter.hasNext ()) {
                    String accessibilityService = colonSplitter.next ();

                    if (accessibilityService.equalsIgnoreCase (service)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private long calculateFlex(int hourOfTheDay,int minute,int sec, int periodInDays) {

        // Initialize the calendar with today and the preferred time to run the job.
        Calendar cal1 = Calendar.getInstance();
        cal1.set(Calendar.HOUR_OF_DAY, hourOfTheDay);
        cal1.set(Calendar.MINUTE, minute);
        cal1.set(Calendar.SECOND, sec);

        // Initialize a calendar with now.
        Calendar cal2 = Calendar.getInstance();

        if (cal2.getTimeInMillis() < cal1.getTimeInMillis()) {
            // Add the worker periodicity.
            cal2.setTimeInMillis(cal2.getTimeInMillis() + TimeUnit.DAYS.toMillis(periodInDays));
        }

        long delta = (cal2.getTimeInMillis() - cal1.getTimeInMillis());

        return ((delta > PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS) ? delta
                : PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS);
    }
}
