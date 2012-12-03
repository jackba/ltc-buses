package org.frasermccrossan.ltc;

import java.util.Calendar;
import java.util.HashMap;

import org.frasermccrossan.ltc.DownloadService.DownloadBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

public class UpdateDatabase extends Activity {
	
	LTCScraper scraper = null;
	ProgressBar progressBar;
	TextView freshnessText;
	TextView weekdayStatus;
	TextView saturdayStatus;
	TextView sundayStatus;
	TextView weekdayLocationStatus;
	TextView saturdayLocationStatus;
	TextView sundayLocationStatus;
	TextView ageLimit;
	TextView status;
	Button updateButton;
	Button cancelButton;
	Button notWorkingButton;
	CheckBox fetchPositions;
	UpdateScrapingStatus scrapingStatus = null;
	
	boolean bound = false;

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            DownloadBinder binder = (DownloadBinder) service;
            DownloadService svc = binder.getService();
            scrapingStatus = new UpdateScrapingStatus();
            svc.setRemoteScrapeStatus(scrapingStatus);
            bound = true;
            disableUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
            enableUI();
        }
    };

	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.update_database);

        Resources res = getResources();

        BusDb db = new BusDb(this);

        progressBar = (ProgressBar)findViewById(R.id.progress);
        
        weekdayStatus = (TextView)findViewById(R.id.weekday_status);
        saturdayStatus = (TextView)findViewById(R.id.saturday_status);
        sundayStatus = (TextView)findViewById(R.id.sunday_status);
        weekdayLocationStatus = (TextView)findViewById(R.id.weekday_location_status);
        saturdayLocationStatus = (TextView)findViewById(R.id.saturday_location_status);
        sundayLocationStatus = (TextView)findViewById(R.id.sunday_location_status);
        ageLimit = (TextView)findViewById(R.id.age_limit);
        status = (TextView)findViewById(R.id.status_text);

        Calendar now = Calendar.getInstance();
        HashMap<Integer, Long> freshnesses = db.getFreshnesses(now.getTimeInMillis());
        int updateStatus = db.updateStatus(freshnesses, now);
        
        String statusFormat = res.getString(R.string.status_format);
        weekdayStatus.setText(String.format(statusFormat,
        		res.getString(R.string.weekday),
        		freshnessDays(freshnesses.get(BusDb.WEEKDAY_FRESHNESS), res)));
        saturdayStatus.setText(String.format(statusFormat,
        		res.getString(R.string.saturday),
        		freshnessDays(freshnesses.get(BusDb.SATURDAY_FRESHNESS), res)));
        sundayStatus.setText(String.format(statusFormat,
        		res.getString(R.string.sunday),
        		freshnessDays(freshnesses.get(BusDb.SUNDAY_FRESHNESS), res)));
        String statusLocationFormat = res.getString(R.string.status_location_format);
        weekdayLocationStatus.setText(String.format(statusLocationFormat,
        		res.getString(R.string.weekday),
        		freshnessDays(freshnesses.get(BusDb.WEEKDAY_LOCATION_FRESHNESS), res)));
        saturdayLocationStatus.setText(String.format(statusLocationFormat,
        		res.getString(R.string.saturday),
        		freshnessDays(freshnesses.get(BusDb.SATURDAY_LOCATION_FRESHNESS), res)));
        sundayLocationStatus.setText(String.format(statusLocationFormat,
        		res.getString(R.string.sunday),
        		freshnessDays(freshnesses.get(BusDb.SUNDAY_LOCATION_FRESHNESS), res)));
        ageLimit.setText(String.format(res.getString(R.string.age_limit),
        		freshnessDays(BusDb.UPDATE_DATABASE_AGE_LIMIT, res)));
        status.setText(res.getString(db.updateStrRes(updateStatus)));
        
        fetchPositions = (CheckBox)findViewById(R.id.fetch_positions);
        
        updateButton = (Button)findViewById(R.id.update_button);
        updateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent serviceIntent = new Intent(UpdateDatabase.this, DownloadService.class);
				serviceIntent.putExtra(DownloadService.FETCH_POSITIONS, fetchPositions.isChecked());
				startService(serviceIntent);
			}
		});

        cancelButton = (Button)findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent serviceIntent = new Intent(UpdateDatabase.this, DownloadService.class);
				stopService(serviceIntent);
			}
		});

        notWorkingButton = (Button)findViewById(R.id.not_working_button);
        notWorkingButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
		    	Intent diagnoseIntent = new Intent(UpdateDatabase.this, DiagnoseProblems.class);
		    	//LTCScraper scraper = new LTCScraper(UpdateDatabase.this, false);
		    	diagnoseIntent.putExtra("testurl", LTCScraper.ROUTE_URL);
		    	startActivity(diagnoseIntent);
		        Intent intent = new Intent(UpdateDatabase.this, DownloadService.class);
		        bindService(intent, connection, 0);
			}
        });
        
        db.close();
        
    }
	
	@Override
	protected void onResume() {
		super.onResume();
        Intent intent = new Intent(this, DownloadService.class);
        bindService(intent, connection, 0);
	}
	
	@Override
	protected void onPause() {
		if (bound) {
			unbindService(connection);
			bound = false;
		}
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		if (scraper != null) {
			scraper.close();
		}
		connection = null;
		super.onDestroy();
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.update_database_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.update_database_help:
        	startActivity(new Intent(this, UpdateDatabaseHelp.class));
    		return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }


	private String freshnessDays(long freshnessMillis, Resources res) {
		long days = freshnessMillis / (1000L * 60L * 60L * 24L);
		if (days > 10000) {
			return res.getString(R.string.never);
		}
		if (days == 1) {
			return String.format(res.getString(R.string.day_ago), days);
		}
		return String.format(res.getString(R.string.days_ago), days);
	}
	
	void disableUI() {
		updateButton.setVisibility(ProgressBar.GONE);
		fetchPositions.setVisibility(CheckBox.GONE);
		cancelButton.setVisibility(ProgressBar.VISIBLE);
		progressBar.setVisibility(ProgressBar.VISIBLE);
	}
	
	void enableUI() {
		updateButton.setVisibility(ProgressBar.VISIBLE);
		fetchPositions.setVisibility(CheckBox.VISIBLE);
		cancelButton.setVisibility(ProgressBar.GONE);
		progressBar.setVisibility(ProgressBar.GONE);
	}

	class UpdateScrapingStatus implements ScrapingStatus {
		
		public void update(LoadProgress progress) {
			status.setText(progress.message);
			progressBar.setProgress(progress.percent);
			if (progress.percent >= 100) {
				finish();
			}
			if (progress.percent < 0) {
				notWorkingButton.setVisibility(Button.VISIBLE);
			}
		}
		
	}
}
