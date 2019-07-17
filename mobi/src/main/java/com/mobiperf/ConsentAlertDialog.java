package com.mobiperf;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

public class ConsentAlertDialog extends DialogFragment {

    public static ConsentAlertDialog newInstance() {
        return new ConsentAlertDialog();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        return new AlertDialog.Builder(getActivity())
                .setMessage(getString(R.string.terms))
                .setPositiveButton("Okay, got it",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((SpeedometerApp) getActivity()).doPositiveClick();
                            }
                        }
                )
                .setNegativeButton("No thanks",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((SpeedometerApp) getActivity()).doNegativeClick();
                            }
                        }
                )
                .create();
    }
}
