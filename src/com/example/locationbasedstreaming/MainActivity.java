package com.example.locationbasedstreaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.android.gcm.GCMRegistrar;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;

public class MainActivity extends Activity implements OnClickListener, LocationListener {

	public static boolean TESTING = true;
	public static String LOGTAG = "LBS";
	
	public static String LOCATION_UPDATE_URL = "YOUR SERVER UPDATE URL";
	
	public static String UNIQUE_ID = "UNIQUE_ID";
    //private String uniqueId = null;
    
    private Location currentLocation = null;
        
	LocationManager locationManager;
	boolean gpsProviderReady = false;
	
	Button stopButton, startStreamButton;
	TextView messageView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		GCMRegistrar.checkDevice(this);
		GCMRegistrar.checkManifest(this);
		final String regId = GCMRegistrar.getRegistrationId(this);
		if (regId.equals("")) {
		  GCMRegistrar.register(this, GCMIntentService.GCM_SENDER_ID);
		} else {
		  Log.v(LOGTAG, "Already registered");
		}		

		setContentView(R.layout.activity_main);
		
        messageView = (TextView) this.findViewById(R.id.messageView);        
		//messageView.setText("UUID: " + MainActivity.getUniqueId(this));
		
		stopButton = (Button) this.findViewById(R.id.stopButton);
		stopButton.setOnClickListener(this);
		
		startStreamButton = (Button) this.findViewById(R.id.startStreamButton);
		startStreamButton.setOnClickListener(this);
		startStreamButton.setText("Waiting for Location");
		startStreamButton.setEnabled(false);
				
		Intent startIntent = getIntent();
		//        i.putExtra("hello",intent.getStringExtra("hello"));

		if (startIntent.hasExtra("hello")) {

			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
	 		alertDialogBuilder.setTitle("Start Stream?");
	 		alertDialogBuilder.setMessage("A user has requested your stream.  Start streaming now?");
	 		alertDialogBuilder.setCancelable(false);
	 		alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,int id) {
							Intent i = new Intent(MainActivity.this, LiveStreamActivity.class);
							i.putExtra(MainActivity.UNIQUE_ID, GCMRegistrar.getRegistrationId(MainActivity.this));
					        startActivity(i);	
						}
			});
			alertDialogBuilder.setNegativeButton("No",new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,int id) {
							dialog.cancel();
						}
			});
			AlertDialog alertDialog = alertDialogBuilder.create();
			alertDialog.show();
		}
	}
	
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    }
	
	/*
	 Saving Battery life..  Not sure we want to do this
	 */
    @Override
	public void onPause() {
    	Log.v(LOGTAG,"onPause");
    	
		//locationManager.removeUpdates(this);

		super.onPause(); 
	}
    
	@Override
	public void onResume() {
		super.onResume();
		
		Log.v(LOGTAG,"onResume");
		
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		Location lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		Location lastGPSLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		
		if ((lastNetworkLocation != null && lastNetworkLocation.getTime() > System.currentTimeMillis() - 1000*60*5) ||
				(lastGPSLocation != null && lastGPSLocation.getTime() > System.currentTimeMillis() - 1000*60*5)) {
			
			if (lastNetworkLocation != null && (lastGPSLocation == null || lastNetworkLocation.getTime() > lastGPSLocation.getTime())) {
				currentLocation = lastNetworkLocation;
			} else if (lastGPSLocation != null) {
				currentLocation = lastGPSLocation;
			}
			
			sendLocation();
		}

		Log.v(LOGTAG,"5 minutes ago: " + (System.currentTimeMillis() - 1000*60*5));
		if (lastGPSLocation != null) {Log.v(LOGTAG,"last GPS update: " + lastGPSLocation.getTime());}
		if (lastNetworkLocation != null) {Log.v(LOGTAG,"last network update: " + lastNetworkLocation.getTime());}
		
		
		// 5 minutes
		try {
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5*60*1000, 0f, this);
		} catch (Exception e) {
			Log.v(LOGTAG,"requestLocationUpdates GPS not working");
		}
		
		
		try {
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5*60*1000, 0f, this);
		} catch (Exception e) {
			Log.v(LOGTAG,"requestLocationUpdates network not working");
		}
		
	}
	
	@Override
	public void onDestroy() {
		Log.v(LOGTAG, "onDestroy");
		locationManager.removeUpdates(this);
		
		super.onDestroy();
	}	
	
	/*
    private String getUniqueId(Context context) {
        if (uniqueId == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(
                    UNIQUE_ID, Context.MODE_PRIVATE);
            uniqueId = sharedPrefs.getString(UNIQUE_ID, null);
            if (uniqueId == null) {
            	uniqueId = UUID.randomUUID().toString();
                Editor editor = sharedPrefs.edit();
                editor.putString(UNIQUE_ID, uniqueId);
                editor.commit();
            }
        }
        return uniqueId;
    }
    */
	
	class UpdateLocation extends AsyncTask<String, Void, String>
	{

		@Override
		protected String doInBackground(String... urls) {
			String jsonString = "";

			// Here is the setup of our network request, we create an HttpClient and an HttpGet request object with the URL.
			DefaultHttpClient client = new DefaultHttpClient();
			HttpGet getRequest = new HttpGet(urls[0]);

			try {
			
				// Once we execute the request, we get an HttpResponse
				HttpResponse getResponse = client.execute(getRequest);
				
				// If the status is OK then we move on.
				final int statusCode = getResponse.getStatusLine().getStatusCode();
				if (statusCode == HttpStatus.SC_OK) { 
					
					// Get the content of the response as an InputStream and construct a reader
	        	   HttpEntity getResponseEntity = getResponse.getEntity();
	        	   InputStream inputStream = getResponseEntity.getContent();
	               
	               // Create a BufferedReader and StringBuilder to read form the stream and output a String
	               // Technically we could just hand gson the reader object but I thought this was a valuable example
	               BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(inputStream));
	               StringBuilder stringbuilder = new StringBuilder();
	        
	               String currentline = null;
	               
	               try {
	                   while ((currentline = bufferedreader.readLine()) != null) {
	                   	stringbuilder.append(currentline + "\n");
	                   }
	               } catch (IOException e) {
	                   e.printStackTrace();
	               }
	               
	               // Here is the resulting string
	               jsonString = stringbuilder.toString();
	               //Log.v("HTTP REQUEST",result);
	               inputStream.close();  
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return jsonString;
		}
		
		@Override
        protected void onProgressUpdate(Void... values) {
        }
		
		@Override
        protected void onPostExecute(String result) {

			Log.v(LOGTAG,result);
			
			// Create the Gson object and pass in the JSON

			//Gson gson = new Gson();
			//TwitterFeed[] responses = gson.fromJson(result, TwitterFeed[].class);
	       
			// Print out the results
			/*
			for (int i = 0; i < responses.length; i++) {
				Log.v(LOGTAG,responses[i].text);
			}
			*/
        }
	}

	private void sendLocation() {
		if (currentLocation != null) {
			startStreamButton.setText("Start Stream");
			startStreamButton.setEnabled(true);
			
			Log.v(LOGTAG,"MainActivity.currentLocation is " + currentLocation.toString());
			Toast.makeText(this, "Lat: " + currentLocation.getLatitude() + " Lon: " + currentLocation.getLongitude(), Toast.LENGTH_SHORT).show();
			UpdateLocation request = new UpdateLocation();
			request.execute(new String[] { LOCATION_UPDATE_URL + "?uuid=" + GCMRegistrar.getRegistrationId(this) + "&lat=" + currentLocation.getLatitude() + "&lon=" + currentLocation.getLongitude()});		
		} else {
			Log.v(LOGTAG,"MainMenu.currentLocation is null");
		}		
	}
	
	@Override
	public void onClick(View clickedView) {
		if (clickedView == stopButton) {
			finish();
		} else if (clickedView == startStreamButton) {
			Intent i = new Intent(this, LiveStreamActivity.class);
			i.putExtra(MainActivity.UNIQUE_ID,GCMRegistrar.getRegistrationId(this));
	        startActivity(i);
		}
	}    

	@Override
	public void onLocationChanged(Location location) {
		if (location.getProvider().equals(LocationManager.GPS_PROVIDER) || !gpsProviderReady) {
			Log.v(LOGTAG,location.getProvider() + " " + location.getLatitude() + " " + location.getLongitude());
			
			currentLocation = location;

			sendLocation();
			
		} else {
			Log.v(LOGTAG,"Got a location changed but it wasn't GPS and it should be");
		}
	}

	@Override
	public void onProviderDisabled(String locationProvider) {
		if (locationProvider.equals(LocationManager.GPS_PROVIDER)) {
			gpsProviderReady = false;
		}
		Log.v(LOGTAG,"onProviderDisabled: " + locationProvider);
	}
	
	@Override
	public void onProviderEnabled(String locationProvider) {
		Log.v(LOGTAG,"onProviderEnabled: " + locationProvider);
	}

	@Override
	public void onStatusChanged(String locationProvider, int status, Bundle extras) {
		if (locationProvider.equals(LocationManager.GPS_PROVIDER)) {
			if (status == LocationProvider.AVAILABLE) {
				Log.v(LOGTAG,"GPS Available");

				gpsProviderReady = true;
			} else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE || status == LocationProvider.OUT_OF_SERVICE) {
				Log.v(LOGTAG,"GPS Unavailable");
								
				gpsProviderReady = false;
			}
		}
		Log.v(LOGTAG,"onStatusChanged: " + locationProvider + " status: " + status);
	}
}
