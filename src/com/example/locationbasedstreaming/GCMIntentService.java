package com.example.locationbasedstreaming;

import java.util.List;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

public class GCMIntentService extends GCMBaseIntentService {

    private static final String LOG_TAG = "GCMIntentService";
    public static final String GCM_SENDER_ID = "SENDER ID GET FROM GOOGLE CLOUD MESSENGER SERVICE;

    public GCMIntentService() {
        super( GCM_SENDER_ID );
        Log.v( LOG_TAG, "GCMIntentService constructor called" );
    }
    
    @Override
    protected void onError(Context _context, String errorId) {
        Log.v( LOG_TAG, "GCMIntentService onError called: " + errorId );
        // Called when the device tries to register or unregister, but GCM returned an error. Typically, there is nothing to be done other than evaluating the error (returned by errorId) and trying to fix the problem.
    }

    @Override
    protected void onMessage(Context _context, Intent intent) {
        Log.v( LOG_TAG, "GCMIntentService onMessage called" );
        //Log.v( LOG_TAG, "Message is: " + intent.getStringExtra( "message" ) );
        Log.v( LOG_TAG, "Hello is: " + intent.getStringExtra("hello"));
        // Called when your server sends a message to GCM, and GCM delivers it to the device. If the message has a payload, its contents are available as extras in the intent.

		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		List< ActivityManager.RunningTaskInfo > runningTaskInfo = manager.getRunningTasks(1); 
		
		ComponentName componentInfo = runningTaskInfo.get(0).topActivity;
		Log.v(LOG_TAG,"Top Activity: " + componentInfo.getClassName());
		
		if (!componentInfo.getClassName().contains("LiveStreamActivity")) {
		Intent i = new Intent(_context,MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		i.putExtra("hello",intent.getStringExtra("hello"));
		    _context.startActivity(i);         
		 } 
    }

    @Override
    protected void onRegistered(Context _context, String registrationId) {
        Log.v( LOG_TAG, "GCMIntentService onRegistered called" );
        Log.v( LOG_TAG, "Registration id is: " + registrationId );
        // Called after a registration intent is received, passes the registration ID assigned by GCM to that device/application pair as parameter. Typically, you should send the regid to your server so it can use it to send messages to this device.
        
    }

    @Override
    protected void onUnregistered(Context _context, String registrationId) {
        Log.v( LOG_TAG, "GCMIntentService onUnregistered called" );
        Log.v( LOG_TAG, "Registration id is: " + registrationId );
        //Called after the device has been unregistered from GCM. Typically, you should send the regid to the server so it unregisters the device.
    }
}