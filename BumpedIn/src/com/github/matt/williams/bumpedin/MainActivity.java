package com.github.matt.williams.bumpedin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.code.linkedinapi.client.LinkedInApiClient;
import com.google.code.linkedinapi.client.LinkedInApiClientException;
import com.google.code.linkedinapi.client.LinkedInApiClientFactory;
import com.google.code.linkedinapi.client.enumeration.ProfileField;
import com.google.code.linkedinapi.client.oauth.LinkedInAccessToken;
import com.google.code.linkedinapi.client.oauth.LinkedInOAuthService;
import com.google.code.linkedinapi.client.oauth.LinkedInOAuthServiceFactory;
import com.google.code.linkedinapi.client.oauth.LinkedInRequestToken;
import com.google.code.linkedinapi.schema.Person;

public class MainActivity extends Activity {
    static final String CONSUMER_KEY = LinkedInAPIKeys.API_KEY;
    static final String CONSUMER_SECRET = LinkedInAPIKeys.SECRET_KEY;

    static final String OAUTH_CALLBACK_SCHEME = "x-oauthflow-linkedin";
    static final String OAUTH_CALLBACK_HOST = "litestcalback";
    static final String OAUTH_CALLBACK_URL = String.format("%s://%s", OAUTH_CALLBACK_SCHEME, OAUTH_CALLBACK_HOST);
    static final String OAUTH_QUERY_TOKEN = "oauth_token";
    static final String OAUTH_QUERY_VERIFIER = "oauth_verifier";
    static final String OAUTH_QUERY_PROBLEM = "oauth_problem";

    static final String OAUTH_PREF = "LINKEDIN_OAUTH";
    static final String PREF_TOKEN = "token";
    static final String PREF_TOKENSECRET = "tokenSecret";
    static final String PREF_REQTOKENSECRET = "requestTokenSecret";

    final LinkedInOAuthService oAuthService = LinkedInOAuthServiceFactory.getInstance().createLinkedInOAuthService(CONSUMER_KEY, CONSUMER_SECRET);
    final LinkedInApiClientFactory factory = LinkedInApiClientFactory.newInstance(CONSUMER_KEY, CONSUMER_SECRET);
    final ExecutorService mExecutor = Executors.newCachedThreadPool();
    final Handler mHandler = new Handler();

    private NfcAdapter mNfcAdapter;
    private PendingIntent mNfcPendingIntent;
    private IntentFilter[] mNdefExchangeFilters;
    private String mUserId;
    private String mUserIdToConnectTo;
    private boolean mPaused = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        Intent intent = getIntent();
        if ((intent != null) &&
            (intent.getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED))) {
            Tag myTag = (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Ndef ndefTag = Ndef.get(myTag);
            mUserIdToConnectTo = ndefTag.getCachedNdefMessage().getRecords()[0].toUri().getQueryParameter("id");
            android.util.Log.e("MainActivity", "Got URI 1: " + mUserIdToConnectTo);
        }

        final SharedPreferences pref = getSharedPreferences(OAUTH_PREF, MODE_PRIVATE);
        final String token = pref.getString(PREF_TOKEN, null);
        final String tokenSecret = pref.getString(PREF_TOKENSECRET, null);
        if (token == null || tokenSecret == null) {
            android.util.Log.e("MainActivity", "Starting authentcation...");
            startAuthenticate();
        } else {
            if (mUserIdToConnectTo != null) {
                android.util.Log.e("MainActivity", "Connecting to user " + mUserIdToConnectTo);
                connectToUser(new LinkedInAccessToken(token, tokenSecret));
            } else {
                android.util.Log.e("MainActivity", "Showing current user...");
                showCurrentUser(new LinkedInAccessToken(token, tokenSecret));
            }
        }
    }

    private void connectToUser(final LinkedInAccessToken linkedInAccessToken) {
        ((TextView)findViewById(R.id.promptTextView)).setText("Connecting to user...");;
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                android.util.Log.e("MainActivity", "Creating client");
                final LinkedInApiClient client = factory.createLinkedInApiClient(linkedInAccessToken);
                try {
                    android.util.Log.e("MainActivity", "Getting profile");
                    final Set<ProfileField> set = new HashSet<ProfileField>();
                    set.add(ProfileField.ID);
                    set.add(ProfileField.PICTURE_URL);
                    set.add(ProfileField.FIRST_NAME);
                    set.add(ProfileField.LAST_NAME);
                    set.add(ProfileField.HEADLINE);
                    set.add(ProfileField.SITE_STANDARD_PROFILE_REQUEST);
                    set.add(ProfileField.SITE_STANDARD_PROFILE_REQUEST_URL);
                    set.add(ProfileField.API_STANDARD_PROFILE_REQUEST);
                    set.add(ProfileField.API_STANDARD_PROFILE_REQUEST_HEADERS);
                    set.add(ProfileField.API_STANDARD_PROFILE_REQUEST_URL);
                    set.add(ProfileField.PUBLIC_PROFILE_URL);
                    final Person p = client.getProfileById(mUserIdToConnectTo, set);
                    if (p == null) {
                        android.util.Log.e("MainActivity", "User not found!");
                    }

                    // /////////////////////////////////////////////////////////
                    // here you can do client API calls ...
                    // client.postComment(arg0, arg1);
                    // client.updateCurrentStatus(arg0);
                    // or any other API call (this sample only check for current user
                    // and shows it in TextView)
                    // /////////////////////////////////////////////////////////
                    android.util.Log.e("MainActivity", "Sending invite");
//                    client.sendInviteToPerson(p, "BumpedIn Invitation", "Bump!  Please join my LinkedIn network.");

                    android.util.Log.e("MainActivity", "Retrieving data from LinkedIn");
                    final String pictureUrl = p.getPictureUrl();
                    final String name = p.getFirstName() + " " + p.getLastName();
                    final String title = p.getHeadline();
                    android.util.Log.e("MainActivity", "Got person " + name + " with picture " + pictureUrl + " and title " + title);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            android.util.Log.e("MainActivity", "Updating display");
                            if (pictureUrl != null) {
                                ((ImageView)findViewById(R.id.imageView)).setImageURI(Uri.parse(pictureUrl));
                            }
                            ((TextView)findViewById(R.id.nameTextView)).setText(name);
                            ((TextView)findViewById(R.id.titleTextView)).setText(title);
                            ((TextView)findViewById(R.id.promptTextView)).setText("You have sent a connection invitation");
                            android.util.Log.e("MainActivity", "Display updated");
                        }
                    });

                    mUserId = Uri.parse(client.getProfileForCurrentUser().getSiteStandardProfileRequest().getUrl()).getQueryParameter("id");
                    if (!mPaused) {
                        mNfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[] {NdefRecord.createUri("http://www.linkedin.com/profile/view?id=" + mUserId)}), MainActivity.this);
                        mNfcAdapter.enableForegroundDispatch(MainActivity.this, mNfcPendingIntent, mNdefExchangeFilters, null);
                    }
                } catch (final LinkedInApiClientException ex) {
                    android.util.Log.e("MainActivity", "LinkedIn request failed", ex);
 //                   clearTokens();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Application down due LinkedInApiClientException: "
                                            + ex.getMessage()
                                            + " Authtokens cleared - try run application again.",
                                            Toast.LENGTH_LONG).show();
                        }
                    });
                }
                mUserIdToConnectTo = null;
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ((intent.getData() != null) &&
            (intent.getData().getScheme().equals(OAUTH_CALLBACK_SCHEME)) &&
            (intent.getData().getHost().equals(OAUTH_CALLBACK_HOST)))
        {
            finishAuthenticate(intent.getData());
        }
        if (intent.getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
            Tag myTag = (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Ndef ndefTag = Ndef.get(myTag);
            mUserIdToConnectTo = ndefTag.getCachedNdefMessage().getRecords()[0].toUri().getQueryParameter("id");
            android.util.Log.e("MainActivity", "Got URI 2: " + mUserIdToConnectTo);
            final SharedPreferences pref = getSharedPreferences(OAUTH_PREF, MODE_PRIVATE);
            final String token = pref.getString(PREF_TOKEN, null);
            final String tokenSecret = pref.getString(PREF_TOKENSECRET, null);
            connectToUser(new LinkedInAccessToken(token, tokenSecret));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mPaused = false;
        if ((mUserId != null) &&
            (mNfcAdapter != null)) {
            mNfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[] {NdefRecord.createUri("http://www.linkedin.com/profile/view?id=" + mUserId)}), this);
            mNfcAdapter.setOnNdefPushCompleteCallback(new OnNdefPushCompleteCallback() {
                @Override
                public void onNdefPushComplete(NfcEvent event) {
                    mNfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[] {NdefRecord.createUri("http://www.linkedin.com/profile/view?id=" + mUserId)}), MainActivity.this);
                }
            }, this, this);
        }
    }

    @Override
    public void onPause() {
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
        mPaused = true;
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    void startAuthenticate() {
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final LinkedInRequestToken liToken = oAuthService
                        .getOAuthRequestToken(OAUTH_CALLBACK_URL);
                final String uri = liToken.getAuthorizationUrl();
                getSharedPreferences(OAUTH_PREF, MODE_PRIVATE).edit()
                .putString(PREF_REQTOKENSECRET, liToken.getTokenSecret())
                .commit();
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                startActivity(i);
            }
        });
    }

    void finishAuthenticate(final Uri uri) {
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                if (uri != null && uri.getScheme().equals(OAUTH_CALLBACK_SCHEME)) {
                    final String problem = uri.getQueryParameter(OAUTH_QUERY_PROBLEM);
                    if (problem == null) {
                        final SharedPreferences pref = getSharedPreferences(OAUTH_PREF,
                                MODE_PRIVATE);
                        android.util.Log.e("MainActivity", "Converting token");
                        final LinkedInAccessToken accessToken = oAuthService
                                .getOAuthAccessToken(
                                        new LinkedInRequestToken(uri
                                                .getQueryParameter(OAUTH_QUERY_TOKEN),
                                                pref.getString(PREF_REQTOKENSECRET,
                                                        null)),
                                                        uri.getQueryParameter(OAUTH_QUERY_VERIFIER));
                        android.util.Log.e("MainActivity", "Token converted");
                        pref.edit()
                        .putString(PREF_TOKEN, accessToken.getToken())
                        .putString(PREF_TOKENSECRET,
                                accessToken.getTokenSecret())
                                .remove(PREF_REQTOKENSECRET).commit();
                        if (mUserIdToConnectTo != null) {
                            connectToUser(accessToken);
                        } else {
                            showCurrentUser(accessToken);
                        }
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Appliaction down due OAuth problem: " + problem,
                                Toast.LENGTH_LONG).show();
                    }

                }
            }
        });
    }

    void clearTokens() {
        getSharedPreferences(OAUTH_PREF, MODE_PRIVATE).edit()
                .remove(PREF_TOKEN).remove(PREF_TOKENSECRET)
                .remove(PREF_REQTOKENSECRET).commit();
    }

    void showCurrentUser(final LinkedInAccessToken accessToken) {
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final LinkedInApiClient client = factory.createLinkedInApiClient(accessToken);
                try {
                    final Set<ProfileField> set = new HashSet<ProfileField>();
                    set.add(ProfileField.ID);
                    set.add(ProfileField.PICTURE_URL);
                    set.add(ProfileField.FIRST_NAME);
                    set.add(ProfileField.LAST_NAME);
                    set.add(ProfileField.HEADLINE);
                    final Person p = client.getProfileForCurrentUser(set);

                    // /////////////////////////////////////////////////////////
                    // here you can do client API calls ...
                    // client.postComment(arg0, arg1);
                    // client.updateCurrentStatus(arg0);
                    // or any other API call (this sample only check for current user
                    // and shows it in TextView)
                    // /////////////////////////////////////////////////////////

                    android.util.Log.e("MainActivity", "Retrieving data from LinkedIn");
                    final String pictureUrl = p.getPictureUrl();
                    final String name = p.getFirstName() + " " + p.getLastName();
                    final String title = p.getHeadline();
                    android.util.Log.e("MainActivity", "Got person " + name + " with picture " + pictureUrl + " and title " + title);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            android.util.Log.e("MainActivity", "Updating display");
                            if (pictureUrl != null) {
                                ((ImageView)findViewById(R.id.imageView)).setImageURI(Uri.parse(pictureUrl));
                            }
                            ((TextView)findViewById(R.id.nameTextView)).setText(name);
                            ((TextView)findViewById(R.id.titleTextView)).setText(title);
                            ((TextView)findViewById(R.id.promptTextView)).setText("Touch your phone with another to connect");;
                            android.util.Log.e("MainActivity", "Display updated");
                        }
                    });
                    mUserId = p.getId();//Uri.parse(p.getSiteStandardProfileRequest().getUrl()).getQueryParameter("id");
                    if (!mPaused) {
                        android.util.Log.e("MainActivity", "Got user ID " + mUserId);
                        mNfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[] {NdefRecord.createUri("http://www.linkedin.com/profile/view?id=" + mUserId)}), MainActivity.this);
                        mNfcAdapter.setOnNdefPushCompleteCallback(new OnNdefPushCompleteCallback() {
                            @Override
                            public void onNdefPushComplete(NfcEvent event) {
                                mNfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[] {NdefRecord.createUri("http://www.linkedin.com/profile/view?id=" + mUserId)}), MainActivity.this);
                            }
                        }, MainActivity.this, MainActivity.this);
                    }
                } catch (final LinkedInApiClientException ex) {
                    android.util.Log.e("MainActivity", "LinkedIn request failed", ex);
//                    clearTokens();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Application down due LinkedInApiClientException: "
                                            + ex.getMessage()
                                            + " Authtokens cleared - try run application again.",
                                            Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });

    }
}
