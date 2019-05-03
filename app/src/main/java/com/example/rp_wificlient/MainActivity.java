package com.example.rp_wificlient;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.thanosfisherman.wifiutils.WifiConnectorBuilder;
import com.thanosfisherman.wifiutils.WifiUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {



    Button onoff;
    Button scan;
    TextView msg;
    ListView lv;
    ProgressDialog mProgressDialog;
    String passwordres, ssidname;
    String ap_ip;
    static Context context;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);
        context = getApplicationContext();
        //onoff=(Button)findViewById(R.id.wifi);
        scan = (Button) findViewById(R.id.scan);
        lv = (ListView) findViewById(R.id.listview);
        WifiUtils.enableLog(true);
        if (checksavedssiddata()) {
            WifiUtils.withContext(getApplicationContext())
                    .connectWith(ssidname, passwordres)
                    .onConnectionResult(this::checkconnectResult)
                    .start();
        } else {
            Toast.makeText(MainActivity.this, "No saved state found. Please scan and connect to a network.", Toast.LENGTH_LONG).show();
        }
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiUtils.withContext(getApplicationContext()).scanWifi(this::getScanResults).start();
            }

            private void getScanResults(List<ScanResult> scanResults) {
                Log.d("testing", "results:" + scanResults.toString());
                if (scanResults.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "NO devices found", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] res = new String[scanResults.size()];

                int i = 0;
                for (i = 0; i < scanResults.size(); i++) {
                    String str = String.valueOf(scanResults.get(i).SSID);
                    Log.d("testing", "results:" + str);
                    res[i] = str;

                }
                final ArrayAdapter<String> adpt = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, res);
                lv.setAdapter(adpt);

            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = ((TextView) view).getText().toString();

                showdialog();
                Log.d("testing", "return");
                ssidname = item;

            }

        });
    }

    public void showdialog() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        final EditText input = new EditText(MainActivity.this);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        alert.setView(input);
        alert.setTitle("Type Your PIN");

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                passwordres = input.getText().toString();
                Log.d("testing", "pw:" + passwordres);
                WifiUtils.withContext(getApplicationContext())
                        .connectWith(ssidname, passwordres)
                        .onConnectionResult(this::checkResult)
                        .start();
                dialog.cancel();


            }

            private void checkResult(boolean b) {
                if (b) {
                    Toast.makeText(MainActivity.this, "CONNECTED YAY", Toast.LENGTH_SHORT).show();
                    SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("SSID", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.clear();
                    editor.putString("ssidname", ssidname);
                    editor.putString("password", passwordres);
                    editor.commit();
                    String ipaddr = getApIpAddr(getApplicationContext());
                    ap_ip = ipaddr;
                    new GetDataFromServer().execute();

                } else
                    Toast.makeText(MainActivity.this, "COULDN'T CONNECT", Toast.LENGTH_SHORT).show();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.cancel();
            }
        });

        alert.show();

    }

    private void checkconnectResult(boolean b) {
        if (b) {
            Toast.makeText(MainActivity.this, "CONNECTED to" + ssidname, Toast.LENGTH_SHORT).show();
            String ipaddr = getApIpAddr(getApplicationContext());
            ap_ip = ipaddr;
            new GetDataFromServer().execute();
        }
        else
            Toast.makeText(MainActivity.this, "COULDN'T CONNECT", Toast.LENGTH_SHORT).show();
    }

    private boolean checksavedssiddata() {
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("SSID", Context.MODE_PRIVATE);
        String name = sharedPref.getString("ssidname", null);
        String pw = sharedPref.getString("password", null);
        if (name != null) {
            ssidname = name;
            passwordres = pw;
            Log.d("testing","password and ssid:"+ssidname+" "+passwordres);
            return true;
        } else return false;
    }

    public String getApIpAddr(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        byte[] ipAddress = convert2Bytes(dhcpInfo.serverAddress);
        try {
            String apIpAddr = InetAddress.getByAddress(ipAddress).getHostAddress();
            Log.d("testing", "IP:" + apIpAddr.toString());
            ap_ip=apIpAddr;
            //starting asyncg task to get data from nodemcu server .
            //new GetDataFromServer().execute();
            return apIpAddr;
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] convert2Bytes(int hostAddress) {
        byte[] addressBytes = {(byte) (0xff & hostAddress),
                (byte) (0xff & (hostAddress >> 8)),
                (byte) (0xff & (hostAddress >> 16)),
                (byte) (0xff & (hostAddress >> 24))};

        return addressBytes;
    }

    private class GetDataFromServer extends AsyncTask<Void, Void, Void> {
        Elements dataFromESP1;
        Elements dataFromESP2;
        Elements dataFromESP3;
        Elements dataFromESP4;
        int flag=0;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setTitle("Android Basic JSoup Tutorial");
            mProgressDialog.setMessage("Loading...");
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {

                String url="http://"+ap_ip;
                Log.d("testing","reterieving data : "+url);
                // Connect to the web site
                Document doc = Jsoup.connect(url).get();
                Log.d("testing","reterieving data :");
                    dataFromESP1 = doc.select("h1");
                dataFromESP2 = doc.select("h2");
                dataFromESP3 = doc.select("h3");
                dataFromESP4 = doc.select("h4");

                flag=1;

            } catch (IOException e) {
                e.printStackTrace();
                Log.d("testing","exception  :"+e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            while(flag==0);
            // Set dataFromESP into TextView
             Log.d("testing","reterieved data :");
            MainActivity.sendToFirebase(dataFromESP1,dataFromESP2,dataFromESP3,dataFromESP4);
            mProgressDialog.dismiss();
        }
    }

    private static void sendToFirebase(Elements data1,Elements data2,Elements data3,Elements data4) {

           String str1 = data1.toString(); String result1 = str1.substring(str1.indexOf("<h1>")+4, str1.indexOf("</h1>"));
        String str2 = data2.toString(); String result2 = str2.substring(str2.indexOf("<h2>")+4, str2.indexOf("</h2>"));
        String str3 = data3.toString();String result3 = str3.substring(str3.indexOf("<h3>")+4, str3.indexOf("</h3>"));
        String str4 = data4.toString();String result4 = str4.substring(str4.indexOf("<h4>")+4, str4.indexOf("</h4>"));

        Date date = new Date();
        Log.d("testing", "sent to firebase :"+date.toString());
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
           FirebaseDatabase database = FirebaseDatabase.getInstance();
           database.setPersistenceEnabled(true);
           DatabaseReference myRef = database.getReference("ESP");
           myRef.push().setValue(result1);
        myRef.push().setValue(result2);
        myRef.push().setValue(result3);
        myRef.push().setValue(date.toString());
           //myRef.setValue(data.toString());
           Log.d("testing", "sent to firebase :");


    }
}
