package cal_on.usbterminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDeviceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class USBTerminal extends AppCompatActivity {

    private final String TAG = USBTerminal.class.getSimpleName();

    private UsbManager mUsbManager;
    private ListView mListView;
    private static UsbSerialPort sPort = null;

    private TextView mDumpTextView;
    private ScrollView mScrollView;
    private BroadcastReceiver mUsbReceiver;
    private String totalData = "";
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;
    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private Spinner baudrate;
    TextView filePath;
    EditText ed;
    Button send;
    //    TextView consoleTextUSB;
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    USBTerminal.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            USBTerminal.this.updateReceivedData(data);
                        }
                    });
                }
            };

    private static final int MESSAGE_REFRESH = 101;
    private List<UsbSerialPort> mEntries = new ArrayList<UsbSerialPort>();
    private ArrayAdapter<UsbSerialPort> mAdapter;
    TextView connect_btn;
    TextView btn_disconnect;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setLogo(R.drawable.logo_new1);
        toolbar.setTitle("   USB Terminal");
        setSupportActionBar(toolbar);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mListView = (ListView) findViewById(R.id.deviceList);
        mDumpTextView = (TextView) findViewById(R.id.consoleTextusb);
        mScrollView = (ScrollView) findViewById(R.id.demoScrollerusb);
        ed=(EditText)findViewById(R.id.time) ;
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        this.mUsbReceiver = new C01102();
        baudrate = (Spinner) findViewById(R.id.spinner_baudrate);
        filePath = (TextView) findViewById(R.id.tv_filepath);
        send=(Button)findViewById(R.id.Send) ;
        TextView clear = (TextView) findViewById(R.id.tv_clear);
        TextView save = (TextView) findViewById(R.id.tv_save_usb);
//        consoleTextUSB=(TextView) findViewById(R.id.consoleTextusb);
        connect_btn = (TextView) findViewById(R.id.btn_connect);
        btn_disconnect = (TextView) findViewById(R.id.btn_disconnect);
        btn_disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectUSB();
            }
        });
        this.permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(USBCommonConstants.ACTION_USB_PERMISSION), 0);
        registerReceiver(this.mUsbReceiver, new IntentFilter(USBCommonConstants.ACTION_USB_PERMISSION));
        registerReceiver(this.mUsbReceiver, new IntentFilter("android.hardware.usb.action.USB_DEVICE_DETACHED"));
        mAdapter = new ArrayAdapter<UsbSerialPort>(this,
                android.R.layout.simple_expandable_list_item_2, mEntries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TwoLineListItem row;
                if (convertView == null) {
                    final LayoutInflater inflater =
                            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    row = (TwoLineListItem) inflater.inflate(android.R.layout.simple_list_item_2, null);
                } else {
                    row = (TwoLineListItem) convertView;
                }

                final UsbSerialPort port = mEntries.get(position);
                final UsbSerialDriver driver = port.getDriver();
                final UsbDevice device = driver.getDevice();

                final String title = String.format("Vendor %s Product %s",
                        HexDump.toHexString((short) device.getVendorId()),
                        HexDump.toHexString((short) device.getProductId()));
                row.getText1().setText(title);

                final String subtitle = driver.getClass().getSimpleName();
                row.getText2().setText(subtitle);

                return row;
            }

        };
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Pressed item " + position);
                if (position >= mEntries.size()) {
                    Log.w(TAG, "Illegal position.");
                    return;
                }

                final UsbSerialPort port = mEntries.get(position);
                showConsoleActivity(port);
            }
        });
        connect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectUSB();
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int usbResult;
                    String ma=ed.getText().toString();
                    //String ma = "";
                    byte[] bytesOut = ma.getBytes();
                    usbResult = sPort.write(bytesOut, 1000);

                    Toast.makeText(getApplicationContext(), "Displaying", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {

                    Toast.makeText(getApplicationContext(), "Unable To Display", Toast.LENGTH_SHORT).show();

                }
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    filePath.setText(exporttoCSV(mDumpTextView.getText().toString()));
                    mDumpTextView.setText("");
                    Toast.makeText(getApplicationContext(), "CSV file created and saved in internal storage" + filePath.getText().toString(), Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), "Error Occurred while export file", Toast.LENGTH_SHORT).show();
                    filePath.setText("Error Occurred while export file");
                    e.printStackTrace();
                }
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDumpTextView.setText("");
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;

        }
//        if (this.mUsbReceiver != null)
//            unregisterReceiver(this.mUsbReceiver);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    void showStatus(TextView theTextView, String theLabel, boolean theValue) {
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        }
        return super.onOptionsItemSelected(item);
    }
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        connectUSB();
    }

    void connectUSB() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            for (UsbDevice device : this.usbManager.getDeviceList().values()) {
                this.usbManager.requestPermission(device, this.permissionIntent);
            }
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0);
        sPort = port;
        showConsoleActivity(port);
        if (sPort == null) {
            Toast.makeText(getApplicationContext(), "No serial device.", Toast.LENGTH_LONG).show();
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                Toast.makeText(getApplicationContext(), "Opening device failed", Toast.LENGTH_LONG).show();
                for (UsbDevice device : this.usbManager.getDeviceList().values()) {
                    this.usbManager.requestPermission(device, this.permissionIntent);
                }
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(Integer.parseInt(baudrate.getSelectedItem().toString()), 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                connect_btn.setVisibility(View.GONE);
                btn_disconnect.setVisibility(View.VISIBLE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                Toast.makeText(getApplicationContext(), "Error opening device: " + e.getMessage(), Toast.LENGTH_LONG).show();
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            Toast.makeText(getApplicationContext(), "Serial device: " + sPort.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            onDeviceStateChange();
        }
    }
    void disconnectUSB() {

        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
                Toast.makeText(getApplicationContext(), "USB Device Disconnected  ", Toast.LENGTH_LONG).show();
                connect_btn.setVisibility(View.VISIBLE);
                btn_disconnect.setVisibility(View.GONE);
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
    }

    private void refreshDeviceList() {

        new AsyncTask<Void, Void, List<UsbSerialPort>>() {
            @Override
            protected List<UsbSerialPort> doInBackground(Void... params) {
                Log.d(TAG, "Refreshing device list ...");
                SystemClock.sleep(1000);

                final List<UsbSerialDriver> drivers =
                        UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                for (final UsbSerialDriver driver : drivers) {
                    final List<UsbSerialPort> ports = driver.getPorts();
                    Log.d(TAG, String.format("+ %s: %s port%s",driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
                    result.addAll(ports);
                }

                return result;
            }

            @Override
            protected void onPostExecute(List<UsbSerialPort> result) {
                mEntries.clear();
                mEntries.addAll(result);
                mAdapter.notifyDataSetChanged();
                Log.d(TAG, "Done refreshing, " + mEntries.size() + " entries found.");
            }

        }.execute((Void) null);
    }



    private void showConsoleActivity(UsbSerialPort port) {
        show(this, port);
    }

    public class USBCommonConstants {
        public static final String ACTION_USB_PERMISSION = "cal_on.usbterminal.usb.USB_PERMISSION";
        public static final int USB_TIME_OUT = 1000;
    }

    class C01102 extends BroadcastReceiver {
        C01102() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device;
            if (USBCommonConstants.ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    device = (UsbDevice) intent.getParcelableExtra("device");
                    if (!intent.getBooleanExtra("permission", false)) {
                        //Toast.makeText(CP210XTermActivity.this, "permission denied for device " + device, CP210XTermActivity.TX_NEW_LINE_NONE).show();
                    } else if (device != null) {
                        UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
                        if (connection == null) {
                            Toast.makeText(getApplicationContext(), "Opening device failed", Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            sPort.open(connection);
                            sPort.setParameters(Integer.parseInt(baudrate.getSelectedItem().toString()), 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                        } catch (IOException e) {
                            Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                            Toast.makeText(getApplicationContext(), "Error opening device: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            try {
                                sPort.close();
                            } catch (IOException e2) {
                                // Ignore.
                            }
                            sPort = null;
                            return;
                        }
                    }
                }
            } else if ("android.hardware.usb.action.USB_DEVICE_DETACHED".equals(action)) {
                device = (UsbDevice) intent.getParcelableExtra("device");
                if (device != null) {

                }
            } else if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(action)) {
//                Toast.makeText(CP210XTermActivity.this, "DEVICE CONNECTED : ", CP210XTermActivity.TX_NEW_LINE_NONE).show();
                UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra("device");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(USBTerminal.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        String s = new String(data);
        totalData = totalData + s;
        mDumpTextView.append(s);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());

    }

    void show(Context context, UsbSerialPort port) {
        sPort = port;
    }

    public String exporttoCSV(final String totalData) throws IOException {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            } else {


                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},23
                );
            }
        }
        File mainFolder = new File(Environment.getExternalStorageDirectory()
                + "/Cal-ONTerminal");
        boolean mainVar = false;
        if (!mainFolder.exists())
            mainVar=mainFolder.mkdir();
        final File folder = new File(Environment.getExternalStorageDirectory()
                + "/Cal-ONTerminal/USB_terminal_files");

        boolean var = false;
        if (!folder.exists())
            var = folder.mkdir();

        System.out.println("" + var);

        @SuppressLint("SimpleDateFormat") SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd_HHmmss");
        Date dt = new Date();
        final String fileFormatName = fmt.format(dt) + ".csv";
        final String filename = folder.toString() + "/" + fileFormatName;

        new Thread() {
            public void run() {
                try {

                    FileWriter fw = new FileWriter(filename);
                    fw.append(totalData);
                    fw.close();

                } catch (Exception e) {
                }
            }
        }.start();
        return "  [/Cal-ONTerminal/USB_terminal_files/" + fileFormatName + "]";
    }
    public class FileOpen {

        public  void openFile(Context context, File url) throws IOException {
            // Create URI
            File file=url;
            Uri uri = Uri.fromFile(file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            // Check what kind of file you are trying to open, by comparing the url with extensions.
            // When the if condition is matched, plugin sets the correct intent (mime) type,
            // so Android knew what application to use to open the file
            if (url.toString().contains(".doc") || url.toString().contains(".docx")) {
                // Word document
                intent.setDataAndType(uri, "application/msword");
            } else if(url.toString().contains(".pdf")) {
                // PDF file
                intent.setDataAndType(uri, "application/pdf");
            } else if(url.toString().contains(".ppt") || url.toString().contains(".pptx")) {
                // Powerpoint file
                intent.setDataAndType(uri, "application/vnd.ms-powerpoint");
            } else if(url.toString().contains(".xls") || url.toString().contains(".xlsx")) {
                // Excel file
                intent.setDataAndType(uri, "application/vnd.ms-excel");
            } else if(url.toString().contains(".zip") || url.toString().contains(".rar")) {
                // WAV audio file
                intent.setDataAndType(uri, "application/x-wav");
            } else if(url.toString().contains(".rtf")) {
                // RTF file
                intent.setDataAndType(uri, "application/rtf");
            } else if(url.toString().contains(".wav") || url.toString().contains(".mp3")) {
                // WAV audio file
                intent.setDataAndType(uri, "audio/x-wav");
            } else if(url.toString().contains(".gif")) {
                // GIF file
                intent.setDataAndType(uri, "image/gif");
            } else if(url.toString().contains(".jpg") || url.toString().contains(".jpeg") || url.toString().contains(".png")) {
                // JPG file
                intent.setDataAndType(uri, "image/jpeg");
            } else if(url.toString().contains(".txt")) {
                // Text file
                intent.setDataAndType(uri, "text/plain");
            } else if(url.toString().contains(".3gp") || url.toString().contains(".mpg") || url.toString().contains(".mpeg") || url.toString().contains(".mpe") || url.toString().contains(".mp4") || url.toString().contains(".avi")) {
                // Video files
                intent.setDataAndType(uri, "video/*");
            } else {
                //if you want you can also define the intent type for any other file

                //additionally use else clause below, to manage other unknown extensions
                //in this case, Android will show all applications installed on the device
                //so you can choose which application to use
                intent.setDataAndType(uri, "*/*");
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
