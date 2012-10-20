package org.frasermccrossan.ltc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class FindStop extends Activity {
	
	EditText searchField;
	ListView stopList;
	SimpleAdapter stopListAdapter;
	List<HashMap<String, String>> stops;
	Spinner searchTypeSpinner;
	LocationManager myLocationManager;
	String locProvider = null;
	Location lastLocation;
	SearchTask mySearchTask = null;
	BusDb db;
	int downloadTry;
	
	// entries in R.array.search_types
	static final int RECENT_STOPS = 0;
	static final int CLOSEST_STOPS = 1;
	
	static final long LOCATION_TIME_UPDATE = 30; // seconds between GPS update
	static final float LOCATION_DISTANCE_UPDATE = 100; // minimum metres between GPS updates
	
	OnItemClickListener stopListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			TextView stopNumberView = (TextView)view.findViewById(R.id.stop_number);
			String stopNumber = stopNumberView.getText().toString();
	    	Intent stopTimeIntent = new Intent(FindStop.this, StopTimes.class);
	    	stopTimeIntent.putExtra(BusDb.STOP_NUMBER, stopNumber);
	    	startActivity(stopTimeIntent);
		}
	};
	
	OnItemSelectedListener searchTypeListener = new OnItemSelectedListener() {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			setLocationUpdates();
			updateStops();
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// nothing
			
		}
	};
	
	LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			// Called when a new location is found by the network location provider.
			lastLocation = location;
			String provider = lastLocation.getProvider();
//			if (provider.equals(LocationManager.GPS_PROVIDER)) {
//				locationImage.setImageResource(R.drawable.ic_action_satellite_location);
//			}
//			else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
//				locationImage.setImageResource(R.drawable.ic_action_antenna_location);
//			}
//			else {
//				locationImage.setImageResource(R.drawable.ic_action_no_location);
//			}
			updateStops();
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {}

		public void onProviderEnabled(String provider) {}

		public void onProviderDisabled(String provider) {}
	};
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.find_stop);
        searchField = (EditText)findViewById(R.id.search);
        searchField.addTextChangedListener(new TextWatcher() {
        	public void afterTextChanged(Editable s) {
        		updateStops();
        	}
        	
        	// don't care
        	public void	beforeTextChanged(CharSequence s, int start, int count, int after) {}
        	public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        myLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        stopList = (ListView)findViewById(R.id.stop_list);
        stopList.setOnItemClickListener(stopListener);
		stops = new ArrayList<HashMap<String, String>>();
		stopListAdapter = new SimpleAdapter(FindStop.this,
        		 stops,
        		 R.layout.stop_list_item,
        		 new String[] { BusDb.STOP_NUMBER, BusDb.STOP_NAME, BusDb.ROUTE_LIST },
        		 new int[] { R.id.stop_number, R.id.stop_name, R.id.route_list });
		stopList.setAdapter(stopListAdapter);
        searchTypeSpinner = (Spinner)findViewById(R.id.search_type_spinner);
        searchTypeSpinner.setOnItemSelectedListener(searchTypeListener);
        db = new BusDb(this);
        downloadTry = 0;
    }
	
	@Override
	protected void onStart() {
		super.onStart();
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		locProvider = myLocationManager.GPS_PROVIDER;
		if (locProvider != null) {
			if (myLocationManager.isProviderEnabled(locProvider)) {
				searchTypeSpinner.setEnabled(true);
				//myLocationManager.requestSingleUpdate(locProvider, locationListener, null);
			}
			else {
				searchTypeSpinner.setSelection(RECENT_STOPS);
				searchTypeSpinner.setEnabled(false);
			}
		}
		setLocationUpdates();
		int updateStatus = db.getUpdateStatus();
        if (updateStatus != BusDb.UPDATE_NOT_REQUIRED) {
        	++downloadTry;
        	if (downloadTry <= 1) {
        		Intent updateDatabaseIntent = new Intent(FindStop.this, UpdateDatabase.class);
        		startActivity(updateDatabaseIntent);
        	}
        	else if (updateStatus == BusDb.UPDATE_REQUIRED) {
        		finish();
        	}
        	updateStops();
        }
        else {
        	updateStops();
        }
	}
    
	@Override
	protected void onStop() {
		myLocationManager.removeUpdates(locationListener);
		if (mySearchTask != null && ! mySearchTask.isCancelled()) {
			mySearchTask.cancel(true);
		}
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		db.close();
		super.onDestroy();
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.about:
        	startActivity(new Intent(this, About.class));
        	return true;
        case R.id.update_database:
    		Intent updateDatabaseIntent = new Intent(FindStop.this, UpdateDatabase.class);
    		startActivity(updateDatabaseIntent);
    		return true;
        case R.id.find_stop_help:
        	startActivity(new Intent(this, FindStopHelp.class));
    		return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public void setLocationUpdates()
    {
		switch (searchTypeSpinner.getSelectedItemPosition()) {
		case RECENT_STOPS:
			myLocationManager.removeUpdates(locationListener);
			lastLocation = null;
			break;
		case CLOSEST_STOPS:
			myLocationManager.requestLocationUpdates(locProvider, LOCATION_TIME_UPDATE * 1000, LOCATION_DISTANCE_UPDATE, locationListener);
			lastLocation = myLocationManager.getLastKnownLocation(locProvider);
			break;
		default:
			// nothing
			break;
		}
    }
    
	public void updateStops() {
		if (mySearchTask != null && ! mySearchTask.isCancelled()) {
			mySearchTask.cancel(true);
		}
		mySearchTask = new SearchTask();
		mySearchTask.execute(searchField.getText());

	}
	
	class SearchTask extends AsyncTask<CharSequence, List<HashMap<String, String>>, Void> {
		
		@SuppressWarnings("unchecked")
		protected Void doInBackground(CharSequence... strings) {
			List<HashMap<String, String>> newStops = db.findStops(strings[0], lastLocation);
			if (!isCancelled()) {
				publishProgress(newStops);
			}
			db.addRoutesToStopList(newStops);
			if (!isCancelled()) {
				// can publish a null since the above update updates all the same objects
				publishProgress((List<HashMap<String, String>>) null);
			}
			return null;
		}
		
		protected void onProgressUpdate(List<HashMap<String, String>>... newStops) {
			if (!isCancelled() && stopListAdapter != null) {
				for (List<HashMap<String, String>> newStop: newStops) {
					if (newStop != null) {
						stops.clear();
						stops.addAll(newStop);
					}
				}
				stopListAdapter.notifyDataSetChanged();
			}
		}

	}
}