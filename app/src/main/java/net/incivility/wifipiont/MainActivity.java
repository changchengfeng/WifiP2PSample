package net.incivility.wifipiont;

import android.Manifest;
import android.app.IntentService;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int CHOOSE_FILE_RESULT_CODE =1001 ;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;

    IntentFilter mIntentFilter;

    WifiP2pManager.PeerListListener myPeerListListener;

    Button btnSearch;
    RecyclerView mRecyclerView;
    WifiP2pManager.ConnectionInfoListener myConnectionInfoListener;
    RecyclerView.Adapter mAdapter;

    ProgressDialog progressDialog;
    WifiP2pInfo mInfo;
    WifiP2pDevice mDevice;

    Button btnSend;
    Button btnDisconnect;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnSearch = (Button) findViewById(R.id.btn_connect);
        btnSend = (Button) findViewById(R.id.btn_send);
        btnDisconnect = (Button) findViewById(R.id.btn_disconnect);
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel);

        myPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList list) {
                mAdapter = new WifiAdapter(list.getDeviceList());
                mRecyclerView.setAdapter(mAdapter);
                mAdapter.notifyDataSetChanged();


            }
        };

        myConnectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {

            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {

                if (info!=null)
                {
                    mInfo = info;



                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    TextView view = (TextView) MainActivity.this.findViewById(R.id.group_owner);
                    view.setText(getResources().getString(R.string.group_owner_text)
                            + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                            : getResources().getString(R.string.no)));

                    view = (TextView) MainActivity.this.findViewById(R.id.device_info);
                    view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

                    if (info.groupFormed && info.isGroupOwner) {
                        ((TextView) MainActivity.this.findViewById(R.id.status_text)).setText(getResources()
                                .getString(R.string.service_text));
                        new FileServerAsyncTask(MainActivity.this, MainActivity.this.findViewById(R.id.status_text))
                                .execute();
                        btnSend.setVisibility(View.GONE);

                    } else if (info.groupFormed) {
                        btnSend.setVisibility(View.VISIBLE);
                        ((TextView) MainActivity.this.findViewById(R.id.status_text)).setText(getResources()
                                .getString(R.string.client_text));
                    }

                    btnSearch.setVisibility(View.GONE);
                    btnDisconnect.setVisibility(View.VISIBLE);

                }



            }
        };

        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT>=23)
                    MainActivityPermissionsDispatcher.showStorageWithCheck(MainActivity.this);
                else
                {
                    showStorage();
                }

            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: btnSend....");
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
            }
        });
        
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: btnDisconnect...");
                if (mManager != null) {

                    if (mDevice == null
                            || mDevice.status == WifiP2pDevice.CONNECTED) {

                        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onFailure(int reasonCode) {
                                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

                            }

                            @Override
                            public void onSuccess() {
                                btnSearch.setText("search");
                            }

                        });
                    } else if (mDevice.status == WifiP2pDevice.AVAILABLE
                            || mDevice.status == WifiP2pDevice.INVITED) {

                        mManager.cancelConnect(mChannel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                btnSearch.setText("search");
                                Toast.makeText(MainActivity.this, "Aborting connection",
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                Toast.makeText(MainActivity.this,
                                        "Connect abort request failed. Reason Code: " + reasonCode,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
            
        });


        btnSearch.setVisibility(View.VISIBLE);
        btnSend.setVisibility(View.GONE);
        btnDisconnect.setVisibility(View.GONE);

    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }


    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            return false;
        }
        return true;
    }

    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

        private WifiP2pManager mManager;
        private WifiP2pManager.Channel mChannel;

        public WiFiDirectBroadcastReceiver() {
        }

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel) {
            super();
            this.mManager = manager;
            this.mChannel = channel;

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                Log.d(TAG, "onReceive: WIFI_P2P_STATE_CHANGED_ACTION ");
                //判断是否支持 wifi点对点传输
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                //查找到设备列表

                Log.d(TAG, "onReceive: WIFI_P2P_PEERS_CHANGED_ACTION ");

                if (mManager != null) {

                    mManager.requestPeers(mChannel, myPeerListListener);
                }

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connection or disconnections
                //获取到连接状态改变的详细信息

                Log.d(TAG, "onReceive: WIFI_P2P_CONNECTION_CHANGED_ACTION");
                if (mManager == null) {
                    return;
                }

                NetworkInfo networkInfo = intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                if (networkInfo.isConnected()) {

                    // we are connected with the other device, request connection
                    // info to find group owner IP

                    Log.d(TAG, "onReceive: isConnected");
                    mManager.requestConnectionInfo(mChannel, myConnectionInfoListener);

                } else {
                    // It's a disconnect
                    Log.d(TAG, "onReceive: disconnect");

                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
                //自身设备信息改变

                Log.d(TAG, "onReceive: WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
            }
        }
    }


    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
            Log.d(TAG, "FileServerAsyncTask: ");
        }

        @Override
        protected String doInBackground(Void... params) {
            try {

                Log.d(TAG, "doInBackground: serverSocket");

                ServerSocket serverSocket = new ServerSocket(8988);
                Socket client = serverSocket.accept();

                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "onPostExecute: " + result);

            if (result != null) {
                statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }
        }
    }

    class WifiAdapter extends RecyclerView.Adapter<WifiHolder> {
        List<WifiP2pDevice> mWifiP2pDevices;

        public WifiAdapter(Collection<WifiP2pDevice> wifiP2pDevices) {
            mWifiP2pDevices = new ArrayList<>();
            Log.d(TAG, "WifiAdapter: " + wifiP2pDevices.size());
            mWifiP2pDevices.addAll(wifiP2pDevices);
        }

        @Override
        public WifiHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new WifiHolder(LayoutInflater.from(
                    MainActivity.this).inflate(R.layout.item_view, parent,
                    false));
        }

        @Override
        public void onBindViewHolder(WifiHolder holder, final int position) {
            holder.tv.setText(mWifiP2pDevices.get(position).deviceAddress);
            holder.tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDevice = mWifiP2pDevices.get(position);
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = mDevice.deviceAddress;
                    config.wps.setup = WpsInfo.PBC;
                    progressDialog = ProgressDialog.show(MainActivity.this, "提示", "连接中");

                    mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

                        @Override
                        public void onSuccess() {
                            //success logic

                        }

                        @Override
                        public void onFailure(int reason) {
                            //failure logic


                        }
                    });
                }
            });

        }

        @Override
        public int getItemCount() {
            return mWifiP2pDevices.size();
        }
    }

    class WifiHolder extends RecyclerView.ViewHolder {
        TextView tv;

        public WifiHolder(View itemView) {
            super(itemView);
            tv = (TextView) itemView.findViewById(R.id.textView);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode==RESULT_OK)
        {
            Uri uri = data.getData();
            TextView statusText = (TextView) findViewById(R.id.status_text);
            statusText.setText("Sending: " + uri);
            Log.d(TAG, "Intent----------- " + uri);
            Intent serviceIntent = new Intent(this, FileTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
            serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                    mInfo.groupOwnerAddress.getHostAddress());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
            startService(serviceIntent);
        }



    }


    public static class FileTransferService extends IntentService {

        private static final int SOCKET_TIMEOUT = 3600;
        public static String ACTION_SEND_FILE = "android.intent.action.SEND_FILE";
        public static String EXTRAS_FILE_PATH = "extras_file_path";
        public static String EXTRAS_GROUP_OWNER_ADDRESS = "extras_group_owner_address";
        public static String EXTRAS_GROUP_OWNER_PORT = "extras_group_owner_port";


        public FileTransferService(String name) {
            super(name);
        }

        public FileTransferService() {
            super("FileTransferService");

        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            Context context = getApplicationContext();
            if (intent.getAction().equals(ACTION_SEND_FILE)) {
                String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
                String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
                Socket socket = new Socket();
                int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

                try {
                    Log.d(TAG, "Opening client socket - ");
                    socket.bind(null);
                    socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                    Log.d(TAG, "Client socket - " + socket.isConnected());
                    OutputStream stream = socket.getOutputStream();
                    ContentResolver cr = context.getContentResolver();
                    InputStream is = null;
                    try {
                        is = cr.openInputStream(Uri.parse(fileUri));
                    } catch (FileNotFoundException e) {
                        Log.d(TAG, e.toString());
                    }
                    copyFile(is, stream);
                    Log.d(TAG, "Client: Data written");
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                } finally {
                    if (socket != null) {
                        if (socket.isConnected()) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                // Give up
                                e.printStackTrace();
                            }
                        }
                    }
                }

            }
        }
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showStorage() {
        Log.d(TAG, "onClick: btnSearch..." );

        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {

                Log.d(TAG, "onSuccess: ");

            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "onFailure: ");
            }
        });
    }

    @OnShowRationale({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showRationaleForStorage(final PermissionRequest request) {

    }

    @OnPermissionDenied({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showDeniedForStorage() {
    }

    @OnNeverAskAgain({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showNeverAskForStorage() {
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);

    }
}
