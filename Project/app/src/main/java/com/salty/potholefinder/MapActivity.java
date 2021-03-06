package com.salty.potholefinder;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.util.Log;
import android.widget.ImageView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.salty.potholefinder.data.FileSystemRepository;
import com.salty.potholefinder.model.Pothole;
import com.salty.potholefinder.model.PotholeBuilder;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.List;
import java.util.UUID;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_TAKE_PHOTO = 1;

    private String mCurrentPicturePath;
    private GoogleMap mMap;
    private GoogleApiClient googleApiClient;
    private LocationManager locationManager;
    private FloatingActionButton fab;
    private boolean fabMenuIsOpen = false;

    private Menu mMenu;

    private ClusterManager<Pothole> mClusterManager;

    private FileSystemRepository<Pothole> potHoleRepo;

    private Location lastLocation;

    private LatLng currentLatLnt;

    Random r = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);

        fab = (FloatingActionButton) this.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fabOnClick(v);
            }
        });

        final FloatingActionButton fabLocation = (FloatingActionButton) this.findViewById(R.id.fab_location);
        fabLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fabOnClick(v);
                try {
                    if (ActivityCompat.checkSelfPermission(MapActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(MapActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.INTERNET}, 10);
                    }
                    lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }

                if (lastLocation != null) {
                    if (mMap.getCameraPosition().zoom < 15f)
                        mMap.animateCamera(CameraUpdateFactory.zoomTo(15f), 1000, null);
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude())), 1000, null);
                    createAndSavePothole(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()));
                    addEffects();
                }
            }
        });

        final FloatingActionButton fabCamera = (FloatingActionButton) this.findViewById(R.id.fab_camera);
        fabCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fabOnClick(v);
                dispatchTakePictureIntent();
            }
        });

        potHoleRepo = new FileSystemRepository<>(getApplicationContext());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        mMenu = menu;

        menu.findItem(R.id.action_heatmap).setChecked(Globals.isHeatMapActive);
        menu.findItem(R.id.action_cluster).setChecked(Globals.isClusterActive);
        menu.findItem(R.id.data_insert).setChecked(Globals.isDataInsertActive);
        menu.findItem(R.id.data_insert_photo).setChecked(Globals.isDataInsertCameraActive);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_heatmap:
                Globals.isHeatMapActive = !Globals.isHeatMapActive;
                item.setChecked(Globals.isHeatMapActive);
                addEffects();
                return true;
            case R.id.action_cluster:
                Globals.isClusterActive = !Globals.isClusterActive;
                item.setChecked(Globals.isClusterActive);
                addEffects();
                return true;
            case R.id.data_insert:
                Globals.isDataInsertActive = !Globals.isDataInsertActive;
                Globals.isDataInsertCameraActive = false;
                mMenu.findItem(R.id.data_insert_photo).setChecked(Globals.isDataInsertCameraActive);
                item.setChecked(Globals.isDataInsertActive);
                return true;
            case R.id.data_insert_photo:
                Globals.isDataInsertCameraActive = !Globals.isDataInsertCameraActive;
                Globals.isDataInsertActive = false;
                mMenu.findItem(R.id.data_insert).setChecked(Globals.isDataInsertActive);
                item.setChecked(Globals.isDataInsertCameraActive);
                return true;
            case R.id.clear_data:
                potHoleRepo.deleteAll();
                File dir = new File("/storage/emulated/0/Android/data/com.salty.potholefinder/files/Pictures/");
                try {
                    FileUtils.deleteDirectory(dir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                addEffects();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.INTERNET}, 10);
            }
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        if (lastLocation != null && Globals.isFirstConnection) {
            mMap.moveCamera(CameraUpdateFactory.zoomTo(15f));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude())));
            Globals.isFirstConnection = false;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e("Google Play API", "Could not connect to the Google Play API");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if(Globals.isDataInsertCameraActive){
                createAndSavePothole(currentLatLnt, mCurrentPicturePath);
            }
            else if(Globals.isDataInsertActive){
                createAndSavePotholeLastLocationWithoutPicture();
            }
            else{
                createAndSavePotholeLastLocationWithPicture();
            }

            addEffects();
        }
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (Globals.isDataInsertActive)
                {
                    createAndSavePothole(latLng, "");
                    addEffects();
                }
                else if(Globals.isDataInsertCameraActive){
                    currentLatLnt = latLng;
                    dispatchTakePictureIntent();
                    addEffects();
                }
            }
        });

        try {
            mMap.setMyLocationEnabled(true);
        } catch(SecurityException e) {
            e.printStackTrace();
        }

        mMap.getUiSettings().setMapToolbarEnabled(false);

        addEffects();
    }

    @Override
    public void onBackPressed() {
        if (fabMenuIsOpen){
            fabOnClick(this.findViewById(R.id.fab));
        }
        else
        {
            moveTaskToBack(true);
        }
    }

    private void addEffects() {
        mMap.clear();

        mClusterManager = new ClusterManager<Pothole>(this, mMap);

        mMap.setOnCameraIdleListener(mClusterManager);
        mMap.setOnMarkerClickListener(mClusterManager);

        mClusterManager.setRenderer(new PotholeRenderer(getApplicationContext(), mMap, mClusterManager));

        mClusterManager.setOnClusterItemClickListener(new ClusterManager.OnClusterItemClickListener<Pothole>() {
            @Override
            public boolean onClusterItemClick(Pothole item) {

                if (item.picturePath.equals(""))
                    return false;
                LayoutInflater inflater = getLayoutInflater();
                View imageDialog = inflater.inflate(R.layout.dialog_image, null);
                Dialog dialog = new Dialog(MapActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(imageDialog);
                dialog.setCanceledOnTouchOutside(true);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

                ImageView image = (ImageView) imageDialog.findViewById(R.id.image);

                Uri uri = Uri.parse(item.picturePath);
                image.setImageURI(uri);

                dialog.show();
                return false;
            }
        });

        FileSystemRepository<Pothole> repo = new FileSystemRepository<>(getApplicationContext());

        List<LatLng> list = new ArrayList<>();
        List<Pothole> potholes = repo.getAll();
        mockPothole(potholes);

        for (Object pothole : potholes) {
            if (pothole != null) {
                try{
                    if(Globals.isClusterActive){
                        mClusterManager.addItem((Pothole)pothole);
                    }
                    LatLng current = ((Pothole)pothole).getPosition();
                    //mMap.addMarker(new MarkerOptions().position(current));
                    list.add(current);
                }catch(Exception e){
                    Log.e("ERROR", e.toString());
                }
            }
        }

        mClusterManager.cluster();

        if (list.size() == 0)
            return;

        if(Globals.isHeatMapActive){
            HeatmapTileProvider mProvider = new HeatmapTileProvider.Builder()
                    .data(list)
                    .build();

            mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File imageFile = null;
            try {
                imageFile = createImageFile();
            } catch (IOException ex) {
                Log.e("ERROR", ex.getMessage());
            }

            if (imageFile != null) {
                Uri imageURI = FileProvider.getUriForFile(this, "com.salty.potholefinder.fileprovider", imageFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageURI);

                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 10:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                break;
        }
    }

    private void fabOnClick(View v){

        FloatingActionButton fabLocation = (FloatingActionButton) this.findViewById(R.id.fab_location);
        FloatingActionButton fabCamera = (FloatingActionButton) this.findViewById(R.id.fab_camera);

        FrameLayout.LayoutParams fabLocationParams = (FrameLayout.LayoutParams) fabLocation.getLayoutParams();
        FrameLayout.LayoutParams fabCameraParams = (FrameLayout.LayoutParams) fabCamera.getLayoutParams();

        if (fabMenuIsOpen){
            fab.setImageDrawable(getResources().getDrawable(R.drawable.pothole));

            Animation hideFabLocation = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_location_hide);
            fabLocationParams.bottomMargin -= (int) (fabLocation.getHeight() * 1.3);
            fabLocation.setLayoutParams(fabLocationParams);
            fabLocation.startAnimation(hideFabLocation);

            Animation hideFabCamera = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_camera_hide);
            fabCameraParams.bottomMargin -= (int) (fabCamera.getHeight() * 2.5);
            fabCamera.setLayoutParams(fabCameraParams);
            fabCamera.startAnimation(hideFabCamera);
        }
        else{
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_close_white_48dp));

            Animation showFabLocation = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_location_show);
            fabLocationParams.bottomMargin += (int) (fabLocation.getHeight() * 1.3);
            fabLocation.setLayoutParams(fabLocationParams);
            fabLocation.startAnimation(showFabLocation);

            Animation showFabCamera = AnimationUtils.loadAnimation(getApplication(), R.anim.fab_camera_show);
            fabCameraParams.bottomMargin += (int) (fabCamera.getHeight() * 2.5);
            fabCamera.setLayoutParams(fabCameraParams);
            fabCamera.startAnimation(showFabCamera);
        }

        fabLocation.setClickable(true);
        fabCamera.setClickable(true);

        fabMenuIsOpen = !fabMenuIsOpen;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        mCurrentPicturePath = image.getAbsolutePath();
        return image;
    }

    private void mockPothole(List<Pothole> potholes)
    {
        potholes.add(addPothole(45.480702, -73.695810));
        potholes.add(addPothole(45.496413, -73.681848));
        potholes.add(addPothole(45.502790, -73.659725));
        potholes.add(addPothole(45.500669, -73.647645));
        potholes.add(addPothole(45.494492, -73.637495));
        potholes.add(addPothole(45.493762, -73.629985));
        potholes.add(addPothole(45.484078, -73.614535));
        potholes.add(addPothole(45.466741, -73.639222));
        potholes.add(addPothole(45.500669, -73.647645));
        potholes.add(addPothole(45.461538, -73.648074));
        potholes.add(addPothole(45.431866, -73.624578));
        potholes.add(addPothole(45.433763, -73.616273));
        potholes.add(addPothole(45.451201, -73.593282));
        potholes.add(addPothole(45.451329, -73.592230));
        potholes.add(addPothole(45.453083, -73.592016));
        potholes.add(addPothole(45.459434, -73.595470));
        potholes.add(addPothole(45.465221, -73.584430));
        potholes.add(addPothole(45.469909, -73.589398));
        potholes.add(addPothole(45.475386, -73.586823));
        potholes.add(addPothole(45.479757, -73.587981));
        potholes.add(addPothole(45.480404, -73.567929));
        potholes.add(addPothole(45.474167, -73.561610));
        potholes.add(addPothole(45.538271, -73.614450));
        potholes.add(addPothole(45.542900, -73.601875));
        potholes.add(addPothole(45.548265, -73.606575));
        potholes.add(addPothole(45.552172, -73.612046));
        potholes.add(addPothole(45.555658, -73.616896));
        potholes.add(addPothole(45.563681, -73.593807));
        potholes.add(addPothole(45.568803, -73.592906));
        potholes.add(addPothole(45.572138, -73.609214));
        potholes.add(addPothole(45.580699, -73.581040));
        potholes.add(addPothole(45.585399, -73.570461));
        potholes.add(addPothole(45.594409, -73.560719));
        potholes.add(addPothole(45.614210, -73.578401));
        potholes.add(addPothole(45.621549, -73.573637));
        potholes.add(addPothole(45.621234, -73.556256));
        potholes.add(addPothole(45.610817, -73.531237));
        potholes.add(addPothole(45.605444, -73.512332));
        potholes.add(addPothole(45.594634, -73.511217));
        potholes.add(addPothole(45.582396, -73.516645));
        potholes.add(addPothole(45.568112, -73.522546));
        potholes.add(addPothole(45.495586, -73.790467));
        potholes.add(addPothole(45.557310, -73.529906));
        potholes.add(addPothole(45.505347, -73.806024));
        potholes.add(addPothole(45.513196, -73.806796));
        potholes.add(addPothole(45.534153, -73.797033));
        potholes.add(addPothole(45.547829, -73.778923));
        potholes.add(addPothole(45.557641, -73.771241));
        potholes.add(addPothole(45.572063, -73.780854));
        potholes.add(addPothole(45.576494, -73.803320));
        potholes.add(addPothole(45.606419, -73.800509));
        potholes.add(addPothole(45.620468, -73.773365));
        potholes.add(addPothole(45.641565, -73.793256));
        potholes.add(addPothole(45.458317, -73.474824));
        potholes.add(addPothole(45.462140, -73.458667));
        potholes.add(addPothole(45.475879, -73.449676));
        potholes.add(addPothole(45.493766, -73.456221));
        potholes.add(addPothole(45.502459, -73.462615));
        potholes.add(addPothole(45.504369, -73.479373));
        potholes.add(addPothole(45.504339, -73.478687));
        potholes.add(addPothole(45.504625, -73.476691));
        potholes.add(addPothole(45.516248, -73.490295));
        potholes.add(addPothole(45.521330, -73.504951));
    }

    private void createAndSavePothole(LatLng latLng, String path){
        String uuid = UUID.randomUUID().toString();
        potHoleRepo.save(uuid, new PotholeBuilder()
                .withPotholeID(uuid)
                .withLatitude(latLng.latitude)
                .withLongitude(latLng.longitude)
                .withPicturePath(path)
                .withUnixTimeStamp(new Date().getTime())
                .createPothole());
    }

    private void createAndSavePothole(LatLng latLng){
        createAndSavePothole(latLng, "");
    }

    private void createAndSavePotholeLastLocationWithPicture(){
        LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
        createAndSavePothole(latLng, mCurrentPicturePath);
    }

    private void createAndSavePotholeLastLocationWithoutPicture(){
        LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
        createAndSavePothole(latLng, "");
    }

    private Pothole addPothole(double latitude, double longitude){
        return new PotholeBuilder()
                .withPotholeID(UUID.randomUUID().toString())
                .withLatitude(latitude)
                .withLongitude(longitude)
                .withPicturePath("")
                .withUnixTimeStamp(new Date().getTime())
                .createPothole();
    }

    private void addPotholeAtLocation(List<Pothole> potholes, double latitude, double longitude){
        potholes.add(new PotholeBuilder()
                .withPotholeID(UUID.randomUUID().toString())
                .withLatitude(latitude)
                .withLongitude(longitude)
                .withPicturePath("")
                .withUnixTimeStamp(new Date().getTime())
                .createPothole());
    }

    private void addRandomPothole(List<Pothole> potholes){
        potholes.add(new PotholeBuilder()
                .withPotholeID(UUID.randomUUID().toString())
                .withLatitude(randomLatitude())
                .withLongitude(randomLongitude())
                .withPicturePath("")
                .withUnixTimeStamp(new Date().getTime())
                .createPothole());
    }

    private double randomLongitude(){
        double longitudeMin = -74;
        double longitudeMax = -73;
        return randomDouble(longitudeMin, longitudeMax);
    }

    private double randomLatitude() {
        double latitudeMin = 45;
        double latitudeMax = 46;
        return randomDouble(latitudeMin, latitudeMax);
    }

    private double randomDouble(double min, double max){
        return min + (max - min) * r.nextDouble();
    }
}
