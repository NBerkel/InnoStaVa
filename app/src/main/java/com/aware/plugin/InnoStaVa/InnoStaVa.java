package com.aware.plugin.InnoStaVa;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;

import java.util.ArrayList;

/**
 * Created by niels on 07/12/2016.
 */

public class InnoStaVa extends AppCompatActivity {

    private TextView device_id;
    private Button join_study, set_settings, sync_data;

    private ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.card);

        REQUIRED_PERMISSIONS.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (!permissions_ok) {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY,
                    getPackageName() + "/" + InnoStaVa.class.getName());

            startActivity(permissions);
            finish();
        } else {
            Applications.isAccessibilityServiceActive(getApplicationContext());
            Aware.joinStudy(getApplicationContext(), "https://api.awareframework.com/index.php/webservice/index/989/73JTLkqEcwWZ");

            Toast.makeText(getApplicationContext(), "Thanks for joining!", Toast.LENGTH_SHORT).show();
        }

        Intent framework = new Intent(this, Aware.class);
        startService(framework);
    }

    @Override
    protected void onResume() {
        super.onResume();

//        String device_id_string = "UUID : " + Aware.getSetting(this, Aware_Preferences.DEVICE_ID);
//        device_id.setText(device_id_string);
//
//        if (Aware.isStudy(getApplicationContext())) {
//            join_study.setEnabled(false);
//            set_settings.setEnabled(false);
//        } else {
//            sync_data.setVisibility(View.INVISIBLE);
//        }
    }
}