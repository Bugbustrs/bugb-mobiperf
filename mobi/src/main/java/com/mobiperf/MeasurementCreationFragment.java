/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.mobiperf;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.Toast;

import com.mobiperf.measurements.DnsLookupTask;
import com.mobiperf.measurements.DnsLookupTask.DnsLookupDesc;
import com.mobiperf.measurements.HttpTask;
import com.mobiperf.measurements.HttpTask.HttpDesc;
import com.mobiperf.measurements.PingTask;
import com.mobiperf.measurements.PingTask.PingDesc;
import com.mobiperf.measurements.TCPThroughputTask;
import com.mobiperf.measurements.TCPThroughputTask.TCPThroughputDesc;
import com.mobiperf.measurements.TracerouteTask;
import com.mobiperf.measurements.TracerouteTask.TracerouteDesc;
import com.mobiperf.measurements.UDPBurstTask;
import com.mobiperf.measurements.UDPBurstTask.UDPBurstDesc;
import com.mobiperf.util.MLabNS;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mobiperf.R.id;
import static com.mobiperf.R.layout;
import static com.mobiperf.R.string;

/**
 * The UI Activity that allows users to create their own measurements
 */
public class MeasurementCreationFragment extends Fragment {

  private static final int NUMBER_OF_COMMON_VIEWS = 1;
  public static final String TAB_TAG = "MEASUREMENT_CREATION";
  /**
   * This stores the status on the permissions that we are going to be using.
   */
  public static EnumMap<Config.PERMISSION_IDS, Boolean> PERMISSION_SETTINGS;

  /**
   * This is a bad idea as it causes memory leaks. But then it is very much needed for now. To fix this we first need to fix the issue to deal with permissions.
   */
  private SpeedometerApp parent;
  private String measurementTypeUnderEdit;
  private ArrayAdapter<String> spinnerValues;
  private String udpDir;
  private String tcpDir;
  private View v;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    v = inflater.inflate(layout.measurement_creation_main, container, false);
    setHasOptionsMenu(true);
    if (v.getParent()!=null && (v.getParent().getClass().getName().compareTo("SpeedometerApp") != 0))
      throw new AssertionError();
    this.parent = SpeedometerApp.getCurrentApp();

    /*set the value of MEASUREMENT_CREATION_CONTEXT to this*/

    /* Initialize the measurement type spinner */
    Spinner spinner = v.findViewById(id.measurementTypeSpinner);
    spinnerValues = new ArrayAdapter<>(v.getContext(), layout.spinner_layout);
    for (String name : MeasurementTask.getMeasurementNames()) {
      // adding list of visible measurements
      if (MeasurementTask.getVisibilityForMeasurementName(name)) {
        spinnerValues.add(name);
      }
    }
    spinnerValues.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(spinnerValues);
    spinner.setOnItemSelectedListener(new MeasurementTypeOnItemSelectedListener());
    spinner.requestFocus();
    /* Setup the 'run' button */
    Button runButton = v.findViewById(id.runTaskButton);
    runButton.setOnClickListener(new ButtonOnClickListener());

    this.measurementTypeUnderEdit = PingTask.TYPE;
    setupEditTextFocusChangeListener();

    this.udpDir = "Up";
    this.tcpDir = "Up";

    final RadioButton radioUDPUp = v.findViewById(id.UDPBurstUpButton);
    final RadioButton radioUDPDown = v.findViewById(id.UDPBurstDownButton);
    final RadioButton radioTCPUp = v.findViewById(id.TCPThroughputUpButton);
    final RadioButton radioTCPDown = v.findViewById(id.TCPThroughputDownButton);

    radioUDPUp.setChecked(true);
    radioUDPUp.setOnClickListener(new UDPRadioOnClickListener());
    radioUDPDown.setOnClickListener(new UDPRadioOnClickListener());

    Button udpSettings = v.findViewById(id.UDPSettingsButton);
    udpSettings.setOnClickListener(new UDPSettingsOnClickListener());

    radioTCPUp.setChecked(true);
    radioTCPUp.setOnClickListener(new TCPRadioOnClickListener());
    radioTCPDown.setOnClickListener(new TCPRadioOnClickListener());
    initPermMap();
    return v;
  }

  private void setupEditTextFocusChangeListener() {
    EditBoxFocusChangeListener textFocusChangeListener = new EditBoxFocusChangeListener();
    EditText text = v.findViewById(id.pingTargetText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = v.findViewById(id.tracerouteTargetText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = v.findViewById(id.httpUrlText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = v.findViewById(id.dnsLookupText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = v.findViewById(id.UDPBurstIntervalText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = v.findViewById(id.UDPBurstPacketCountText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = v.findViewById(id.UDPBurstPacketSizeText);
    text.setOnFocusChangeListener(textFocusChangeListener);
  }

  @Override
  public void onStart() {
    super.onStart();
    this.populateMeasurementSpecificArea();
  }

  private void clearMeasurementSpecificViews(TableLayout table) {
    for (int i = NUMBER_OF_COMMON_VIEWS; i < table.getChildCount(); i++) {
      View v = table.getChildAt(i);
      v.setVisibility(View.GONE);
    }
  }

  private void populateMeasurementSpecificArea() {
    TableLayout table = v.findViewById(id.measurementCreationLayout);
    this.clearMeasurementSpecificViews(table);
    if (this.measurementTypeUnderEdit.compareTo(PingTask.TYPE) == 0) {
      v.findViewById(id.pingView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(HttpTask.TYPE) == 0) {
      v.findViewById(id.httpUrlView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(TracerouteTask.TYPE) == 0) {
      v.findViewById(id.tracerouteView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(DnsLookupTask.TYPE) == 0) {
      v.findViewById(id.dnsTargetView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(UDPBurstTask.TYPE) == 0) {
      v.findViewById(id.UDPBurstDirView).setVisibility(View.VISIBLE);
      v.findViewById(id.UDPSettingsButton).setVisibility(View.VISIBLE);
//      v.findViewById(R.id.UDPBurstPacketSizeView).setVisibility(View.VISIBLE);
//      v.findViewById(R.id.UDPBurstPacketCountView).setVisibility(View.VISIBLE);
//      v.findViewById(R.id.UDPBurstIntervalView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(TCPThroughputTask.TYPE) == 0) {
      v.findViewById(id.TCPThroughputDirView).setVisibility(View.VISIBLE);
    }
  }

  private void hideKeyboard(EditText textBox) {
    if (textBox != null) {
      InputMethodManager imm = (InputMethodManager) getActivity().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(textBox.getWindowToken(), 0);
    }
  }
  private class UDPSettingsOnClickListener implements OnClickListener {
    private boolean isShowSettings = false;
    @Override
    public void onClick(View v) {
      Button b = (Button)v;
      if (!isShowSettings) {
        isShowSettings = true;
        b.setText(getString(string.Collapse_Advanced_Settings));
        v.findViewById(id.UDPBurstPacketSizeView).setVisibility(View.VISIBLE);
        v.findViewById(id.UDPBurstPacketCountView).setVisibility(View.VISIBLE);
        v.findViewById(id.UDPBurstIntervalView).setVisibility(View.VISIBLE);
      }
      else {
        isShowSettings = false;
        b.setText(getString(string.Expand_Advanced_Settings));
        v.findViewById(id.UDPBurstPacketSizeView).setVisibility(View.GONE);
        v.findViewById(id.UDPBurstPacketCountView).setVisibility(View.GONE);
        v.findViewById(id.UDPBurstIntervalView).setVisibility(View.GONE);
      }
    }
  }

  private class UDPRadioOnClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      RadioButton rb = (RadioButton) v;
      MeasurementCreationFragment.this.udpDir = (String) rb.getText();
    }
  }

  private class TCPRadioOnClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      RadioButton rb = (RadioButton) v;
      MeasurementCreationFragment.this.tcpDir = (String) rb.getText();
    }
  }

  private class ButtonOnClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      checkAndRequestPerms();
      MeasurementTask newTask = null;
      boolean showLengthWarning = false;
      try {
        switch (measurementTypeUnderEdit) {
          case PingTask.TYPE: {
            EditText pingTargetText = getView().findViewById(id.pingTargetText);
            Map<String, String> params = new HashMap<>();
            params.put("target", pingTargetText.getText().toString());
            PingDesc desc = new PingDesc(null,
                    Calendar.getInstance().getTime(),
                    null,
                    Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                    Config.DEFAULT_USER_MEASUREMENT_COUNT,
                    MeasurementTask.USER_PRIORITY,
                    params);
            newTask = new PingTask(desc, getActivity().getApplicationContext());
            break;
          }
          case HttpTask.TYPE: {
            EditText httpUrlText = getView().findViewById(id.httpUrlText);
            Map<String, String> params = new HashMap<>();
            params.put("url", httpUrlText.getText().toString());
            params.put("method", "get");
            HttpDesc desc = new HttpDesc(null,
                    Calendar.getInstance().getTime(),
                    null,
                    Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                    Config.DEFAULT_USER_MEASUREMENT_COUNT,
                    MeasurementTask.USER_PRIORITY,
                    params);
            newTask = new HttpTask(desc, getActivity().getApplicationContext());
            break;
          }
          case TracerouteTask.TYPE: {
            EditText targetText = getView().findViewById(id.tracerouteTargetText);
            Map<String, String> params = new HashMap<>();
            params.put("target", targetText.getText().toString());
            TracerouteDesc desc = new TracerouteDesc(null,
                    Calendar.getInstance().getTime(),
                    null,
                    Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                    Config.DEFAULT_USER_MEASUREMENT_COUNT,
                    MeasurementTask.USER_PRIORITY,
                    params);
            newTask =
                    new TracerouteTask(desc, getActivity().getApplicationContext());
            showLengthWarning = true;
            break;
          }
          case DnsLookupTask.TYPE: {
            EditText dnsTargetText = getView().findViewById(id.dnsLookupText);
            Map<String, String> params = new HashMap<>();
            params.put("target", dnsTargetText.getText().toString());
            DnsLookupDesc desc = new DnsLookupDesc(null,
                    Calendar.getInstance().getTime(),
                    null,
                    Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                    Config.DEFAULT_USER_MEASUREMENT_COUNT,
                    MeasurementTask.USER_PRIORITY,
                    params);
            newTask =
                    new DnsLookupTask(desc, getActivity().getApplicationContext());
            break;
          }
          case UDPBurstTask.TYPE: {
            Map<String, String> params = new HashMap<>();
            // TODO(dominic): Support multiple servers for UDP. For now, just
            // m-lab.
            params.put("target", MLabNS.TARGET);
            params.put("direction", udpDir);
            // Get UDP Burst packet size
            EditText UDPBurstPacketSizeText =
                    getView().findViewById(id.UDPBurstPacketSizeText);
            params.put("packet_size_byte"
                    , UDPBurstPacketSizeText.getText().toString());
            // Get UDP Burst packet count
            EditText UDPBurstPacketCountText =
                    getView().findViewById(id.UDPBurstPacketCountText);
            params.put("packet_burst"
                    , UDPBurstPacketCountText.getText().toString());
            // Get UDP Burst interval
            EditText UDPBurstIntervalText =
                    getView().findViewById(id.UDPBurstIntervalText);
            params.put("udp_interval"
                    , UDPBurstIntervalText.getText().toString());

            UDPBurstDesc desc = new UDPBurstDesc(null,
                    Calendar.getInstance().getTime(),
                    null,
                    Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                    Config.DEFAULT_USER_MEASUREMENT_COUNT,
                    MeasurementTask.USER_PRIORITY,
                    params);
            newTask = new UDPBurstTask(desc
                    , getActivity().getApplicationContext());
            break;
          }
          case TCPThroughputTask.TYPE: {
            Map<String, String> params = new HashMap<>();
            params.put("target", MLabNS.TARGET);
            params.put("dir_up", tcpDir);
            TCPThroughputDesc desc = new TCPThroughputDesc(null,
                    Calendar.getInstance().getTime(),
                    null,
                    Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
                    Config.DEFAULT_USER_MEASUREMENT_COUNT,
                    MeasurementTask.USER_PRIORITY,
                    params);
            newTask = new TCPThroughputTask(desc,
                    getActivity().getApplicationContext());
            showLengthWarning = true;
            break;
          }
        }

        if (newTask != null) {
          MeasurementScheduler scheduler = parent.getScheduler();
          if (scheduler != null && scheduler.submitTask(newTask)) {
            /*
             * Broadcast an intent with MEASUREMENT_ACTION so that the scheduler will immediately
             * handles the user measurement
             */
           getActivity().getApplicationContext().sendBroadcast(
                new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION));
            SpeedometerApp parent =SpeedometerApp.getCurrentApp();
            String toastStr =
                MeasurementCreationFragment.this.getString(string.userMeasurementSuccessToast);
            if (showLengthWarning) {
              toastStr += newTask.getDescriptor() + " measurements can be long. Please be patient.";
            }
            Toast.makeText(getView().getContext(), toastStr, Toast.LENGTH_LONG).show();

            if (scheduler.getCurrentTask() != null) {
              showBusySchedulerStatus();
            }
          } else {
            Toast.makeText(getView().getContext(), string.userMeasurementFailureToast,
                Toast.LENGTH_LONG).show();
          }
        }
      } catch (InvalidParameterException e) {
        Logger.e("InvalidParameterException when creating user measurements", e);
        Toast.makeText(getView().getContext(),
                       string.invalidParameterExceptionMeasurementToast +
                       ": " + e.getMessage(),
                       Toast.LENGTH_LONG).show();
      }
    }

  }

  private void showBusySchedulerStatus() {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    intent.putExtra(
        UpdateIntent.STATUS_MSG_PAYLOAD, getString(string.userMeasurementBusySchedulerToast));
    getActivity().getApplicationContext().sendBroadcast(intent);
  }

  private class EditBoxFocusChangeListener implements OnFocusChangeListener {

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
      switch (v.getId()) {
        case id.pingTargetText:
          /*
           *
           * TODO(Wenjie): Verify user input
           */
          break;
        case id.httpUrlText:
          /*
           *
           * TODO(Wenjie): Verify user input
           */
          break;
        default:
          break;
      }
      if (!hasFocus) {
        hideKeyboard((EditText) v);
      }
    }
  }

  private class MeasurementTypeOnItemSelectedListener implements OnItemSelectedListener {

    /*
     * Handles the ItemSelected event in the MeasurementType spinner. Populate the measurement
     * specific area based on user input
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
      measurementTypeUnderEdit =
          MeasurementTask.getTypeForMeasurementName(spinnerValues.getItem((int) id));
      if (measurementTypeUnderEdit != null) {
        populateMeasurementSpecificArea();
      }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
      // TODO(Wenjie): at the moment there is nothing we need to do here
    }
  }



  private static void initPermMap(){
    PERMISSION_SETTINGS = new EnumMap<>(Config.PERMISSION_IDS.class);
    for(Config.PERMISSION_IDS permission_id: Config.PERMISSION_IDS.values()) {
      /* Assume false when starting*/
      PERMISSION_SETTINGS.put(permission_id, false);
    }
  }

  public void checkAndRequestPerms(){
    List<String> list = new ArrayList<>();
    if (ContextCompat.checkSelfPermission(v.getContext(),
            Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED)
      list.add(Manifest.permission.READ_PHONE_STATE);
    if (ContextCompat.checkSelfPermission(v.getContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
      list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    if(list.size()>0){
      String [] temp = new String[list.size()];
      for (int i = 0; i < list.size(); i++) {
        temp[i]= list.get(i);
      }
      Logger.i("Requesting permissions from the device");
      ActivityCompat.requestPermissions(SpeedometerApp.getCurrentApp(),temp,0);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions, int[] grantResults) {
    for (int i = 0, permissionsLength = permissions.length; i < permissionsLength; i++) {
      String s = permissions[i];
      s=s.substring(s.lastIndexOf('.'));
      PERMISSION_SETTINGS.put(Config.PERMISSION_IDS.valueOf(s),grantResults[i] == PackageManager.PERMISSION_GRANTED);
    }
  }
}
