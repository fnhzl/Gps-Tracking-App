package com.example.v2;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;


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

    private Map<String, Marker> userMarkers = new HashMap<>();

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
        // Initialize Firebase Realtime Database reference to user locations
        DatabaseReference userLocationsRef = FirebaseDatabase.getInstance().getReference().child("Users");

        // Add a listener to monitor changes in user locations
        userLocationsRef.addChildEventListener(new ChildEventListener() {

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                removeMarker(dataSnapshot.getKey());
            }
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, String s) {
                updateMarker(dataSnapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String s) {
                updateMarker(dataSnapshot);
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String s) {
                // Handle child moved event if needed
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle errors if needed
            }
        });

//        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//        String savedGroupID = sharedPreferences.getString("groupID", null);
//
//        if (savedGroupID != null) {
//            // Automatically start tracking the group
//            groupIDEditText.setText(savedGroupID);
//            trackGroup(savedGroupID);
//        }
    }

    private void saveGroupIDToSharedPreferences(String groupId) {
        SharedPreferences preferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("groupID", groupId);
        editor.apply();
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

            // Get the group ID from the user's child database
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("Users").child(uid);
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        String groupId = dataSnapshot.child("Group").getValue(String.class);

                        if (groupId != null && !groupId.isEmpty()) {
                            // Retrieve the group admin's UID from the "Groups" node in the database
                            DatabaseReference groupAdminRef = FirebaseDatabase.getInstance().getReference()
                                    .child("Groups")
                                    .child(groupId)
                                    .child("Admin");

                            groupAdminRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot adminSnapshot) {
                                    if (adminSnapshot.exists()) {
                                        String adminUid = adminSnapshot.getValue(String.class);

                                        // Check if the current user is the group admin
                                        if (adminUid != null && adminUid.equals(uid)) {
                                            // The user is the group admin, allow them to save the geofence
                                            DatabaseReference geofenceRef = FirebaseDatabase.getInstance().getReference()
                                                    .child("Groups") // Reference to the "Groups" node
                                                    .child(groupId) // Reference to the specific group using group ID
                                                    .child("Geofence"); // Child node for geofence data

                                            // Save geofence details to the database
                                            geofenceRef.child("latitude").setValue(latitude);
                                            geofenceRef.child("longitude").setValue(longitude);
                                            geofenceRef.child("radius").setValue(radius);

                                            Toast.makeText(MapsActivity.this, "Geofence saved for the group", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(MapsActivity.this, "Only group admins can save geofences", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                    // Handle database errors if needed
                                }
                            });
                        } else {
                            Toast.makeText(MapsActivity.this, "User does not belong to a group", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    // Handle database errors if needed
                }
            });
        } else {
            Toast.makeText(this, "No geofence to save", Toast.LENGTH_SHORT).show();
        }
    }






    private void trackGroup(String groupId) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference().child("Groups").child(groupId).child("Groupmembers");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mMap.clear();
                createGeofenceFromDatabase();
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
        // Check if the current user is the group admin
        checkIfUserIsGroupAdmin(new GroupAdminCheckListener() {
            @Override
            public void onGroupAdminCheckComplete(boolean isGroupAdmin) {
                if (isGroupAdmin) {
                    // The current user is the group admin, allow them to move the geofence location.
                    handleMapLongClick(latLng);
                } else {
                    // Display a message indicating that only the group admin can move the geofence.
                    Toast.makeText(MapsActivity.this, "Only group admins can move the geofence location", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    interface GroupAdminCheckListener {
        void onGroupAdminCheckComplete(boolean isGroupAdmin);
    }

    private void checkIfUserIsGroupAdmin(GroupAdminCheckListener listener) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("Users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String groupId = dataSnapshot.child("Group").getValue(String.class);

                    if (groupId != null && !groupId.isEmpty()) {
                        // Retrieve the group admin's UID from the "Groups" node in the database
                        DatabaseReference groupAdminRef = FirebaseDatabase.getInstance().getReference()
                                .child("Groups")
                                .child(groupId)
                                .child("Admin");

                        groupAdminRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot adminSnapshot) {
                                if (adminSnapshot.exists()) {
                                    String adminUid = adminSnapshot.getValue(String.class);

                                    // Check if the current user is the group admin
                                    boolean isGroupAdmin = adminUid != null && adminUid.equals(uid);
                                    listener.onGroupAdminCheckComplete(isGroupAdmin);
                                } else {
                                    listener.onGroupAdminCheckComplete(false);
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                // Handle database errors if needed
                                listener.onGroupAdminCheckComplete(false);
                            }
                        });
                    } else {
                        listener.onGroupAdminCheckComplete(false);
                    }
                } else {
                    listener.onGroupAdminCheckComplete(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle database errors if needed
                listener.onGroupAdminCheckComplete(false);
            }
        });
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
        circleOptions.fillColor(Color.argb(50, 255, 0, 0));
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
        // Get the user's group ID from the database
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String groupId = dataSnapshot.child("Group").getValue(String.class); // Assuming 'groupID' is the key for the group ID

                    if (groupId != null && !groupId.isEmpty()) {
                        // Use the retrieved group ID to fetch the geofence data from the group's database
                        DatabaseReference geofenceRef = FirebaseDatabase.getInstance().getReference()
                                .child("Groups")
                                .child(groupId)
                                .child("Geofence");

                        geofenceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot geofenceDataSnapshot) {
                                if (geofenceDataSnapshot.exists()) {
                                    double latitude = geofenceDataSnapshot.child("latitude").getValue(Double.class);
                                    double longitude = geofenceDataSnapshot.child("longitude").getValue(Double.class);
                                    float radius = geofenceDataSnapshot.child("radius").getValue(Float.class);

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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle any errors or edge cases
            }
        });
    }

    private void updateMarker(DataSnapshot dataSnapshot) {
//        mMap.clear();
        //createGeofenceFromDatabase();
        String userId = dataSnapshot.getKey();
        Double userLatitude = dataSnapshot.child("Location").child("latitude").getValue(Double.class);
        Double userLongitude = dataSnapshot.child("Location").child("longitude").getValue(Double.class);

        if (userLatitude != null && userLongitude != null) {
            LatLng userLatLng = new LatLng(userLatitude, userLongitude);
            String userName = dataSnapshot.child("Name").getValue(String.class);

            if (userName != null) {
                // Check if the marker is in the same group as the current user
                if (isUserInSameGroup(dataSnapshot)) {
                    // Check if the marker already exists
                    if (userMarkers.containsKey(userId)) {
                        Marker marker = userMarkers.get(userId);
                        LatLng previousLatLng = marker.getPosition();

                        // Check if the marker is outside the geofence
                        boolean wasInside = isMarkerInsideGeofence(previousLatLng);
                        boolean isInside = isMarkerInsideGeofence(userLatLng);

                        if (!wasInside && isInside) {
                            showGeofenceNotification(userName + " has moved inside the geofence.");
                        } else if (wasInside && !isInside) {
                            showGeofenceNotification(userName + " has moved outside the geofence.");
                        }

                        marker.setPosition(userLatLng);
                    } else {
                        // Create a new marker for the user
                        MarkerOptions marker = new MarkerOptions()
                                .position(userLatLng)
                                .title(userName);
                        userMarkers.put(userId, mMap.addMarker(marker));

                        // Check if the marker is outside the geofence
                        if (!isMarkerInsideGeofence(userLatLng)) {
                            showGeofenceNotification(userName + " has moved outside the geofence.");
                        }
                    }
                }
            }
        }
    }

    private boolean isUserInSameGroup(DataSnapshot userSnapshot) {
        // Retrieve the group ID of the current user
        String currentUserGroupId = groupIDEditText.getText().toString();

        // Retrieve the group ID of the user from the data snapshot
        String userGroupId = userSnapshot.child("Group").getValue(String.class);

        // Check if both the current user and the user from the snapshot are in the same group
        return currentUserGroupId != null && userGroupId != null && currentUserGroupId.equals(userGroupId);
    }


    private boolean isMarkerInsideGeofence(LatLng markerLatLng) {
        if (geofenceCircle == null) {
            return false;
        }

        // Calculate the distance between the marker's LatLng and the center of the geofence
        float[] distance = new float[1];
        Location.distanceBetween(markerLatLng.latitude, markerLatLng.longitude,
                geofenceCircle.getCenter().latitude, geofenceCircle.getCenter().longitude, distance);

        // Compare the distance with the radius of the geofence
        return distance[0] <= geofenceCircle.getRadius();
    }


    private void showGeofenceNotification(String notificationMessage) {
        // Create a notification to alert the user about marker movement
        String channelId = "GeofenceChannelId";
        String channelName = "GeofenceChannel";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Geofence Alert")
                .setContentText(notificationMessage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // Create the notification channel (for Android 8.0 and higher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // Show the notification
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        notificationManager.notify(1, builder.build());
    }

    private void removeMarker(String userId) {
        if (userMarkers.containsKey(userId)) {
            // Remove the marker from the map
            userMarkers.get(userId).remove();
            userMarkers.remove(userId);
        }
    }
}