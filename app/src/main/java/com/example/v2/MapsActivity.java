package com.example.v2;

import android.Manifest;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


    private static final String TAG = "MapsActivity";
    private GoogleMap mMap;
    private GeofencingClient geofencingClient;
    private GeofenceHelper geofenceHelper;
    private float GEOFENCE_RADIUS = 150;
    private String GEOFENCE_ID = "1000";
    private Button trackButton;
    private EditText groupIDEditText;
    private FirebaseAuth mAuth;
    private String uid;
    private DatabaseReference loc;
    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private Circle geofenceCircle;
    private ZoomControls zoomControls;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private SeekBar radiusSeekBar;
    private Button saveGeofenceButton;
    private TextView radiusTextView;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        zoomControls = findViewById(R.id.zoomControls);
        geofencingClient = LocationServices.getGeofencingClient(this);
        geofenceHelper = new GeofenceHelper(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        mAuth = FirebaseAuth.getInstance();
        uid = mAuth.getCurrentUser().getUid();

        trackButton = findViewById(R.id.trackButton1);
        groupIDEditText = findViewById(R.id.groupIDEditText);
        radiusSeekBar = findViewById(R.id.radiusSeekBar);
        radiusTextView = findViewById(R.id.radiusTextView);

        radiusSeekBar.setMax(1000); // Set the maximum SeekBar value

        trackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String groupId = groupIDEditText.getText().toString();
                if (!groupId.isEmpty()) {
                    trackGroup(groupId);
                } else {
                    Toast.makeText(MapsActivity.this, "Please enter a group ID", Toast.LENGTH_SHORT).show();
                }
            }
        });

        loc = FirebaseDatabase.getInstance().getReference().child("Users").child(uid).child("Location");

        // Initialize mGoogleApiClient
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Set up zoom in and zoom out buttons
        zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });

        radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update the geofence radius when SeekBar is moved
                GEOFENCE_RADIUS = progress;
                updateRadiusTextView(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        saveGeofenceButton = findViewById(R.id.saveGeofenceButton);
        saveGeofenceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveGeofenceToDatabase();
            }
        });
    }

    private void updateRadiusTextView(int radiusInMeters) {
        String radiusText = "Radius (meters): " + radiusInMeters;
        radiusTextView.setText(radiusText);
    }

    private void saveGeofenceToDatabase() {
        if (geofenceCircle != null) {
            LatLng geofenceCenter = geofenceCircle.getCenter();
            double latitude = geofenceCenter.latitude;
            double longitude = geofenceCenter.longitude;
            double radius = geofenceCircle.getRadius();

            DatabaseReference geofenceRef = FirebaseDatabase.getInstance().getReference()
                    .child("Geofences")
                    .child(uid); // Use the user's UID as the key for geofence data

            // Save geofence details to the database
            geofenceRef.child("latitude").setValue(latitude);
            geofenceRef.child("longitude").setValue(longitude);
            geofenceRef.child("radius").setValue(radius);

            Toast.makeText(this, "Geofence saved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No geofence to save", Toast.LENGTH_SHORT).show();
        }
    }


        private void trackGroup(String groupId) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("Groups").child(groupId);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                //mMap.clear();
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String uid = userSnapshot.getKey();
                    DatabaseReference userInfoRef = FirebaseDatabase.getInstance().getReference()
                            .child("Users")
                            .child(uid);
                    userInfoRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                String userName = dataSnapshot.child("Name").getValue(String.class);
                                Double userLatitude = dataSnapshot.child("Location").child("latitude").getValue(Double.class);
                                Double userLongitude = dataSnapshot.child("Location").child("longitude").getValue(Double.class);
                                if (userName != null && userLatitude != null && userLongitude != null) {
                                    LatLng userLatLng = new LatLng(userLatitude, userLongitude);
                                    MarkerOptions marker = new MarkerOptions()
                                            .position(userLatLng)
                                            .title(userName);
                                    mMap.addMarker(marker);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            // Handle the error condition, if needed
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle the error condition, if needed
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

            // Check if we have location permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // Request the last known location
                fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();

                            LatLng latLng = new LatLng(latitude, longitude);

                            // Move the camera to the user's current location
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                            mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
                        }
                    }
                });

                // Start location updates
                startLocationUpdates();
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }

        createGeofenceFromDatabase();

        mMap.setOnMapLongClickListener(this);

        // Add ZoomControls to the map
        mMap.getUiSettings().setZoomControlsEnabled(false);
    }


        private void enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableUserLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

        @Override
        public void onMapLongClick(LatLng latLng) {
        if (Build.VERSION.SDK_INT >= 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                handleMapLongClick(latLng);
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_ACCESS_REQUEST_CODE);
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_ACCESS_REQUEST_CODE);
                }
            }
        } else {
            handleMapLongClick(latLng);
        }
    }

        private void handleMapLongClick(LatLng latLng) {
        // Remove the previously added geofence circle, if any
        if (geofenceCircle != null) {
            geofenceCircle.remove();
        }

        addCircle(latLng, GEOFENCE_RADIUS);
        addGeofence(latLng, GEOFENCE_RADIUS);
    }

        private void addCircle(LatLng latLng, float radius) {
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(latLng);
        circleOptions.radius(radius);
        circleOptions.strokeColor(Color.argb(255, 255, 0, 0));
        circleOptions.fillColor(Color.argb(64, 255, 0, 0));
        circleOptions.strokeWidth(4);
        geofenceCircle = mMap.addCircle(circleOptions); // Store the reference to the geofence circle
    }


        private void addGeofence(LatLng latLng, float radius) {
        Geofence geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT);
        GeofencingRequest geofencingRequest = geofenceHelper.getGeofencingRequest(geofence);
        PendingIntent pendingIntent = geofenceHelper.getPendingIntent();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "onSuccess: Geofence Added...");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        String errorMessage = geofenceHelper.getErrorString(e);
                        Log.d(TAG, "onFailure: " + errorMessage);
                    }
                });
    }

        public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        LatLng latLng = new LatLng(latitude, longitude);
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        //mMap.animateCamera(CameraUpdateFactory.zoomTo(15));

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("Users").child(uid);
        userRef.child("Location").child("latitude").setValue(latitude);
        userRef.child("Location").child("longitude").setValue(longitude);
    }

        private void startLocationUpdates() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.requestLocationUpdates(mLocationRequest, new com.google.android.gms.location.LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    onLocationChanged(locationResult.getLastLocation());
                }
            }, null);
        }
    }

        @Override
        protected void onStart() {
        super.onStart();

        mGoogleApiClient.connect();
    }

        @Override
        protected void onStop() {
        super.onStop();
        loc.child("latitude").removeValue();
        loc.child("longitude").removeValue();
        mGoogleApiClient.disconnect();
    }

        public void onConnected(Bundle bundle) {
        // Your implementation here
    }

        @Override
        public void onConnectionSuspended(int cause) {
        // Your implementation here
    }
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Handle connection failure here
    }

    private void createGeofenceFromDatabase() {
        DatabaseReference geofenceRef = FirebaseDatabase.getInstance().getReference()
                .child("Geofences")
                .child(uid); // Use the user's UID as the key for geofence data

        geofenceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    double latitude = dataSnapshot.child("latitude").getValue(Double.class);
                    double longitude = dataSnapshot.child("longitude").getValue(Double.class);
                    float radius = dataSnapshot.child("radius").getValue(Float.class);

                    if (latitude != 0 && longitude != 0 && radius != 0) {
                        LatLng geofenceCenter = new LatLng(latitude, longitude);
                        addCircle(geofenceCenter, radius);
                        addGeofence(geofenceCenter, radius);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle any errors or edge cases
            }
        });
    }


}
