package uk.ac.tees.gingerbread.myfitness.Fragments;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.share.Sharer;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;

import uk.ac.tees.gingerbread.myfitness.Adapters.ProgressPicAdapter;
import uk.ac.tees.gingerbread.myfitness.Models.InfoEntry;
import uk.ac.tees.gingerbread.myfitness.Models.PictureEntry;
import uk.ac.tees.gingerbread.myfitness.R;
import uk.ac.tees.gingerbread.myfitness.Services.DatabaseHandler;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 */
public class InfoFragment extends Fragment {

    private long timeInMillis;
    private long todayTimeInMillis;
    private Calendar c;

    private InfoEntry info;
    private DatabaseHandler dh;

    private EditText weightField;
    private EditText heightField;
    private Spinner activitySpinner;
    private Spinner goalSpinner;
    private FloatingActionButton addPictureButton;

    private CallbackManager callbackManager;
    private ShareDialog shareDialog;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        int facebookRequest = shareDialog.getRequestCode();
        switch(requestCode) {
            case 0:
                if(resultCode == RESULT_OK){
                    Bundle extras = imageReturnedIntent.getExtras();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 8;
                    Bitmap imageBitmap= (Bitmap) extras.get("data");

                    //Compression of bitmap
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                    byte[] byteArray = stream.toByteArray();
                    imageBitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.length);

                    DatabaseHandler dh = new DatabaseHandler(getContext());
                    dh.addPictureEntry(timeInMillis,imageBitmap);
                    populateImageList();
                }
                break;
        }
    }

    public void updateTitleBar(long date)
    {
        c = Calendar.getInstance();
        c.setTimeInMillis(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        getActivity().setTitle("Personal Info " + c.get(Calendar.DAY_OF_MONTH) + "/" + (c.get(Calendar.MONTH)+1) + "/" + c.get(Calendar.YEAR));
    }

    public void populateImageList()
    {
        ListView pictureList = (ListView) getView().findViewById(R.id.info_picture_list);
        final DatabaseHandler dh = new DatabaseHandler(getContext());

        final ArrayList<PictureEntry> pictures = dh.getPicturesForDate(timeInMillis);
        ProgressPicAdapter adapter = new ProgressPicAdapter(getActivity(),pictures);
        pictureList.setAdapter(adapter);
        pictureList.setItemsCanFocus(true);
        pictureList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                final PictureEntry picture = pictures.get(position);
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder
                        .setTitle("Picture options")
                        .setMessage("Choose an option")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("Cancel", new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int which) {}
                        })
                        .setNegativeButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //Remove picture entry
                                dh.deletePictureEntry(position,timeInMillis);
                                pictures.remove(picture);
                                //Refresh
                                populateImageList();
                            }
                        })
                        .setNeutralButton("Share to Facebook", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharePhoto photo = new SharePhoto.Builder()
                                        .setBitmap(picture.getPicture())
                                        .build();
                                if (ShareDialog.canShow(SharePhotoContent.class)) {
                                    SharePhotoContent content = new SharePhotoContent.Builder()
                                            .addPhoto(photo)
                                            .build();
                                    shareDialog.show(content);
                                }
                                else
                                {
                                    Toast.makeText(getContext(),"Facebook app required. Opening store to install.",Toast.LENGTH_LONG).show();
                                    //Open
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.facebook.katana&hl=en_GB")));
                                    } catch (android.content.ActivityNotFoundException e) {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.facebook.katana&hl=en_GB")));
                                    }
                                }
                            }
                        })
                        .show();
            }
        });
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_calendar) {
            DatePickerDialog datePickerDialog = new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener()
            {
                @Override
                public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth)
                {
                    final Long previousDate = timeInMillis;
                    c.set(year, monthOfYear, dayOfMonth);
                    timeInMillis = c.getTimeInMillis();

                    if (dh.getInfoEntry(timeInMillis) == null)
                    {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder
                                .setTitle("Create info")
                                .setMessage("Could not find info for selected day. Would you like to create some?")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener(){
                                    public void onClick(DialogInterface dialog, int which) {
                                        //Add for selected date
                                        info = dh.getInfoEntry(todayTimeInMillis);
                                        info.setDate(timeInMillis);
                                        info.setGoal("Not Set");
                                        dh.addInfo(info);
                                        //Update text fields
                                        updateFields(info);
                                        updateTitleBar(timeInMillis);
                                        populateImageList();
                                        Toast.makeText(getContext(),"Info created",Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        c.setTimeInMillis(previousDate);
                                    }
                                })
                                .show();
                    }
                    else
                    {
                        info = dh.getInfoEntry(timeInMillis);
                        updateFields(info);
                        populateImageList();
                    }
                }
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.getDatePicker().setMaxDate(todayTimeInMillis);
            datePickerDialog.show();
            return true;
        }

        return false;
    }

    public InfoFragment() {
        // Required empty public constructor
    }

    public void updateFields(InfoEntry info)
    {
        List<String> goalList = new ArrayList<>();
        goalList.add("Not Set");
        goalList.add("Build Muscle");
        goalList.add("Lose Weight");
        goalList.add("Stay Healthy");

        weightField.setText(String.valueOf(info.getWeight()));
        heightField.setText(String.valueOf(info.getHeight()));
        activitySpinner.setSelection(info.getActivityLevel() - 1);

        if (timeInMillis != todayTimeInMillis)
        {
            addPictureButton.setVisibility(View.INVISIBLE);
        }
        else
        {
            addPictureButton.setVisibility(View.VISIBLE);
        }

        goalSpinner.setSelection(goalList.indexOf(info.getGoal()));
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_info, container, false);
        setHasOptionsMenu(true);

        weightField = (EditText) view.findViewById(R.id.editText_weight);
        heightField = (EditText) view.findViewById(R.id.editText_Height);

        addPictureButton = (FloatingActionButton) view.findViewById(R.id.add_picture_button);

        activitySpinner = (Spinner) view.findViewById(R.id.spinner_activity);
        List<String> activityList = new ArrayList<>();
        activityList.add("Sedentary");
        activityList.add("Lightly active");
        activityList.add("Active");
        activityList.add("Very active");
        ArrayAdapter<String> dataAdapterActivity = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_spinner_item, activityList);
        dataAdapterActivity.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activitySpinner.setAdapter(dataAdapterActivity);

        goalSpinner = (Spinner) view.findViewById(R.id.spinner_goal);
        List<String> goalList = new ArrayList<>();
        goalList.add("Not Set");
        goalList.add("Build Muscle");
        goalList.add("Lose Weight");
        goalList.add("Stay Healthy");
        ArrayAdapter<String> goalAdapterActivity = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_spinner_item, goalList);
        goalAdapterActivity.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        goalSpinner.setAdapter(goalAdapterActivity);

        //Set calendar up
        c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        timeInMillis = c.getTimeInMillis();
        todayTimeInMillis = c.getTimeInMillis();

        updateTitleBar(timeInMillis);

        dh = new DatabaseHandler(getActivity());
        //Create record for diet in db if there isn't one for today
        info = dh.getInfoEntry(todayTimeInMillis);
        if (info == null)
        {
            //Copy last one and set date to today
            InfoEntry info = dh.getLatestInfo();
            if (info != null)
            {
                info.setDate(todayTimeInMillis);
                dh.addInfo(info);
            }
            else
            {
                info = new InfoEntry(0,0,0,todayTimeInMillis,"Not set");
                dh.addInfo(info);
            }
        }

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Facebook share dialog
        shareDialog = new ShareDialog(this);
        callbackManager = CallbackManager.Factory.create();

        shareDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>() {
            @Override
            public void onSuccess(Sharer.Result result) {
                Toast.makeText(getContext(),"Shared to Facebook",Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {

            }

            @Override
            public void onError(FacebookException error) {

            }
        });

        updateFields(info);
        populateImageList();

        addPictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Ask whether to pick from camera, gallery or cancel
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Add picture");
                builder.setIcon(R.drawable.ic_menu_gallery);
                builder.setMessage("Where would you like to add a picture from?");
                builder.setPositiveButton("Camera",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                //Start camera intent and get bitmap and save to db and refresh
                                Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                startActivityForResult(takePicture, 0);//zero can be replaced with any action code
                            }
                        });

                builder.setNeutralButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                builder.show();
            }
        });

        weightField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equalsIgnoreCase(""))
                {
                    if (info.getWeight() != Float.parseFloat(s.toString()))
                    {
                        info.setWeight(Float.parseFloat(s.toString()));
                        dh.updateInfoEntry(info);
                        Toast.makeText(getActivity().getApplicationContext(),"Weight updated.",Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        heightField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equalsIgnoreCase(""))
                {
                    if (info.getHeight() != Float.parseFloat(s.toString()))
                    {
                        info.setHeight(Float.parseFloat(s.toString()));
                        dh.updateInfoEntry(info);
                        Toast.makeText(getActivity().getApplicationContext(),"Height updated.",Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        activitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position + 1 != info.getActivityLevel())
                {
                    info.setActivityLevel(position + 1);
                    dh.updateInfoEntry(info);
                    Toast.makeText(getActivity().getApplicationContext(),"Activity level updated.",Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        goalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                List<String> goalList = new ArrayList<>();
                goalList.add("Not Set");
                goalList.add("Build Muscle");
                goalList.add("Lose weight");
                goalList.add("Stay healthy");

                if (!(info.getGoal().equals(goalList.get(position))))
                {
                    info.setGoal(goalList.get(position));
                    dh.updateInfoEntry(info);
                    Toast.makeText(getActivity().getApplicationContext(),"Goal updated.",Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateTitleBar(timeInMillis);
    }
}
