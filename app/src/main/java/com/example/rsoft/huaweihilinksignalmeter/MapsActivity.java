package com.example.rsoft.huaweihilinksignalmeter;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.StringReader;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    private GoogleMap mMap;
    private OkHttpClient client;
    private static final String TAG = "myLogs";

    private String modem_rssi;
    private String modem_cellID;
    private String modem_ecio;
    private String modem_mode;

    private String plnm_mcc;
    private String plnm_opsos;

    Timer myTimer = new Timer(); // Создаем таймер
    final Handler uiHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        client = new OkHttpClient();

        myTimer.schedule(new TimerTask() { // Определяем задачу
            @Override
            public void run() {
                doLongAndComplicatedTask();
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateView();
                    }
                });
            }
        }, 0L, 3L * 1000);
    }

    protected void parsePLNM(String response){
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser xpp = factory.newPullParser();
            StringReader sw = new StringReader(response); //s содержит ваш XML
            xpp.setInput(sw); //подаем на вход парсера
            int eventType = xpp.getEventType();
            int modem_value = 0;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) //начальный тег
                {
                    if (xpp.getName().compareTo("FullName") == 0) {
                        modem_value = 1;
                    } else if (xpp.getName().compareTo("Numeric") == 0) {
                        modem_value = 2;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    switch (modem_value) {
                        case 1:
                            plnm_opsos = xpp.getText();
                            break;
                        case 2:
                            plnm_mcc = xpp.getText();
                            break;
                    }
                    modem_value = 0;
                }
                eventType = xpp.next();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }
    }
    protected void parseSignalStrength(String response){
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser xpp = factory.newPullParser();
            StringReader sw = new StringReader(response); //s содержит ваш XML
            xpp.setInput(sw); //подаем на вход парсера
            int eventType = xpp.getEventType();
            int modem_value = 0;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) //начальный тег
                {
                    if (xpp.getName().compareTo("rssi") == 0) {
                        modem_value = 1;
                    } else if (xpp.getName().compareTo("cell_id") == 0) {
                        modem_value = 2;
                    } else if (xpp.getName().compareTo("ecio") == 0) {
                        modem_value = 3;
                    } else if (xpp.getName().compareTo("mode") == 0) {
                        modem_value = 4;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    switch (modem_value) {
                        case 1:
                            modem_rssi = xpp.getText();
                            break;
                        case 2:
                            modem_cellID = xpp.getText();
                            break;
                        case 3:
                            modem_ecio = xpp.getText();
                            break;
                        case 4:
                            modem_mode = xpp.getText();
                            break;
                    }
                    modem_value = 0;
                }
                eventType = xpp.next();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }
    }

    protected void doLongAndComplicatedTask(){
        Request request = new Request.Builder()
                .url("http://192.168.8.1/api/device/signal")
                .build();
        try {
            Response response = client.newCall(request).execute();
            Log.d(TAG, "Received RSSI from Modem");
            final String responseData = response.body().string();
            parseSignalStrength(responseData);
        } catch (IOException e) {
            e.printStackTrace();
        }

        request = new Request.Builder()
                .url("http://192.168.8.1/api/net/current-plmn")
                .build();
        try {
            Response response = client.newCall(request).execute();
            Log.d(TAG, "Received plnm Modem");
            final String responseData = response.body().string();
            parsePLNM(responseData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String stripNonDigits(
            final CharSequence input /* inspired by seh's comment */){
        final StringBuilder sb = new StringBuilder(
                input.length() /* also inspired by seh's comment */);
        for(int i = 0; i < input.length(); i++){
            final char c = input.charAt(i);
            if(c > 47 && c < 58){
                sb.append(c);
            }
        }
        return sb.toString();
    }

    protected void updateView(){
        ProgressBar rssiBar = (ProgressBar) findViewById(R.id.rssiBar);
        TextView rssiValue = (TextView) findViewById(R.id.rssiValueView);
        TextView addInfo = (TextView) findViewById(R.id.additionalInfoView);

        int rssiInt = 0;
        rssiBar.setMax(120);

        if (modem_rssi != null) {
            rssiInt = 120 - Integer.parseInt(stripNonDigits(modem_rssi));
            rssiValue.setText(modem_rssi);
        } else {
            rssiValue.setText("");
        }
        rssiBar.setProgress(rssiInt);
        String ainfo="";
        if (modem_cellID != null) {
            int cellid = Integer.parseInt(modem_cellID);
            int lac = (cellid & 0xFFF0000) >> 16;
            if (lac > 0) {
                lac -= 1;
            }
            int cid = cellid & 0xFFFF;
            ainfo = "LAC:"+lac+" CID:"+cid;
        }
        if (modem_ecio != null) {
            ainfo += " ecio:"+modem_ecio;
        }
        if (plnm_mcc != null) {
            if (plnm_opsos != null) {
                ainfo += " MCC:" + plnm_mcc +"("+plnm_opsos+")";
            } else {
                ainfo += " MCC:" + plnm_mcc;
            }
        }
        addInfo.setText(ainfo);
        Log.d(TAG, ainfo);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Getting Google Play availability status
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());

        // Showing status
        if (status != ConnectionResult.SUCCESS) { // Google Play Services are not available

            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();

        } else { // Google Play Services are available

            // Enabling MyLocation Layer of Google Map
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mMap.setMyLocationEnabled(true);

            // Getting LocationManager object from System Service LOCATION_SERVICE
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            // Creating a criteria object to retrieve provider
            Criteria criteria = new Criteria();

            // Getting the name of the best provider
            String provider = locationManager.getBestProvider(criteria, true);

            // Getting Current Location
            Location location = locationManager.getLastKnownLocation(provider);

            if(location!=null){
                onLocationChanged(location);
            }
            locationManager.requestLocationUpdates(provider, 20000, 0, this);
        }

    }

    @Override
    public void onLocationChanged(Location location) {

  //      TextView tvLocation = (TextView) findViewById(R.id.tv_location);

        // Getting latitude of the current location
        double latitude = location.getLatitude();

        // Getting longitude of the current location
        double longitude = location.getLongitude();

        // Creating a LatLng object for the current location
        LatLng latLng = new LatLng(latitude, longitude);

        // Showing the current location in Google Map
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

        // Zoom in the Google Map
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15));

        // Setting latitude and longitude in the TextView tv_location
//        tvLocation.setText("Latitude:" +  latitude  + ", Longitude:"+ longitude );

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
