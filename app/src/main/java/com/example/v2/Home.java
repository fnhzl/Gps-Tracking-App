package com.example.v2;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
public class Home extends AppCompatActivity implements View.OnClickListener {

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;
    ImageView create_gp,join_gp,Map,GroupView;

    private android.location.Location mLastLocation ;

    double latitude;
    double longitude;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        create_gp = findViewById(R.id.Find);
        join_gp =  findViewById(R.id.add);

        create_gp.setOnClickListener(this);
        join_gp.setOnClickListener(this);


        mAuth = FirebaseAuth.getInstance();
        String id = mAuth.getCurrentUser().getUid();


        //extra btn
        GroupView= findViewById(R.id.Profile);
        GroupView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                Intent i = new Intent(Home.this, MainScreen.class);
                startActivity(i);
            }
        });
        mAuth = FirebaseAuth.getInstance();

        Map = findViewById(R.id.Gps);
        Map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (id != null) {
                    Intent i = new Intent(Home.this, MapsActivity.class);
                    i.putExtra("UID", id); // Pass the UID as an intent extra
                    startActivity(i);
                }
            }
        });




    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(1, 1, 2, "Log Out");
        menu.add(1,1,1,"Setting");

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
            case 1:

                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(Home.this,Login.class);
                startActivity(intent);
                finish();
                break;
            case 2:
                break;


    }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.Find: Intent i = new Intent(Home.this,Creategp.class);
                startActivity(i);
                finish(); break;

            case R.id.add: Intent j = new Intent(Home.this,Joingp.class);
                startActivity(j);
                finish();break;
        }

    }



    public void showToast(String message)
    {
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();

    }


}

