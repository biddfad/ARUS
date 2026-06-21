package id.abid.arus;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tomtom.sdk.map.display.TomTomMap;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText etInputPencarian;
    private TextView tvHasilRespon;
    private TextToSpeech tts;
    private TomTomMap tomtomMap;

    // MASUKKAN API KEY ASLI DI SINI
    private final String TOMTOM_API_KEY = "dqGG1yaBrYRyaDk1K1dnR8daTt08HCcQ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etInputPencarian = findViewById(R.id.etInputPencarian);
        tvHasilRespon = findViewById(R.id.tvHasilRespon);
        ImageButton btnCariKetik = findViewById(R.id.btnCariKetik);
        ImageButton btnMic = findViewById(R.id.btnMic);
        FloatingActionButton btnMyLocation = findViewById(R.id.btnMyLocation);

        Button btnKategoriSPBU = findViewById(R.id.btnKategoriSPBU);
        Button btnKategoriRS = findViewById(R.id.btnKategoriRS);
        Button btnKategoriResto = findViewById(R.id.btnKategoriResto);
        Button btnKategoriParkir = findViewById(R.id.btnKategoriParkir);

        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };

        boolean needsPermission = false;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needsPermission = true;
                break;
            }
        }

        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions, 1);
        }

        initTomTomMap();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("id", "ID"));
        });



        btnCariKetik.setOnClickListener(v -> {
            String input = etInputPencarian.getText().toString().trim();
            if (!input.isEmpty()) {
                tutupKeyboard();
                prosesPerintah(input, false);
            }
        });

        btnMic.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Sebutkan lokasi tujuan Anda...");
            try {
                startActivityForResult(intent, 100);
            } catch (Exception e) {
                tampilkanHasil("Fitur suara tidak didukung di perangkat ini.", false);
            }
        });

        btnMyLocation.setOnClickListener(v -> {
            if (tomtomMap != null) {
                com.tomtom.sdk.location.GeoPoint loc = TomTomSetup.getCurrentGeoPoint(tomtomMap);
                if (loc != null) {
                    tomtomMap.moveCamera(new com.tomtom.sdk.map.display.camera.CameraOptions(
                        loc,
                        15.0, // zoom level for street view
                        null, null, null
                    ));
                    tampilkanHasil("Menampilkan lokasi Anda saat ini.", false);
                } else {
                    TomTomSetup.setCenterIndonesia(tomtomMap);
                    tampilkanHasil("Lokasi GPS belum tersedia, menampilkan peta utama.", false);
                }
            }
        });

        // Quick Categories
        btnKategoriSPBU.setOnClickListener(v -> prosesBanyakLokasi("SPBU", false));
        btnKategoriRS.setOnClickListener(v -> prosesBanyakLokasi("Rumah Sakit", false));
        btnKategoriResto.setOnClickListener(v -> prosesBanyakLokasi("Restoran", false));
        btnKategoriParkir.setOnClickListener(v -> prosesBanyakLokasi("Parkir", false));
    }

    private com.tomtom.sdk.search.Search searchApi;
    private com.tomtom.sdk.routing.RoutePlanner routePlanner;
    private com.tomtom.sdk.location.android.AndroidLocationProvider locationProvider;

    private void initTomTomMap() {
        searchApi = TomTomSetup.buatSearchApi(this, TOMTOM_API_KEY);
        routePlanner = TomTomSetup.buatRoutePlanner(this, TOMTOM_API_KEY);
        locationProvider = TomTomSetup.buatLocationProvider(this);

        // 1. Buat opsi peta dengan API Key
        com.tomtom.sdk.map.display.MapOptions mapOptions = TomTomSetup.buatOpsiPeta(TOMTOM_API_KEY);

        // 2. KEMBALIKAN ke newInstance (Gunakan helper untuk akses dari Java)
        com.tomtom.sdk.map.display.ui.MapFragment mapFragment = TomTomSetup.buatMapFragment(mapOptions);

        // 3. Pasang ke layar
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.map_container, mapFragment)
                .commit();

        // 4. Tunggu peta siap
        mapFragment.getMapAsync(map -> {
            tomtomMap = map;
            tomtomMap.showTrafficFlow();
            tomtomMap.setLocationProvider(locationProvider);
            locationProvider.enable();
            TomTomSetup.setCenterIndonesia(tomtomMap);
        });
    }

    private void prosesPerintah(String perintah, boolean pakaiSuara) {
        if (tomtomMap == null) {
            tampilkanHasil("Peta belum siap, tunggu sebentar.", pakaiSuara);
            return;
        }

        tampilkanHasil("Mencari lokasi " + perintah + "...", pakaiSuara);

        TomTomSetup.cariLokasi(searchApi, perintah, tomtomMap, new TomTomSetup.SearchResultCallback() {
            @Override
            public void onSuccess(double lat, double lon) {
                com.tomtom.sdk.location.GeoPoint destination = new com.tomtom.sdk.location.GeoPoint(lat, lon);
                tampilkanHasil("Lokasi ditemukan! Menghitung rute...", pakaiSuara);
                
                TomTomSetup.gambarRute(routePlanner, tomtomMap, destination, new TomTomSetup.RouteResultCallback() {
                    @Override
                    public void onSuccess(int waktuMenit, double jarakKm) {
                        String jarakStr = String.format(Locale.getDefault(), "%.1f", jarakKm);
                        String hasil = "Rute ke " + perintah + " siap. Jarak " + jarakStr + " km, waktu tempuh sekitar " + waktuMenit + " menit.";
                        tampilkanHasil(hasil, pakaiSuara);
                    }

                    @Override
                    public void onFailure() {
                        tampilkanHasil("Lokasi ditemukan, tapi gagal membuat rute.", pakaiSuara);
                    }
                });
            }

            @Override
            public void onFailure() {
                String hasil = "Maaf, lokasi " + perintah + " tidak ditemukan.";
                tampilkanHasil(hasil, pakaiSuara);
            }
        });
    }

    private void prosesBanyakLokasi(String perintah, boolean pakaiSuara) {
        if (tomtomMap == null) {
            tampilkanHasil("Peta belum siap, tunggu sebentar.", pakaiSuara);
            return;
        }

        tampilkanHasil("Mencari " + perintah + "...", pakaiSuara);

        TomTomSetup.cariBanyakLokasi(searchApi, perintah, tomtomMap, new TomTomSetup.SearchResultCallback() {
            @Override
            public void onSuccess(double lat, double lon) {
                String hasil = "Menampilkan beberapa lokasi untuk " + perintah;
                tampilkanHasil(hasil, pakaiSuara);
            }

            @Override
            public void onFailure() {
                String hasil = "Maaf, " + perintah + " tidak ditemukan.";
                tampilkanHasil(hasil, pakaiSuara);
            }
        });
    }

    private void tampilkanHasil(String teks, boolean putarSuara) {
        runOnUiThread(() -> {
            tvHasilRespon.setText(teks);
            if (putarSuara) tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, null);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                etInputPencarian.setText(spokenText);
                tutupKeyboard();
                prosesPerintah(spokenText, true);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void tutupKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationProvider != null) {
            locationProvider.disable();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}