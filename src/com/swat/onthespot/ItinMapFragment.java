package com.swat.onthespot;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.directions.route.Route;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.directions.route.Segment;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.SnapshotReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.ui.IconGenerator;
import com.swat.onthespot.support.DrawArrowsTask;
import com.swat.onthespot.support.OTSDatabase;
import com.swat.onthespot.support.SaveMapTask;
import com.swat.onthespot.util.SystemUiHider;

/**
 * Map View Implementation. Contains an interactive map which
 * shows a plotted route between Experiences.
 * 
 * @Author: Richard Liang
 * @Author: Peng Zhao
 */
public class ItinMapFragment extends FragmentActivity implements RoutingListener
{
	public static final String INTENT_EXTRA = "result";
	public static final String RESULT_JOURNAL = "journal";
	public static final String RESULT_MAIN = "main";

	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after user
	 * interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;
	private GoogleMap map; //Main map.
	private LatLng start; //Start point of one route.
	private LatLng end; //End point of same route.
	private Geocoder gc; //Placeholder - for use later.
	private ArrayList<String> addresses; //List of Experience addresses.
	private ArrayList<String> names; //List of Experience names.
	private boolean doneRouting; //Placeholder - for use later.
	private OTSDatabase mDatabase; //Local sqlite database.
	private ArrayList<String>[] directions; //List of driving directions for each route.
	private boolean hasN; //App has network connection or not?
	public Activity activity; //Context of current activity.
	private ProgressDialog dialog; //Dialog.

	/*TODO: Make LatLng Queries AsyncTasks*/
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_itin_map_fragment);
		hasN = hasNetworkConnection();
		View contentView;
		if(!hasN)
		{
			contentView = findViewById(R.id.staticMap);
			loadSavedMap();
		}
		else
		{
			contentView = findViewById(R.id.map);
		}

		setTitle("Map View");
		SupportMapFragment fm = (SupportMapFragment)  getSupportFragmentManager().findFragmentById(R.id.map);
		gc = new Geocoder(ItinMapFragment.this, Locale.getDefault());
		map = fm.getMap();
		final View controlsView = findViewById(R.id.fullscreen_content_controls);


		// Get instance of OTS database.
		mDatabase = OTSDatabase.getInstance(this);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider
		.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener()
		{
			// Cached values.
			int mControlsHeight;
			int mShortAnimTime;

			@Override
			@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
			public void onVisibilityChange(boolean visible)
			{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2)
				{
					// If the ViewPropertyAnimator API is available
					// (Honeycomb MR2 and later), use it to animate the
					// in-layout UI controls at the bottom of the
					// screen.
					if (mControlsHeight == 0)
					{
						mControlsHeight = controlsView.getHeight();
					}
					if (mShortAnimTime == 0)
					{
						mShortAnimTime = getResources().getInteger(
								android.R.integer.config_shortAnimTime);
					}
					controlsView.animate()
					.translationY(visible ? 0 : mControlsHeight)
					.setDuration(mShortAnimTime);
				} else
				{
					// If the ViewPropertyAnimator APIs aren't
					// available, simply show or hide the in-layout UI
					// controls.
					controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
				}

				if (visible && AUTO_HIDE)
				{
					// Schedule a hide().
					delayedHide(AUTO_HIDE_DELAY_MILLIS);
				}
			}
		});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				if (TOGGLE_ON_CLICK)
				{
					mSystemUiHider.toggle();
				} else
				{
					mSystemUiHider.show();
				}
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		findViewById(R.id.journalButton).setOnTouchListener(mDelayHideTouchListener);
		findViewById(R.id.itinButton).setOnTouchListener(mDelayHideTouchListener);
		Log.i("GETLL", "INIT");
		if(hasN)
		{
			ThreadPolicy tp = ThreadPolicy.LAX;
			StrictMode.setThreadPolicy(tp);
			updateMap();
		}
		activity = this;
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the system
	 * UI. This is to prevent the jarring behavior of controls going away while
	 * interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener()
	{
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent)
		{
			if (AUTO_HIDE)
			{
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis)
	{
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}

	/**
	 * Function: updateMap()
	 * 
	 * Updates our Google Map with Experience info including routes from
	 * one Experience to another, and informational markers for each Experience.
	 * 
	 * Returns: nothing
	 */
	public void updateMap()
	{
		addresses = new ArrayList<String>();
		names = new ArrayList<String>();
		String itinName = getIntent().getStringExtra(ItineraryActivity.INTENT_EXTRA);

		// Get sorted addresses in this itinerary.
		String selection = OTSDatabase.TABLE_EXPS + "." + OTSDatabase.EXPS_KEY_ADDR + " , " +
				OTSDatabase.TABLE_EXPS + "." + OTSDatabase.EXPS_KEY_NAME + " , " +
				OTSDatabase.TABLE_ITINS_EXPS + "." + OTSDatabase.ITINS_EXPS_KEY_SORT;

		String expsQuery = 	"SELECT " + selection + " FROM " + 
				OTSDatabase.TABLE_EXPS + ", " + OTSDatabase.TABLE_ITINS_EXPS + " " +
				"WHERE " + OTSDatabase.TABLE_EXPS + "." + OTSDatabase.EXPS_KEY_ID + "=" + 
				OTSDatabase.TABLE_ITINS_EXPS + "." + OTSDatabase.ITINS_EXPS_KEY_EXPID + " AND " +
				OTSDatabase.TABLE_ITINS_EXPS + "." + OTSDatabase.ITINS_EXPS_KEY_ITINID + "=" + 
				mDatabase.ItinNameToIds(itinName)[0] + " ORDER BY " + 
				OTSDatabase.TABLE_ITINS_EXPS + "." + OTSDatabase.ITINS_EXPS_KEY_SORT + " ASC";
		Cursor expsCursor = mDatabase.rawQuery(expsQuery, null);

		// Store addresses into the array.
		expsCursor.moveToFirst();
		//Find index of Experience address and name columns in cursor.
		int addCol = expsCursor.getColumnIndex(OTSDatabase.EXPS_KEY_ADDR);
		int nameCol = expsCursor.getColumnIndex(OTSDatabase.EXPS_KEY_NAME);
		String address;
		String name;
		Log.i("ADDRESS", "RA");
		
		 
		/* For each Experience, place its address and name 
		 * in the apropriate array.
		 */
		while(!expsCursor.isAfterLast())
		{
			address = expsCursor.getString(addCol);
			Log.i("ADDRESS", address);
			addresses.add(address);
			name = expsCursor.getString(nameCol);
			names.add(name);
			expsCursor.moveToNext();
		}


		directions = (ArrayList<String>[])new ArrayList[addresses.size() - 1];
		for(int i = 0; i < directions.length; i++)
			directions[i] = new ArrayList<String>();

		//Place markers for each experience on map(TODO: make this Async).
		ArrayList<LatLng> locationData = new ArrayList<LatLng>();
		for(int i = 0; i < addresses.size(); i++)
		{
			LatLng toAdd = getLatLngFromLoc(addresses.get(i));
			locationData.add(toAdd);
			addMarker(toAdd, i+1);
		}
		
		//Plot routes for our Experiences.
		for(int i = 0; i < locationData.size() - 1; i++)
		{
			start = locationData.get(i);
			end = locationData.get(i + 1);
			Routing routing = new Routing(Routing.TravelMode.WALKING, i + 1);
			routing.registerListener(this);
			routing.execute(start, end);
		}
		
		//Update view to zoom in on first experience.
		CameraUpdate center=CameraUpdateFactory.newLatLng(getLatLngFromLoc(addresses.get(0)));
		CameraUpdate zoom=  CameraUpdateFactory.zoomTo(15);

		map.moveCamera(center);	
		map.animateCamera(zoom);

	}

	public void exitToJournal(View v)
	{
		Intent returnIntent = new Intent();
		returnIntent.putExtra(INTENT_EXTRA, RESULT_JOURNAL);
		setResult(RESULT_OK,returnIntent);     
		if(hasN)
			promptSave();
		else
			finish();
	}

	public void exitToMain(View v)
	{
		Intent returnIntent = new Intent();
		returnIntent.putExtra(INTENT_EXTRA, RESULT_MAIN);
		setResult(RESULT_OK, returnIntent);     
		if(hasN)
			promptSave();
		else
			finish();
	}

	@Override 
	public void onRoutingFailure() {
		// The Routing request failed
	}

	@Override
	public void onRoutingStart() {
		// The Routing Request starts
	}

	@Override
	public void onRoutingSuccess(PolylineOptions mPolyOptions, LatLng start, LatLng end, int routeN, Route result) {

		PolylineOptions polyoptions = new PolylineOptions();
		if(routeN % 2 == 0)
			polyoptions.color(Color.parseColor("#62a5d4"));
		else
			polyoptions.color(Color.parseColor("#62a5d4"));
		polyoptions.width(10);
		polyoptions.addAll(mPolyOptions.getPoints());
		map.addPolyline(polyoptions);
		List<LatLng> pts = polyoptions.getPoints();
		List<Segment> segments  = result.getSegments();

		for(int i = 0; i < segments.size(); i++)
		{
			Segment currSegment = segments.get(i);
			String segDir = currSegment.getInstruction();
			String dirDist = currSegment.getText();
			directions[routeN - 1].add(segDir + " (" + dirDist + ") ");
			/*if(i == segments.size() - 1)
				break;
			LatLng pt1 = currSegment.startPoint();
			LatLng pt2 = segments.get(i + 1).startPoint();
			DrawArrowsTask dat = new DrawArrowsTask(map, ItinMapFragment.this, pt1, pt2);
			dat.execute();*/
		}

	}

	/*public LatLng getLatLngFromLoc(String address) throws IOException{

  	 	List<Address> list = null;
  	 	for(int i = 0; i < 100; i++)
  	 	{
  	 		 list = gc.getFromLocationName(address, 1);
  	 		 Log.i("GETLL", "Loop Number" + i);
  	 		 if(list.size() > 0)
  	 			break;
  	 	}
  	 	if(list.size() == 0)
  	 		return null;
  	 	Address add = list.get(0);
  	 	return new LatLng(add.getLatitude(), add.getLongitude());
	 }*/

	public LatLng getLatLngFromLoc(String address) {
		address = address.replace("\n"," ").replace(" ", "%20");
		HttpGet httpGet = new HttpGet("http://maps.google.com/maps/api/geocode/json?address=" +address+"&ka&sensor=false");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response;
		StringBuilder stringBuilder = new StringBuilder();

		try {
			response = client.execute(httpGet);
			HttpEntity entity = response.getEntity();
			InputStream stream = entity.getContent();
			int b;
			while ((b = stream.read()) != -1) {
				stringBuilder.append((char) b);
			}
		} catch (ClientProtocolException e) {
		} catch (IOException e) {
		}

		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject = new JSONObject(stringBuilder.toString());
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Double lon = new Double(0);
		Double lat = new Double(0);

		try {

			lon = ((JSONArray)jsonObject.get("results")).getJSONObject(0)
					.getJSONObject("geometry").getJSONObject("location")
					.getDouble("lng");

			lat = ((JSONArray)jsonObject.get("results")).getJSONObject(0)
					.getJSONObject("geometry").getJSONObject("location")
					.getDouble("lat");

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return new LatLng(lat, lon);
	} 

	public void promptSave()
	{
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				ItinMapFragment.this);

		// set title
		alertDialogBuilder.setTitle("Save");

		// set dialog message
		alertDialogBuilder
		.setMessage("Save This Map?(A snapshot of this current map will be taken for offline use)")
		.setCancelable(false)
		.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog,int id) {
				saveMap();
				dialog.dismiss();
			}
		})
		.setNegativeButton("No",new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog,int id) {
				// if this button is clicked, just close
				// the dialog box and do nothing
				dialog.dismiss();
				finish();
			}
		});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();

		// show it
		alertDialog.show();
	}


	public void saveMap()
	{
		String FILE_NAME = getIntent().getStringExtra("Extra").replace(" ", "");
		dialog = ProgressDialog.show(ItinMapFragment.this, "Save", "Please Wait...");
		SaveMapTask smt = new SaveMapTask(FILE_NAME, map, dialog, ItinMapFragment.this);
		smt.execute();

		/*SnapshotReadyCallback callback = new SnapshotReadyCallback() {
			Bitmap bitmap;

			@Override
			public void onSnapshotReady(Bitmap snapshot) {
				// TODO Auto-generated method stub
				bitmap = snapshot;
				try {
					String FILE_NAME = getIntent().getStringExtra("Extra").replace(" ", "");
					Log.i("FILENAME", FILE_NAME);
					FileOutputStream out = openFileOutput(FILE_NAME +".png", Context.MODE_PRIVATE);
					bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
					out.flush();
					out.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		map.snapshot(callback);*/
	}

	public void loadSavedMap()
	{
		String FILE_NAME = getIntent().getStringExtra("Extra").replace(" ", "");
		try
		{
			View mapV = findViewById(R.id.map);
			mapV.setVisibility(View.GONE);
			FileInputStream in = openFileInput(FILE_NAME + ".png");
			Log.i("FILENAME", FILE_NAME);
			Bitmap map = BitmapFactory.decodeStream(in);
			ImageView imgV = (ImageView)findViewById(R.id.staticMap);
			imgV.setImageBitmap(map);			


		} catch (FileNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	public boolean hasNetworkConnection()
	{
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	void addMarker(LatLng point, int place)
	{
		IconGenerator iconFactory = new IconGenerator(this);
		iconFactory.setStyle(IconGenerator.STYLE_BLUE);
		MarkerOptions markerOptions = new MarkerOptions().
				icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon("(" + place + ")"))).
				title(names.get(place - 1)).
				snippet(addresses.get(place - 1)).
				position(point).
				anchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());

		map.addMarker(markerOptions);

	}

}