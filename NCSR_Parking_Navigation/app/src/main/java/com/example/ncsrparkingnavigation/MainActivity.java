package com.example.ncsrparkingnavigation;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import java.io.InputStream;
import java.util.Iterator;
import java.io.FileReader;
import java.util.Map;
import android.content.Context;
import android.content.res.AssetManager;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener{
    private String choice = "";
    private RadioGroup radioGroup;
    private TextView resultText;
    private Button findSpot;
    private Button navBtn;
    private List<String> resultData;
    private String foundLat;
    private String foundLon;
    private String foundStreetId;
    private Map<String, String> sensorsToLocation;
    Map<String, List<String>> parkingCsvData;


    protected Map<String, String> mapSensorsToLocations(Context context, String fileName) {
        Map<String, String> map = new HashMap<>();
        StringBuilder jsonContent = new StringBuilder();

        AssetManager assetManager = context.getAssets();

        try (InputStream is = assetManager.open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);

            }

            JSONObject jsonObject = new JSONObject(jsonContent.toString());
            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject innerObject = jsonObject.getJSONObject(key);
                JSONArray idArray = innerObject.getJSONArray("id");

                for (int i = 0; i < idArray.length(); i++) {
                    String idValue = idArray.getString(i);
                    map.put(idValue, key);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }

    protected Map<String, List<String>> readParkingCSV(Context context, String fileName) {
        Map<String, List<String>> dataMap = new HashMap<>();
        AssetManager assetManager = context.getAssets();

        try (InputStream is = assetManager.open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");

                if (values.length > 0) {
                    String key = values[0];
                    List<String> valuesList = new ArrayList<>();

                    for (int i = 1; i < values.length; i++) {
                        valuesList.add(values[i]);
                    }

                    dataMap.put(key, valuesList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dataMap;
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorsToLocation = mapSensorsToLocations(this, "parking-list.json");
        parkingCsvData = readParkingCSV(this, "parking-static.csv");

        AutoCompleteTextView acdropdown = findViewById(R.id.acDropdown);
        radioGroup = findViewById(R.id.constrGroup);
        resultText = findViewById(R.id.resultMsg);
        resultData = new ArrayList<>();
        acdropdown.setThreshold(1);
        findSpot = findViewById(R.id.findButton);
        navBtn = findViewById(R.id.mapButton);
        ArrayAdapter<CharSequence> acAdapter = ArrayAdapter.createFromResource(this, R.array.ncsr_locations, android.R.layout.select_dialog_item);
        acdropdown.setAdapter(acAdapter);
        acdropdown.setOnItemClickListener(this);
        acdropdown.setOnTouchListener((view, motionEvent) -> {
            acdropdown.showDropDown();
            return false;
        });
        acdropdown.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void afterTextChanged(Editable editable) {
                if(acdropdown.getText().toString().equals("")) {
                    choice = "";
                    findSpot.setEnabled(false);
                }
                else {
                    choice = acdropdown.getText().toString();
                    findSpot.setEnabled(true);
                }
            }
        });

        findSpot.setOnClickListener(view -> findAvailableSpot());
        navBtn.setOnClickListener(view -> openMapActivity());
    }

    public void openMapActivity() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("lat", foundLat);
        intent.putExtra("lon", foundLon);
        startActivity(intent);
    }

    @SuppressLint("StaticFieldLeak")
    public void findAvailableSpot() {
        List<String> ncsr_locations = Arrays.asList(getResources().getStringArray(R.array.ncsr_locations));
        if (ncsr_locations.contains(choice)) {
            resultText.setBackgroundColor(Color.parseColor("#abf5a7"));
            int selectedId = radioGroup.getCheckedRadioButtonId();
            RadioButton constraintRadioBtn = findViewById(selectedId);

            if (selectedId == -1) {
                resultText.setBackgroundColor(Color.parseColor("#ffb976"));
                resultText.setText(getString(R.string.invalidConstraintMsg));
            } else {
                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        resultText.setVisibility(View.INVISIBLE);
                        findSpot.setText(getString(R.string.loadingBtn));
                    }

                    @Override
                    protected String doInBackground(Void... voids) {
                        HttpURLConnection urlConnection = null;
                        try {
                            URL url = new URL("https://demokritos.smartiscity.gr/api/api.php?func=parkingAll&lang=el");
                            urlConnection = (HttpURLConnection) url.openConnection();
                            urlConnection.setRequestMethod("GET");
                            urlConnection.setRequestProperty("Accept", "application/json");

                            int code = urlConnection.getResponseCode();
                            if (code != 200) {
                                throw new IOException("Invalid response from server: " + code);
                            }

                            BufferedReader rd = new BufferedReader(new InputStreamReader(
                                    urlConnection.getInputStream()));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = rd.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            rd.close();
                            return sb.toString();
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (urlConnection != null) {
                                urlConnection.disconnect();
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(String response) {
                        super.onPostExecute(response);
                        findSpot.setText(getString(R.string.btnFunc));
                        if (response != null) {

                            try {
                                JSONArray jsonArray = new JSONArray(response);
                                resultData.clear();
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    JSONObject spot = jsonArray.getJSONObject(i);
                                    if (!spot.getBoolean("IsOccupied")) {
                                        resultData.add(spot.getString("id"));
                                    }
                                }

                                String foundSpot = findSpotBasedOnConstraints(resultData, constraintRadioBtn.getText().toString(), jsonArray);
                                updateUI(foundSpot, jsonArray);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                resultText.setBackgroundColor(Color.parseColor("#ffb976"));
                                resultText.setText(getString(R.string.invalidMsg));
                            }
                        } else {
                            resultText.setBackgroundColor(Color.parseColor("#ffb976"));
                            resultText.setText(getString(R.string.invalidMsg));
                        }
                    }
                }.execute();
            }
        } else {
            resultText.setBackgroundColor(Color.parseColor("#ffb976"));
            resultText.setText(getString(R.string.invalidMsg));
            if (resultText.getVisibility() == View.INVISIBLE)
                resultText.setVisibility(View.VISIBLE);
        }
    }

    private String findSpotBasedOnConstraints(List<String> availableSpots, String constraint, JSONArray jsonArray) {
        List<String> ruleOrder = getOrderBasedOnLocation(choice);
        return getParkingSpot(ruleOrder, jsonArray, constraint, availableSpots);
    }

    private void updateUI(String foundSpot, JSONArray jsonArray) {
        if (foundSpot.isEmpty()) {
            resultText.setBackgroundColor(Color.parseColor("#ffb976"));
            resultText.setText(getString(R.string.noVacancyMsg));
        } else {
            resultText.setBackgroundColor(Color.parseColor("#abf5a7"));

            try {
                JSONObject spot = null;
                for (int i = 0; i < jsonArray.length(); i++) {
                    if (jsonArray.getJSONObject(i).getString("id").equals(foundSpot)) {
                        spot = jsonArray.getJSONObject(i);
                        break;
                    }
                }

                if (spot != null) {
                    foundLat = spot.getString("Lat");
                    foundLon = spot.getString("Lon");
                    foundSpot = spot.getString("id");
                    List<String> sensorData = parkingCsvData.get(foundSpot);

                    if (foundSpot.equalsIgnoreCase("399")){
                        foundStreetId = "♿";
                    }
                    else{
                        foundStreetId = Objects.requireNonNull(sensorData.get(3));
                    }
                    resultText.setText(foundStreetId);
                }



            } catch (JSONException e) {
                e.printStackTrace();
            }

            resultText.setVisibility(View.VISIBLE);
            navBtn.setEnabled(true);
        }
    }

    private List<String> getOrderBasedOnLocation(String choice) {
        List<String> order1 = new ArrayList<>(Arrays.asList("Lefkippos1", "Lefkippos2", "Tesla", "Library1", "Library2"));
        List<String> order2 = new ArrayList<>(Arrays.asList("Tesla", "Lefkippos1", "Lefkippos2", "Library1", "Library2"));
        List<String> order3 = new ArrayList<>(Arrays.asList("Library2", "Library1", "Tesla", "Lefkippos1", "Lefkippos2"));
        List<String> order4 = new ArrayList<>(Arrays.asList("Library1", "Library2", "Tesla", "Lefkippos1", "Lefkippos2"));

        if (choice.equals("Lefkippos") || choice.equals("Technology Park") || choice.equals("SCio")
                || choice.equals("Fuelics")) {
            return order1;
        } else if (choice.equals("Tesla")) {
            return order2;
        } else if (choice.equals("Roboskel")) {
            return order3;
        } else if (choice.equals("Library") || choice.equals("Innovation Office")) {
            return order4;
        }

        return new ArrayList<>();
    }

    private String getParkingSpot(List<String> ruleOrder, JSONArray jsonArray, String constraint, List<String> availableSpots) {

        String foundSpot = "";

        if (constraint.equalsIgnoreCase("Motability car") && availableSpots.contains("399")){
            foundSpot = "399";
            List<String> sensorData = parkingCsvData.get(foundSpot);
            foundStreetId = "";
            foundLat = Objects.requireNonNull(sensorData.get(0));
            foundLon = Objects.requireNonNull(sensorData.get(1));
        }

        if(constraint.equalsIgnoreCase("Long vehicle")) {

            if (ruleOrder.contains("Lefkippos1")) {
                ruleOrder.remove("Lefkippos1");
            }

            if (ruleOrder.contains("Library2")) {
                ruleOrder.remove("Library2");
            }

        }

        for (String preferredSpot : ruleOrder) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject spot = jsonArray.getJSONObject(i);
                    String spotId = spot.getString("id");

                    if (!spot.getBoolean("IsOccupied") && availableSpots.contains(spotId)) {

                        if (preferredSpot.equalsIgnoreCase(sensorsToLocation.get(spotId)) && !(constraint.equalsIgnoreCase("Motability car")) ){
                            foundSpot = spotId;
                            List<String> sensorData = parkingCsvData.get(foundSpot);
                            foundStreetId = Objects.requireNonNull(sensorData.get(3));
                            foundLat = Objects.requireNonNull(sensorData.get(0));
                            foundLon = Objects.requireNonNull(sensorData.get(1));
                            break;
                        }

                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        return foundSpot;


    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        findSpot.setEnabled(true);
        choice = adapterView.getItemAtPosition(i).toString();
    }
}
