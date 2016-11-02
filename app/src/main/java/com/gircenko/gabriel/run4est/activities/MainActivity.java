package com.gircenko.gabriel.run4est.activities;

import android.content.Intent;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.gircenko.gabriel.run4est.App;
import com.gircenko.gabriel.run4est.Constants;
import com.gircenko.gabriel.run4est.Helper;
import com.gircenko.gabriel.run4est.R;
import com.gircenko.gabriel.run4est.adapters.JogPagerAdapter;
import com.gircenko.gabriel.run4est.fragments.JogFragment;
import com.gircenko.gabriel.run4est.intefaces.OnJogFragmentCallback;
import com.gircenko.gabriel.run4est.models.JogModelWithId;
import com.gircenko.gabriel.run4est.models.UserType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements OnJogFragmentCallback {

    @BindView(R.id.vp_jogs)
    ViewPager vp_jogs;

    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private JogPagerAdapter adapter;

    /** String key is the jogId */
    private Map<String, JogModelWithId> map = new TreeMap<>();
    private int[] totalDistance = new int[JogPagerAdapter.PAGE_COUNT];

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();

        if (firebaseAuth.getCurrentUser() == null) userNotLoggedInGoToLogin();
        else userLoggedIn();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                startActivity(new Intent(this, SearchActivity.class));
                return true;

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.action_logout:
                logoutClicked();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @OnClick(R.id.fab)
    public void fabClicked() {
        startActivity(new Intent(this, EditJogActivity.class));
    }

    private void logoutClicked() {
        firebaseAuth.signOut();
        finish();
        startActivity(new Intent(this, LoginActivity.class));
    }

    public void setTotalDistanceText(int page, String totalCalories) {
        ((JogFragment) adapter.getItem(page)).setTotalTime(totalCalories);
    }

    public void applyDate(int page, String date) {
        ((JogFragment) adapter.getItem(page)).setDate(date);
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
    }

    private void userLoggedIn() {
        adapter = new JogPagerAdapter(getSupportFragmentManager());
        vp_jogs.setAdapter(adapter);
        vp_jogs.setCurrentItem(JogPagerAdapter.PAGE_COUNT - 1);  // sets last day as default

        for (int i = 0; i < JogPagerAdapter.PAGE_COUNT; i++) {
            applyDate(i, getDateByPage(i));
        }

        String userId = firebaseAuth.getCurrentUser().getUid();
        getUserType(userId);
        getJogsByUser(userId);
    }

    private void getJogsByUser(String userId) {
        DatabaseReference databaseReference = firebaseDatabase.getReference(Constants.USER_JOGS).child(userId);
        databaseReference.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Log.i(TAG, "onChildAdded");
                addJog(Helper.getJog(dataSnapshot));
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Log.i(TAG, "onChildChanged");
                onJogChanged(Helper.getJog(dataSnapshot));
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.i(TAG, "onChildRemoved");
                removeJog(Helper.getJog(dataSnapshot));
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                Log.i(TAG, "onChildMoved");
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.i(TAG, "onCancelled");
            }
        });
    }

    private String getDateByPage(int page) {
        // today
        Calendar date = Helper.getTodaysCalendar();
        date.add(Calendar.DAY_OF_MONTH, - (JogPagerAdapter.PAGE_COUNT - 1 - page));

        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT);
        return sdf.format(date.getTime());
    }

    private void userNotLoggedInGoToLogin() {
        finish();
        Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
        goToLogin();
    }

    private void onJogChanged(JogModelWithId jog) {
        removeJog(jog);
        addJog(jog);
    }

    private void addJog(JogModelWithId jog) {
        if (jog != null) {
            int day = Helper.getDayInLastWeekByDate(jog.getJog().getDate());
            if (day >= 0) {
                map.put(jog.getJogId(), jog);
                totalDistance[day] += jog.getJog().getDistance();
                setTotalDistance(day);
            }
        }
    }

    private void removeJog(JogModelWithId jog) {
        if (jog != null) {
            if (map.containsKey(jog.getJogId())) {
                JogModelWithId retrievedJog = map.get(jog.getJogId());
                int day = Helper.getDayInLastWeekByDate(retrievedJog.getJog().getDate());
                if (day >= 0) {
                    totalDistance[day] -= retrievedJog.getJog().getDistance();
                    map.remove(jog.getJogId());
                    setTotalDistance(day);
                }
            }
        }
    }

    private void onJogsChanged(List<JogModelWithId> jogs) {
        if (jogs != null && !jogs.isEmpty()) {
            int day = Helper.getDayInLastWeekByDate(jogs.get(0).getJog().getDate());
            if (day >= 0) {
                map = new TreeMap<>();
                totalDistance[day] = 0;
                Iterator<JogModelWithId> iterator = jogs.iterator();
                while (iterator.hasNext()) {
                    JogModelWithId jog = iterator.next();
                    map.put(jog.getJogId(), jog);
                    totalDistance[day] += jog.getJog().getDistance();
                }

                setTotalDistance(day);
            }

        } else {
            map = new TreeMap<>();
            totalDistance = new int[JogPagerAdapter.PAGE_COUNT];
            for (int i = 0; i < JogPagerAdapter.PAGE_COUNT; i++) {
                setTotalDistance(i);
            }
        }
    }

    private void onJogRemoved(JogModelWithId jogModelWithId) {
        if (jogModelWithId != null) {
            int day = Helper.getDayInLastWeekByDate(jogModelWithId.getJog().getDate());
            if (day >= 0) {
                totalDistance[day] -= jogModelWithId.getJog().getDistance();
                map.remove(jogModelWithId.getJogId());
                setTotalDistance(day);
            }
        }
    }

    private void setTotalDistance(int day) {
        if (totalDistance[day] != 0) {
            setTotalDistanceText(day, totalDistance[day] + " km");

        } else {
            setTotalDistanceText(day, Constants.MESSAGE_NO_JOGS);
        }
    }

    @Override
    public void onTotalCountClicked(String date) {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.BUNDLE_KEY_UID, firebaseAuth.getCurrentUser().getUid());
        bundle.putString(Constants.BUNDLE_KEY_DATE, date);
        Intent intent = new Intent(this, JogListActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    private void getUserType(String userId) {
        DatabaseReference databaseReference = firebaseDatabase.getReference(Constants.USER_PERMISSIONS).child(userId).child(Constants.USER_TYPE);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Long value = dataSnapshot.getValue(Long.class);
                String processValue = value != null ? value.toString() : null;
                onUserTypeRetrieved(processValue);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                onUserTypeError();
            }
        });
    }

    private void onUserTypeRetrieved(String s) {
        App.userType = UserType.determineUserType(s);
    }

    private void onUserTypeError() {}
}
