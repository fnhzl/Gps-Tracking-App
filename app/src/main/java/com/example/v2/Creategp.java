package com.example.v2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;



public class Creategp extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListner;
    private Button create, Back;
    private EditText mcode;
    String uname = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_creategp);

        mAuth = FirebaseAuth.getInstance();
        create = (Button) findViewById(R.id.creategp);
        mcode = (EditText) findViewById(R.id.code);
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        final String uid = firebaseUser.getUid();

        final DatabaseReference userInfo = FirebaseDatabase.getInstance().getReference().child("Users").child(uid).child("Name");
        userInfo.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                uname = dataSnapshot.getValue().toString();
                Log.d("Updated", "Hey");
                Toast.makeText(getApplicationContext(), uname, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });

        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = mcode.getText().toString();

                DatabaseReference groupRef = FirebaseDatabase.getInstance().getReference().child("Groups").child(code);

                // Create a Map to store group details
                Map<String, Object> groupData = new HashMap<>();
                groupData.put("Admin", uid); // Store the admin's UID
                groupData.put("AdminName", uname); // Store the admin's name

                groupRef.setValue(groupData);

                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("Users").child(uid);
                userRef.child("Group").setValue(code);

                Intent intent = new Intent(Creategp.this, Home.class);
                startActivity(intent);

                Toast.makeText(getApplicationContext(), "Group Created", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(Creategp.this, Home.class);
        startActivity(intent);
    }
}
